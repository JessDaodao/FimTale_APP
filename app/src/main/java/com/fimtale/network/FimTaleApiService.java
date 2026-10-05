package com.fimtale.network;
import com.fimtale.model.*;
import com.fimtale.editor.*;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/** Routes from ft-front/schema/openapi.json. No deprecated v1 endpoints. */
public interface FimTaleApiService {
    @GET("work/get_drafts") Call<List<OnlineDraft.Summary>> getDrafts(@Header("Token") String token, @Query("prefix") List<String> prefixes);
    @GET("work/get_draft") Call<OnlineDraft> getDraft(@Header("Token") String token, @Query("draft_key") String key);
    @POST("work/save_draft") Call<OnlineDraft> saveDraft(@Header("Token") String token, @Body OnlineDraft.Save draft);
    @POST("work/delete_draft") Call<Void> deleteDraft(@Header("Token") String token, @Body OnlineDraft.Delete draft);
    @GET("user/get_user") Call<CurrentUser> getCurrentUser(@Header("Token") String token);
    @POST("user/logout") Call<ResponseBody> logout(@Header("Token") String token);
    @GET("user/get_active_sessions") Call<List<UserSession>> getActiveSessions(@Header("Token") String token);
    @POST("user/create_api_key") Call<String> createApiKey(@Header("Token") String token);
    @POST("user/logout") Call<Void> logoutSession(@Header("Token") String token, @Body LogoutRequest request);
    @GET("user/get_default_content_filter") Call<ContentFilterDef> getDefaultContentFilter(@Header("Token") String token);
    @POST("user/set_content_filter") Call<UserMaterial> setContentFilter(@Header("Token") String token,
            @Body SetContentFilterRequest request);
    @POST("user/user_blocklist") Call<List<BlockedUser>> updateBlocklist(@Header("Token") String token,
            @Body BlockUserRequest request);
    @GET("search/search_works") Call<TopicListResponse> getTopicList(
            @Query("page") int page, @Query("query") String query, @Query("rank") String rank);
    @GET("search/work_feed") Call<TopicListResponse> getFeed(@Query("page") int page);
    @GET("work/get_curated_works") Call<CuratedResponse> getCuratedWorks(@Query("page") int page);
    @GET("work/get_work") Call<TopicDetailResponse> getWork(@Query("work_id") int workId);
    @GET("work/get_work") Call<TopicDetailResponse> getWorkViewer(@Header("Token") String token, @Query("work_id") int workId);
    @POST("work/do_work_vote") Call<Void> voteWork(@Header("Token") String token, @Body WorkInteractions.Vote vote);
    @POST("work/do_work_high_praise") Call<Void> highPraiseWork(@Header("Token") String token, @Body WorkInteractions.HighPraise highPraise);
    @GET("user/get_favorite_folders") Call<List<WorkInteractions.Folder>> getFavoriteFolders(@Header("Token") String token);
    @POST("work/add_favorite_work") Call<Void> addFavoriteWork(@Header("Token") String token, @Body WorkInteractions.FavoriteRequest favorite);
    @POST("work/remove_favorite_work") Call<Void> removeFavoriteWork(@Header("Token") String token, @Body WorkInteractions.FavoriteRequest favorite);
    @GET("work/get_comments") Call<WorkCommentsResponse> getWorkComments(@Query("work_id") int workId,
            @Query("page") int page, @Query("per_page") int perPage,
            @Query("order_by") String orderBy, @Query("order_option") String orderOption);
    @POST("work/create_update_comment") Call<Void> createUpdateComment(@Header("Token") String token,
            @Body WorkCommentRequest comment);
    @GET("work/get_chapter") Call<ChapterResponse> getChapter(@Query("chapter_id") int chapterId);
    @GET("work/get_comment") Call<CommentResponse> getComment(@Query("comment_id") int commentId);
    @GET("user/get_user_auth") Call<UserAuth> getUserAuth(@Header("Token") String token);
    @GET("work/get_work") Call<WorkEditResponse> getWorkForEdit(@Query("work_id") int workId, @Query("for_edit") boolean forEdit);
    @GET("work/get_chapter") Call<ChapterResponse> getChapterForEdit(@Query("chapter_id") int chapterId, @Query("for_edit") boolean forEdit);
    @POST("work/create_update_work") Call<SaveResult> saveWork(@Header("Token") String token, @Body WorkInput work,
            @Query("captcha_response") String captcha, @Query("captcha_type") String captchaType);
    @POST("work/create_update_chapter") Call<SaveResult> saveChapter(@Header("Token") String token, @Body ChapterInput chapter);
    @GET("tag/list_tags") Call<List<TagGroup>> getEditorTags(@Query("page") int page,
            @Query("per_page") int perPage, @Query("keyword") String keyword, @Query("type_names") List<String> types);
    @Multipart @POST("misc/upload_image") Call<String> uploadImage(@Header("Token") String token, @Part okhttp3.MultipartBody.Part file);
    @GET("user/list_read_progress") Call<HistoryResponse> getHistory(@Query("page") int page);
    @GET("work/get_favorite_works") Call<FavoritesResponse> getFavorites(@Query("page") int page);
    @GET("user/get_user_page_header") Call<UserDetailResponse> getUserDetail(@Query("username") String username);
    @GET("user/get_user_page_tab") Call<UserWorksResponse> getUserTopics(
            @Query("username") String username, @Query("tab") String tab, @Query("page") int page);
    @GET("tag/get_tag") Call<TagInfo> getTag(@Query("tag_name") String tagName);
    @GET("tag/get_tag_works") Call<TopicListResponse> getTagTopics(
            @Query("tag_name") String tagName, @Query("page") int page, @Query("rank") String rank);
    @GET("tag/list_tags") Call<List<TagGroup>> getTags(@Query("page") int page, @Query("keyword") String keyword);
    @POST("user/update_read_progress") Call<ReadProgress> saveReadingProgress(@Body ReadProgress progress);
    @GET Call<UpdateResponse> checkUpdate(@Url String url);
}
