package com.app.fimtale.model;
import com.google.gson.annotations.SerializedName;
import com.app.fimtale.network.SiteUrls;
public class AuthorInfo {
    @SerializedName("user_id") private int id;
    private String username;
    private String avatar;
    public int getId() { return id; }
    public String getUserName() { return username; }
    public String getAvatar() { return SiteUrls.media(avatar); }
}
