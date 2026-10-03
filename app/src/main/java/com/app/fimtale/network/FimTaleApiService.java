package com.app.fimtale.network;
import com.app.fimtale.model.*;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/** Routes from ft-front/schema/openapi.json. No deprecated v1 endpoints. */
public interface FimTaleApiService {
    @GET("user/get_user") Call<CurrentUser> getCurrentUser(@Header("Token") String token);
    @POST("user/logout") Call<ResponseBody> logout(@Header("Token") String token);
    @GET("search/search_works") Call<TopicListResponse> getTopicList(
            @Query("page") int page, @Query("query") String query, @Query("rank") String rank);
    @GET("search/work_feed") Call<TopicListResponse> getFeed(@Query("page") int page);
    @GET("work/get_curated_works") Call<CuratedResponse> getCuratedWorks(@Query("page") int page);
    @GET("work/get_work") Call<TopicDetailResponse> getWork(@Query("work_id") int workId);
    @GET("work/get_chapter") Call<ChapterResponse> getChapter(@Query("chapter_id") int chapterId);
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
