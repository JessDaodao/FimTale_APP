package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class UserAuth {
    @SerializedName("user_id") public int userId;
    @SerializedName("role_id") public int roleId;
    @SerializedName("qualify_status") public int qualifyStatus;
    @SerializedName("space_status") public int spaceStatus;
    public boolean canEdit(int ownerId) { return userId > 0 && (roleId >= 4 || ownerId == userId); }
    public boolean canPublish() { return userId > 0 && qualifyStatus >= 2; }
    public boolean canReview() { return userId > 0 && roleId >= 2; }
    public boolean canManageReviews() { return userId > 0 && roleId >= 4; }
}
