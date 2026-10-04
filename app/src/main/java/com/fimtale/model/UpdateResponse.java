package com.fimtale.model;

import com.google.gson.annotations.SerializedName;

public class UpdateResponse {
    @SerializedName("version_code")
    private int versionCode;
    
    @SerializedName("version_name")
    private String versionName;
    
    @SerializedName("download_url")
    private String downloadUrl;
    
    @SerializedName("update_log")
    private String updateLog;
    
    @SerializedName("force_update")
    private boolean forceUpdate;

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getUpdateLog() {
        return updateLog;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }
}
