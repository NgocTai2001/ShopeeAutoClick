package com.example.shopeeautoclick.data.model;

import com.google.gson.annotations.SerializedName;

public class ErrorRequest {
    @SerializedName("error_message")
    private final String errorMessage;

    public ErrorRequest(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
