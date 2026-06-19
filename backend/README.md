# Shopee Live Task Backend

FastAPI backend khong can auth. Backend doc Google Sheet, tim task `pending`, va cap nhat `processing`, `done`, hoac `error`.

## Google Sheet mau

Tao sheet voi header o dong 1:

```text
id | live_url | wait_time_seconds | gmail_account | account_note | collected_coin | status | last_updated
1  | https://shopee.vn/live/example | 30 | example@gmail.com | device_01 | 0 | pending | 2026-01-01T10:00:00
```

App Android chi dung cac cot: `id`, `live_url`, `wait_time_seconds`, `collected_coin`, `status`, `last_updated`.

## Tao Service Account

1. Vao Google Cloud Console.
2. Tao project hoac chon project co san.
3. Enable Google Sheets API.
4. Tao Service Account.
5. Tao key JSON cho Service Account.
6. Doi ten file JSON tai ve thanh `service_account.json` va dat trong thu muc `backend/`.
7. Mo Google Sheet, bam Share, them email cua Service Account voi quyen Editor.

Khong dua file `service_account.json` that len git. File nay da duoc them vao `.gitignore`.

## Cau hinh bien moi truong

PowerShell:

```powershell
$env:GOOGLE_SHEET_ID="your_google_sheet_id"
$env:GOOGLE_WORKSHEET_NAME="Sheet1"
$env:GOOGLE_SERVICE_ACCOUNT_FILE="backend/service_account.json"
```

`GOOGLE_SHEET_ID` la phan ID trong URL:

```text
https://docs.google.com/spreadsheets/d/<GOOGLE_SHEET_ID>/edit
```

## Chay backend

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Test:

```powershell
curl http://127.0.0.1:8000/tasks/pending
curl -X POST http://127.0.0.1:8000/tasks/1/processing
curl -X POST http://127.0.0.1:8000/tasks/1/done -H "Content-Type: application/json" -d "{\"collected_coin\":0}"
curl -X POST http://127.0.0.1:8000/tasks/1/error -H "Content-Type: application/json" -d "{\"error_message\":\"Cannot open live url\"}"
```

## Cau hinh Android BASE_URL

Mac dinh Android dung:

```java
RetrofitClient.DEFAULT_BASE_URL = "http://192.168.1.100:8000/";
```

Doi IP nay thanh IP may dang chay backend trong file:

```text
app/src/main/java/com/example/shopeeautoclick/data/remote/RetrofitClient.java
```

Neu dung emulator Android Studio, co the dung:

```text
http://10.0.2.2:8000/
```

Neu dung dien thoai that, dien thoai va may chay backend phai cung mang Wi-Fi, va firewall phai cho phep port `8000`.

## Build Android

```powershell
.\gradlew.bat :app:assembleDebug
```

Mo app, bam `Open Accessibility Settings`, bat service `Shopee Auto Click Accessibility`, quay lai app. Neu permission da bat, app tu start `TaskMonitorService`.

## Test click

1. Bat Accessibility Service thu cong.
2. Mo app.
3. Bam `Test Click`.
4. Xem Logcat tag `AutoClickAccessibility` va `TaskMonitorService`.

Toa do mac dinh:

```text
x = screenWidth - 80
y = screenHeight / 2
```

## Loi thuong gap

- `Missing GOOGLE_SHEET_ID`: chua set bien moi truong.
- `Missing service account file`: chua thay placeholder bang JSON that tu Google Cloud.
- `Cannot open worksheet`: service account chua duoc share vao sheet hoac sai worksheet name.
- Android bao `No pending task`: sheet khong co dong `status = pending`.
- Android khong goi duoc API: sai `BASE_URL`, khac Wi-Fi, firewall chan port, hoac backend chua chay.
- Click khong chay: Accessibility Service chua bat hoac bi Android tat trong Settings.
- Link khong mo: `live_url` rong, sai scheme, hoac may khong co app/browser xu ly link.
