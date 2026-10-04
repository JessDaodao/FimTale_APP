package com.app.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class BlockedUser {
    @SerializedName("user_id") public int userId;
    public String username;
    public String avatar;
}
