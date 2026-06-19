import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

import gspread
from fastapi import FastAPI, HTTPException
from dotenv import load_dotenv
from gspread.utils import rowcol_to_a1
from pydantic import BaseModel, Field


BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")

SERVICE_ACCOUNT_FILE = os.getenv(
    "GOOGLE_SERVICE_ACCOUNT_FILE",
    str(BASE_DIR / "service_account.json"),
)
GOOGLE_SHEET_ID = os.getenv("GOOGLE_SHEET_ID", "")
GOOGLE_WORKSHEET_NAME = os.getenv("GOOGLE_WORKSHEET_NAME", "Sheet1")
STALE_PROCESSING_SECONDS = int(os.getenv("STALE_PROCESSING_SECONDS", "90"))

REQUIRED_COLUMNS = {
    "id",
    "live_url",
    "wait_time_seconds",
    "collected_coin",
    "status",
    "last_updated",
}

app = FastAPI(title="Shopee Live Task Backend", version="1.0.0")
_client = None


class DoneRequest(BaseModel):
    collected_coin: int = Field(default=0, ge=0)


class ErrorRequest(BaseModel):
    error_message: str = Field(default="Unknown error")


def get_gspread_client():
    global _client
    if _client is not None:
        return _client

    service_account_path = Path(SERVICE_ACCOUNT_FILE)
    if not service_account_path.exists():
        raise HTTPException(
            status_code=500,
            detail=f"Missing service account file: {service_account_path}",
        )

    try:
        _client = gspread.service_account(filename=str(service_account_path))
        return _client
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Cannot load Google service account: {exc}",
        ) from exc


def get_worksheet():
    if not GOOGLE_SHEET_ID:
        raise HTTPException(
            status_code=500,
            detail="Missing GOOGLE_SHEET_ID environment variable",
        )

    try:
        spreadsheet = get_gspread_client().open_by_key(GOOGLE_SHEET_ID)
        try:
            return spreadsheet.worksheet(GOOGLE_WORKSHEET_NAME)
        except Exception:
            first_worksheet = spreadsheet.get_worksheet(0)
            if first_worksheet is None:
                raise RuntimeError("Spreadsheet has no worksheets")
            return first_worksheet
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Cannot open worksheet: {exc}",
        ) from exc


def get_column_map(worksheet) -> Dict[str, int]:
    headers = worksheet.row_values(1)
    columns = {name.strip(): index for index, name in enumerate(headers, start=1)}
    missing = REQUIRED_COLUMNS - set(columns.keys())
    if missing:
        raise HTTPException(
            status_code=500,
            detail=f"Missing required columns: {', '.join(sorted(missing))}",
        )
    return columns


def to_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def normalize_task(row: Dict[str, Any]) -> Dict[str, Any]:
    row = normalize_row_keys(row)
    return {
        "id": str(row.get("id", "")),
        "live_url": str(row.get("live_url", "")),
        "wait_time_seconds": to_int(row.get("wait_time_seconds")),
        "gmail_account": str(row.get("gmail_account", "")),
        "account_note": str(row.get("account_note", "")),
        "collected_coin": to_int(row.get("collected_coin")),
        "status": str(row.get("status", "")),
        "last_updated": str(row.get("last_updated", "")),
    }


def normalize_row_keys(row: Dict[str, Any]) -> Dict[str, Any]:
    return {str(key).strip(): value for key, value in row.items()}


def find_task_row(worksheet, task_id: str) -> Tuple[int, Dict[str, Any]]:
    records = worksheet.get_all_records()
    for index, raw_row in enumerate(records, start=2):
        row = normalize_row_keys(raw_row)
        if str(row.get("id", "")) == str(task_id):
            return index, row

    raise HTTPException(status_code=404, detail=f"Task {task_id} not found")


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def parse_iso_datetime(value: Any) -> Optional[datetime]:
    if value is None:
        return None

    text = str(value).strip()
    if not text:
        return None

    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return None

    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def is_stale_processing_task(row: Dict[str, Any]) -> bool:
    last_updated = parse_iso_datetime(row.get("last_updated"))
    if last_updated is None:
        return True

    age = datetime.now(timezone.utc) - last_updated
    return age >= timedelta(seconds=STALE_PROCESSING_SECONDS)


def update_task(
    task_id: str,
    status: str,
    collected_coin: Optional[int] = None,
) -> Dict[str, Any]:
    worksheet = get_worksheet()
    columns = get_column_map(worksheet)
    row_index, row = find_task_row(worksheet, task_id)

    updates = [
        {
            "range": rowcol_to_a1(row_index, columns["status"]),
            "values": [[status]],
        },
        {
            "range": rowcol_to_a1(row_index, columns["last_updated"]),
            "values": [[utc_now_iso()]],
        },
    ]

    if collected_coin is not None and "collected_coin" in columns:
        updates.append(
            {
                "range": rowcol_to_a1(row_index, columns["collected_coin"]),
                "values": [[collected_coin]],
            }
        )

    try:
        worksheet.batch_update(updates)
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Cannot update task {task_id}: {exc}",
        ) from exc

    row["status"] = status
    row["last_updated"] = utc_now_iso()
    if collected_coin is not None:
        row["collected_coin"] = collected_coin
    return normalize_task(row)


@app.get("/tasks/pending")
def get_pending_task():
    worksheet = get_worksheet()
    get_column_map(worksheet)

    try:
        records = worksheet.get_all_records()
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Cannot read tasks: {exc}",
        ) from exc

    for raw_row in records:
        row = normalize_row_keys(raw_row)
        if str(row.get("status", "")).lower() == "pending":
            return normalize_task(row)

    for raw_row in records:
        row = normalize_row_keys(raw_row)
        status = str(row.get("status", "")).lower()
        task_id = str(row.get("id", ""))
        if status == "processing" and task_id and is_stale_processing_task(row):
            return update_task(task_id, "pending")

    return {"message": "no_task"}


@app.post("/tasks/{task_id}/processing")
def mark_processing(task_id: str):
    task = update_task(task_id, "processing")
    return {"message": "updated", "task": task}


@app.post("/tasks/{task_id}/done")
def mark_done(task_id: str, request: DoneRequest):
    task = update_task(task_id, "done", collected_coin=request.collected_coin)
    return {"message": "updated", "task": task}


@app.post("/tasks/{task_id}/error")
def mark_error(task_id: str, request: ErrorRequest):
    task = update_task(task_id, "error")
    return {
        "message": "updated",
        "error_message": request.error_message,
        "task": task,
    }
