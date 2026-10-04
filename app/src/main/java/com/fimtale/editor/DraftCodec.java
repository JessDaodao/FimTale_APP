package com.fimtale.editor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Shares the website's keys and payloads; preserves tag objects and unknown editor fields. */
public final class DraftCodec {
    private static final Gson GSON = new Gson();
    private DraftCodec() {}
    public static String key(int workId, int chapterId, String slot) {
        if (chapterId > 0) return "chapter:" + chapterId;
        if (chapterId < 0 && workId > 0) return "work:" + workId;
        // Navigation represents legacy drafts without a slot as an empty extra.
        // Keep the same key as the local store, which treats null and empty alike.
        if (slot != null && slot.isEmpty()) slot = null;
        if (slot == null || !slot.matches("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            slot = UUID.nameUUIDFromBytes(("fimtale-app:" + workId + ":" + chapterId + ":" + slot).getBytes(StandardCharsets.UTF_8)).toString();
        return chapterId == 0 ? "chapter:new:" + workId + ":" + slot : "work:new:" + slot;
    }
    public static class Target {
        public int workId, chapterId = -1;
        public String slot;
    }
    public static Target target(String key, JsonObject payload) {
        if (key == null) throw new IllegalArgumentException("草稿缺少标识");
        String[] parts = key.split(":"); Target target = new Target();
        if (parts.length == 2 && parts[0].equals("work")) target.workId = positive(parts[1]);
        else if (parts.length == 3 && parts[0].equals("work") && parts[1].equals("new")) target.slot = parts[2];
        else if (parts.length == 2 && parts[0].equals("chapter")) {
            target.chapterId = positive(parts[1]);
            if (payload == null || !payload.has("work_id")) throw new IllegalArgumentException("章节草稿缺少作品 ID");
            target.workId = positive(payload.get("work_id").getAsString());
        } else if (parts.length == 4 && parts[0].equals("chapter") && parts[1].equals("new")) {
            target.chapterId = 0; target.workId = positive(parts[2]); target.slot = parts[3];
        } else throw new IllegalArgumentException("不支持的草稿类型");
        if (target.slot != null && !target.slot.matches("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("草稿标识无效");
        return target;
    }
    private static int positive(String number) {
        int value = Integer.parseInt(number); if (value <= 0) throw new IllegalArgumentException("草稿 ID 无效"); return value;
    }
    public static JsonObject payload(EditorDocument document, boolean chapter) {
        JsonObject result = document.onlinePayload == null ? new JsonObject() : copy(document.onlinePayload);
        JsonObject fields = GSON.toJsonTree(chapter ? document.chapter : document.work).getAsJsonObject();
        result.remove("id"); result.remove("tag_ids");
        for (Map.Entry<String, JsonElement> field : fields.entrySet()) if (!field.getKey().equals("tag_ids")) result.add(field.getKey(), field.getValue());
        if (!chapter) {
            String prequel = document.prequelText == null ? "" : document.prequelText.trim();
            if (prequel.isEmpty()) result.remove("prequel_id");
            else try { result.addProperty("prequel_id", Integer.parseInt(prequel)); }
            catch (NumberFormatException ignored) { result.addProperty("prequel_id", prequel); }
            JsonObject oldTags = result.has("selected_tags") && result.get("selected_tags").isJsonObject() ? result.getAsJsonObject("selected_tags") : new JsonObject();
            JsonArray regular = new JsonArray(), characters = new JsonArray();
            document.tags.forEach((id, name) -> {
                JsonObject tag = findTag(oldTags, id);
                if (tag == null) { tag = new JsonObject(); tag.addProperty("id", id); tag.addProperty("name", name); }
                String group = document.tagGroups.get(id);
                boolean isRegular = "题材".equals(group) || "读者注意".equals(group) || "历史标签".equals(group);
                if (isRegular) regular.add(tag); else characters.add(tag);
            });
            JsonObject selected = new JsonObject(); selected.add("regular", regular); selected.add("characters", characters); result.add("selected_tags", selected);
        }
        return result;
    }
    private static JsonObject findTag(JsonObject selected, int id) {
        for (String group : new String[]{"regular", "characters"}) {
            if (!selected.has(group) || !selected.get(group).isJsonArray()) continue;
            for (JsonElement value : selected.getAsJsonArray(group)) if (value.isJsonObject()) {
                JsonObject tag = value.getAsJsonObject();
                if (tag.has("id") && tag.get("id").getAsInt() == id) return tag;
            }
        }
        return null;
    }
    public static EditorDocument document(OnlineDraft draft, int workId, int chapterId) {
        if (draft == null || draft.payload == null || draft.revision <= 0) throw new IllegalArgumentException("在线草稿数据不完整");
        Target target = target(draft.key, draft.payload);
        if (target.workId != workId || target.chapterId != chapterId) throw new IllegalArgumentException("草稿与作品不匹配");
        EditorDocument result = new EditorDocument(); result.draftKey = draft.key; result.revision = draft.revision;
        result.pendingSync = false; result.onlinePayload = copy(draft.payload);
        if (chapterId >= 0) {
            result.chapter = GSON.fromJson(draft.payload, ChapterInput.class);
            result.chapter.workId = workId; result.chapter.id = chapterId > 0 ? chapterId : null;
        } else {
            JsonObject workPayload = copy(draft.payload);
            // A draft may contain an unfinished number; it is validated only on publication.
            workPayload.remove("prequel_id");
            result.work = GSON.fromJson(workPayload, WorkInput.class); result.work.id = workId > 0 ? workId : null;
            if (draft.payload.has("prequel_id") && !draft.payload.get("prequel_id").isJsonNull()) result.prequelText = draft.payload.get("prequel_id").getAsString();
            JsonElement selected = draft.payload.get("selected_tags");
            if (selected != null && selected.isJsonObject()) for (String group : new String[]{"regular", "characters"}) {
                JsonElement tags = selected.getAsJsonObject().get(group);
                if (tags == null || !tags.isJsonArray()) continue;
                for (JsonElement value : tags.getAsJsonArray()) {
                    JsonObject tag = value.getAsJsonObject(); int id = tag.get("id").getAsInt();
                    result.tags.put(id, tag.has("name") ? tag.get("name").getAsString() : "标签 " + id);
                    result.tagGroups.put(id, group.equals("regular") ? "题材" : "角色");
                }
            }
            result.syncTags();
        }
        try { result.savedAt = java.time.OffsetDateTime.parse(draft.updatedAt).toInstant().toEpochMilli(); }
        catch (RuntimeException ignored) { result.savedAt = System.currentTimeMillis(); }
        return result;
    }
    public static JsonObject copy(JsonObject json) { return GSON.fromJson(GSON.toJson(json), JsonObject.class); }
}
