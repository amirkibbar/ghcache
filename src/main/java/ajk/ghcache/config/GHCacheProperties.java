package ajk.ghcache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;
import java.util.List;

import static ajk.ghcache.config.GHCacheProperties.RepoView.ToNumConverter.fromNumber;

@Data
@ConfigurationProperties(prefix = "github")
public class GHCacheProperties {
    /**
     * The base URL of the remote GitHub service. This is configurable to support Enterprise GitHub where this URL may
     * be different
     */
    private URL baseRemoteUrl;

    /**
     * The GitHub API token. This is a GitHub personal authentication token in the format of <code>username:token</code>,
     * for example: <code>amirkibbar:12ab3cd4efa567b89cd0e1fa234b5fa56b7c8d90</code>
     */
    private String apiToken;

    /**
     * A list of URIs to cache, anything outside this list will be proxied with a simple pass-through
     */
    private List<String> cachedUris;

    /**
     * Base URL of the Consul server. The GitHub cache uses the Consul key-value store to store the cached responses
     * from GitHub
     */
    private URL consulUrl;

    /**
     * The root folder in the Consul key-value store under which the results are stored
     */
    private String consulKVRoot = "github-cache";

    /**
     * Number of minutes to cache results, unless the respectGitHubCacheConfig is true in which case if the result
     * specify a Cache-Control header in which case the header will take precedence
     */
    private int cacheMinutes = 10;

    /**
     * GitHub provides a Cache-Control header on its responses. If this property is set to true we'll respect their
     * cache configuration, otherwise we'll just ignore it and use the cacheMinutes instead
     */
    private boolean respectGitHubCacheConfig = false;

    /**
     * A list of views to be fetched periodically and store
     */
    private List<RepoView> repoViews;

    /**
     * The path to the repositories used to calculate the view
     */
    private String repoViewsRoot;

    /**
     * The views refresh rate in minutes - this is the interval between view data refreshes
     */
    private int viewRefreshMinutes = 15;

    /**
     * The cached URIs refresh rate in minutes - this is the interval between cached URIs data refresh, it usually makes
     * sense to keep this number lower than the cacheMinutes
     */
    private int cachedUrisRefreshMinutes = 9;

    @Data
    public static class RepoView {
        /**
         * Name of field to store for view
         */
        private String field;

        /**
         * To number converter, right now only a fromDate and fromNumber converters are supported. This is required to
         * allow the cache to store the value as a number to be able to return the top N results. This can be later
         * changed to a simple sortable format instead of a number if there's ever a need
         */
        private ToNumConverter converter = fromNumber;

        /**
         * The path under which this view is fetched. This path is appended to the url like this: /view/top/{N}/{path}.
         * If left empty then the default path is the same as the field
         */
        private String path;

        public enum ToNumConverter {
            fromNumber, fromDate
        }
    }
}
