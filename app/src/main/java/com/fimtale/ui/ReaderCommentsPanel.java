package com.fimtale.ui;

import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fimtale.LoginActivity;
import com.fimtale.R;
import com.fimtale.adapter.CommentAdapter;
import com.fimtale.model.WorkCommentRequest;
import com.fimtale.model.WorkCommentsResponse;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.RetrofitClient;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Comments and composer used by the reader's inter-chapter page and bottom sheet. */
public final class ReaderCommentsPanel {
    private static final int PER_PAGE = 16;

    private final AppCompatActivity activity;
    private final View root;
    private final int workId;
    private final RecyclerView list;
    private final CommentAdapter adapter;
    private final ShimmerSkeletonView skeleton;
    private final TextView title, status, pageLabel;
    private final MaterialButton refresh, sort, previous, next, send, emoji;
    private final TextInputEditText input;
    private final View pager;
    private Call<WorkCommentsResponse> activeCall;
    private Call<Void> commentCall;
    private int chapterId;
    private int page = 1;
    private int totalPages = 1;
    private boolean descending;
    private boolean sending;
    private boolean closed;

    public ReaderCommentsPanel(AppCompatActivity activity, View root, int workId) {
        this.activity = activity;
        this.root = root;
        this.workId = workId;
        list = root.findViewById(R.id.readerCommentsList);
        list.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new CommentAdapter(new ArrayList<>(), activity);
        list.setAdapter(adapter);
        list.setItemAnimator(null);

        skeleton = root.findViewById(R.id.readerCommentsSkeleton);
        skeleton.setSkeletonLayout(ShimmerSkeletonView.Layout.COMMENTS);
        title = root.findViewById(R.id.readerCommentsTitle);
        status = root.findViewById(R.id.readerCommentsStatus);
        pageLabel = root.findViewById(R.id.readerCommentsPage);
        pager = root.findViewById(R.id.readerCommentsPager);
        refresh = root.findViewById(R.id.readerCommentsRefresh);
        sort = root.findViewById(R.id.readerCommentsSort);
        previous = root.findViewById(R.id.readerCommentsPrevious);
        next = root.findViewById(R.id.readerCommentsNext);
        input = root.findViewById(R.id.readerCommentComposerInput);
        emoji = root.findViewById(R.id.readerCommentComposerEmoji);
        send = root.findViewById(R.id.readerCommentComposerSend);

        refresh.setOnClickListener(v -> load(1));
        sort.setOnClickListener(v -> {
            descending = !descending;
            sort.setText(descending ? "从晚到早" : "从早到晚");
            load(1);
        });
        previous.setOnClickListener(v -> load(page - 1));
        next.setOnClickListener(v -> load(page + 1));
        send.setOnClickListener(v -> submit());
        emoji.setOnClickListener(v -> FtemojiPicker.show(activity,
                name -> insert(":ftemoji_" + name + ":")));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    public void bind(int chapterId) {
        this.chapterId = chapterId;
        title.setText("评论");
        load(1);
    }

    private boolean valid(Call<?> call) {
        return !closed && !activity.isFinishing() && !activity.isDestroyed()
                && !call.isCanceled() && call == activeCall;
    }

    private void load(int requestedPage) {
        if (closed || requestedPage < 1) return;
        if (activeCall != null) activeCall.cancel();
        updatePager(true);
        status.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        skeleton.setVisibility(View.VISIBLE);
        activeCall = RetrofitClient.getInstance().getWorkComments(workId, requestedPage, PER_PAGE,
                "created_at", descending ? "desc" : "asc");
        activeCall.enqueue(new Callback<WorkCommentsResponse>() {
            @Override public void onResponse(Call<WorkCommentsResponse> call, Response<WorkCommentsResponse> response) {
                if (!valid(call)) return;
                if (response.isSuccessful() && response.body() != null) {
                    WorkCommentsResponse result = response.body();
                    totalPages = result.totalPages(PER_PAGE);
                    if (requestedPage > totalPages) { load(totalPages); return; }
                    page = requestedPage;
                    adapter.updateData(result.getItems());
                    title.setText("评论（" + result.total + "）");
                    skeleton.setVisibility(View.GONE);
                    list.setVisibility(View.VISIBLE);
                    updatePager(false);
                    if (result.getItems().isEmpty()) {
                        status.setText("暂无评论，点击刷新");
                        status.setVisibility(View.VISIBLE);
                        status.setOnClickListener(v -> load(1));
                    }
                } else showError(ApiErrors.message(response));
            }

            @Override public void onFailure(Call<WorkCommentsResponse> call, Throwable error) {
                if (valid(call)) showError("评论加载失败");
            }
        });
    }

    private void showError(String message) {
        skeleton.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        updatePager(false);
        status.setText(message + "，点击重试");
        status.setVisibility(View.VISIBLE);
        status.setOnClickListener(v -> load(page));
    }

    private void updatePager(boolean loading) {
        pager.setVisibility(totalPages > 1 ? View.VISIBLE : View.GONE);
        previous.setEnabled(!loading && page > 1);
        next.setEnabled(!loading && page < totalPages);
        refresh.setEnabled(!loading);
        sort.setEnabled(!loading);
        pageLabel.setText(page + " / " + totalPages);
    }

    private void submit() {
        if (sending || closed) return;
        String content = input.getText() == null ? "" : input.getText().toString().trim();
        if (content.isEmpty()) {
            input.setError("评论内容不能为空");
            return;
        }
        if (!UserPreferences.isLoggedIn(activity)) {
            activity.startActivity(new Intent(activity, LoginActivity.class));
            return;
        }

        String token = UserPreferences.getToken(activity);
        sending = true;
        input.setEnabled(false);
        emoji.setEnabled(false);
        send.setEnabled(false);
        Integer chapter = chapterId > 0 ? chapterId : null;
        commentCall = RetrofitClient.getInstance().createUpdateComment(token,
                new WorkCommentRequest(workId, content, chapter, null));
        commentCall.enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!validComment(call)) return;
                finishSubmit();
                if (response.isSuccessful()) {
                    input.setText("");
                    input.clearFocus();
                    hideKeyboard();
                    descending = true;
                    sort.setText("从晚到早");
                    load(1);
                    Toast.makeText(activity, "评论已发送", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, ApiErrors.message(response), Toast.LENGTH_LONG).show();
                    if (response.code() == 401) activity.startActivity(new Intent(activity, LoginActivity.class));
                }
            }

            @Override public void onFailure(Call<Void> call, Throwable error) {
                if (!validComment(call)) return;
                finishSubmit();
                Toast.makeText(activity, "评论发送失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validComment(Call<?> call) {
        return !closed && !activity.isFinishing() && !activity.isDestroyed()
                && !call.isCanceled() && call == commentCall;
    }

    private void finishSubmit() {
        commentCall = null;
        sending = false;
        input.setEnabled(true);
        emoji.setEnabled(true);
        send.setEnabled(true);
    }

    private void insert(String value) {
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(start, input.getSelectionEnd());
        input.getText().replace(start, end, value);
        input.requestFocus();
        input.setSelection(Math.min(input.length(), start + value.length()));
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    public void close() {
        closed = true;
        if (activeCall != null) activeCall.cancel();
        if (commentCall != null) commentCall.cancel();
        list.setAdapter(null);
    }
}
