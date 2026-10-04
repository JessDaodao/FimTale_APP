package com.app.fimtale.model;
import com.app.fimtale.network.SiteUrls;
public class CurrentUser {
    public int id;
    public String username, avatar, intro;
    public UserMaterial material;
    public String getAvatar() { return SiteUrls.media(avatar); }
}
