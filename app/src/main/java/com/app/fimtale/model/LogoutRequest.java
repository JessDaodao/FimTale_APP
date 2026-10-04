package com.app.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class LogoutRequest {
    @SerializedName("token_prefix") public final String tokenPrefix;
    public LogoutRequest(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
}
