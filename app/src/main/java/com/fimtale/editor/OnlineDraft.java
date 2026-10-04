package com.fimtale.editor;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/** Current ft-front/ft-go draft wire format. */
public class OnlineDraft {
    @SerializedName("draft_key") public String key;
    public long revision;
    @SerializedName("updated_at") public String updatedAt;
    public JsonObject payload;
    public static class Summary {
        @SerializedName("draft_key") public String key;
        public String title, preview;
        public long revision;
        @SerializedName("updated_at") public String updatedAt;
    }
    public static class Save {
        @SerializedName("draft_key") public final String key;
        @SerializedName("expected_revision") public final long revision;
        public final JsonObject payload;
        public Save(String key, long revision, JsonObject payload) { this.key = key; this.revision = revision; this.payload = payload; }
    }
    public static class Delete {
        @SerializedName("draft_key") public final String key;
        @SerializedName("expected_revision") public final long revision;
        public Delete(String key, long revision) { this.key = key; this.revision = revision; }
    }
}
