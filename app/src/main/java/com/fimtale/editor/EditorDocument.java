package com.fimtale.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** A local draft; never serialized directly as an API request. */
public class EditorDocument {
    public WorkInput work = new WorkInput();
    public ChapterInput chapter = new ChapterInput();
    public LinkedHashMap<Integer, String> tags = new LinkedHashMap<>();
    public LinkedHashMap<Integer, String> tagGroups = new LinkedHashMap<>();
    public String draftKey;
    public long revision;
    public boolean pendingSync = true;
    public com.google.gson.JsonObject onlinePayload;
    public String prequelText = "";
    public long savedAt;
    public boolean handbookAccepted;
    // Persisted before sending. A lost response/process must not silently publish twice.
    public boolean submissionUncertain;
    public void syncTags() { work.tagIds = new ArrayList<>(tags.keySet()); }
}
