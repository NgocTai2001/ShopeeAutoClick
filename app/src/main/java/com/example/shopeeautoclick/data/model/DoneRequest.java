package com.example.shopeeautoclick.data.model;

import com.google.gson.annotations.SerializedName;

public class DoneRequest {
    @SerializedName("collected_coin")
    private final int collectedCoin;

    public DoneRequest(int collectedCoin) {
        this.collectedCoin = collectedCoin;
    }

    public int getCollectedCoin() {
        return collectedCoin;
    }
}
