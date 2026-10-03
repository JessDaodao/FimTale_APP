package com.app.fimtale.network;
import java.net.URI;
import com.app.fimtale.BuildConfig;
/** Site navigation and backend authentication are scoped to their configured origins. */
public final class SiteUrls {
    public static final String SITE = BuildConfig.SITE_URL;
    public static final String API = BuildConfig.API_URL;
    private SiteUrls() {}
    public static String work(int id) { return SITE + "/work/" + id; }
    public static String chapter(int workId, int chapterId) { return work(workId) + "/chapter/" + chapterId; }
    public static boolean isSite(String url) { return sameOrigin(url, SITE); }
    public static boolean isApi(String url) { return sameOrigin(url, API); }
    private static boolean sameOrigin(String url, String origin) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url), base = URI.create(origin);
            return base.getScheme().equals(uri.getScheme()) && base.getHost().equals(uri.getHost())
                    && port(base) == port(uri) && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
    private static int port(URI uri) { return uri.getPort() < 0 ? 443 : uri.getPort(); }
    public static String media(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            URI uri = URI.create(SITE + "/").resolve(path);
            return "https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) ? uri.toString() : null;
        } catch (IllegalArgumentException e) { return null; }
    }
}
