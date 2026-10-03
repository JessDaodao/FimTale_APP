package com.app.fimtale.editor;

import com.app.fimtale.model.AuthorInfo;
import com.app.fimtale.model.TagGroup;
import com.google.gson.Gson;
import java.util.List;

/** Keep raw BBCode, cover paths, taxonomy, and every selected tag when editing. */
public class WorkEditResponse {
    public Work work;
    public static class Work extends WorkInput {
        public AuthorInfo user;
        public List<TagGroup> tags;
    }
    public EditorDocument toDocument() {
        EditorDocument document = new EditorDocument();
        // Deserialize to the request class so response-only fields cannot be sent back.
        Gson gson = new Gson();
        document.work = gson.fromJson(gson.toJson(work), WorkInput.class);
        document.work.tagIds.clear();
        if (work.tags != null) for (TagGroup group : work.tags) {
            if (group.tags != null) group.tags.forEach(tag -> {
                if (tag.getId() > 0) { document.tags.put(tag.getId(), tag.getName()); document.tagGroups.put(tag.getId(), group.name); }
            });
        }
        document.syncTags();
        document.prequelText = work.prequelId == null || work.prequelId == 0 ? "" : String.valueOf(work.prequelId);
        return document;
    }
}
