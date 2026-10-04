package com.fimtale.model;
import com.fimtale.network.SiteUrls;
public class CurrentUser {
    public int id;
    public String username, avatar, intro;
    public UserMaterial material;
    public String getAvatar() { return SiteUrls.media(avatar); }
}
