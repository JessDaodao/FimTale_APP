package com.fimtale.editor;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import com.fimtale.network.ApiErrors;
import com.fimtale.network.FimTaleApiService;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import retrofit2.Response;

/** Blocking transport, called only from draft IO executors. */
public final class DraftRemote {
    private final FimTaleApiService api;
    private final String token;
    public DraftRemote(FimTaleApiService api, String token) { this.api = api; this.token = token; }
    public static class Failure extends IOException {
        public final int status;
        public Failure(int status, String message) { super(message); this.status = status; }
    }
    private static <T> T body(Response<T> response) throws IOException {
        if (!response.isSuccessful()) throw new Failure(response.code(), ApiErrors.message(response));
        return response.body();
    }
    public OnlineDraft get(String key) throws IOException {
        Response<OnlineDraft> response = api.getDraft(token, key).execute();
        return response.code() == 404 ? null : body(response);
    }
    public List<OnlineDraft.Summary> list() throws IOException {
        List<OnlineDraft.Summary> list = body(api.getDrafts(token, java.util.Arrays.asList("work:", "chapter:")).execute());
        return list == null ? Collections.emptyList() : list;
    }
    public OnlineDraft save(OnlineDraft.Save draft) throws IOException {
        OnlineDraft saved = body(api.saveDraft(token, draft).execute());
        if (saved == null || saved.revision <= 0 || !draft.key.equals(saved.key)) throw new IOException(AppStrings.get(R.string.drafts_save_invalid_response));
        return saved;
    }
    public void delete(String key, long revision) throws IOException {
        Response<Void> response = api.deleteDraft(token, new OnlineDraft.Delete(key, revision)).execute();
        if (response.code() != 404) body(response);
    }
}
