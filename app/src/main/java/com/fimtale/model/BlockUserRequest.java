package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class BlockUserRequest {
    @SerializedName("user_id") public final Integer userId;
    public BlockUserRequest() { this.userId = null; }
    public BlockUserRequest(int userId) { this.userId = userId; }
}
