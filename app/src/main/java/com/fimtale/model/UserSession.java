package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class UserSession {
    @SerializedName("last_activity") public String lastActivity;
    @SerializedName("role_id") public int roleId;
    @SerializedName("token_prefix") public String tokenPrefix;
    @SerializedName("user_id") public int userId;
}
