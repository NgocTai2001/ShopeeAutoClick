package com.example.shopeeautoclick.data.model;

import com.google.gson.annotations.SerializedName;

public class LiveTask {
    @SerializedName("id")
    private String id;

    @SerializedName("live_url")
    private String liveUrl;

    @SerializedName("wait_time_seconds")
    private Integer waitTimeSeconds;

    @SerializedName("gmail_account")
    private String gmailAccount;

    @SerializedName("account_note")
    private String accountNote;

    @SerializedName("collected_coin")
    private Integer collectedCoin;

    @SerializedName("status")
    private String status;

    @SerializedName("last_updated")
    private String lastUpdated;

    @SerializedName("message")
    private String message;

    public String getId() {
        return id;
    }

    public String getLiveUrl() {
        return liveUrl;
    }

    public int getWaitTimeSeconds() {
        return waitTimeSeconds == null ? 0 : waitTimeSeconds;
    }

    public String getGmailAccount() {
        return gmailAccount;
    }

    public String getAccountNote() {
        return accountNote;
    }

    public int getCollectedCoin() {
        return collectedCoin == null ? 0 : collectedCoin;
    }

    public String getStatus() {
        return status;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public String getMessage() {
        return message;
    }

    public boolean isPending() {
        return "pending".equalsIgnoreCase(status);
    }

    public boolean isNoTask() {
        return "no_task".equalsIgnoreCase(message);
    }
}
