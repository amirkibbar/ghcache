package ajk.ghcache.services;

import ajk.ghcache.config.GHCacheProperties;
import ajk.ghcache.services.repositories.ResponseRepository;
import com.codahale.metrics.MetricRegistry;
import org.apache.commons.logging.Log;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.client.fluent.Executor.newInstance;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.impl.client.HttpClientBuilder.create;

@Service
public class CacheService {
    private Log log = getLog(getClass());

    @Autowired
    private GHCacheProperties props;

    @Autowired
    private ResponseRepository cache;

    @Autowired
    private MetricRegistry metrics;

    private Pattern cacheMaxAge = Pattern.compile(".*max-age=(\\p{Digit}*).*", CASE_INSENSITIVE);

    @Cacheable(value = "responses", unless = "#result == null || #root.args[1]")
    public CachedResponse fetch(String path, boolean force) {
        CachedResponse response;
        if (force) {
            response = cache.store(path, fetchFromRemote(path));
        } else {
            if (props.getCachedUris().contains(path)) {
                response = cache.fetch(path);
                if (response == null) {
                    // this path is not in the cache, let's try the real thing
                    response = cache.store(path, fetchFromRemote(path));
                } else {
                    metrics.meter("cache.cache-fetch").mark();
                }
            } else {
                response = fetchFromRemote(path);
            }
        }

        if (response != null) {
            response.setContent(decompress(response.getContent()));
        }

        return response;
    }

    private CachedResponse fetchFromRemote(String path) {
        log.info("fetching direct " + path);
        metrics.meter("cache.remote-fetch").mark();

        try {
            URL url = props.getBaseRemoteUrl();
            String username = props.getApiToken().split(":")[0];
            String token = props.getApiToken().split(":")[1];

            // make the initial request
            HttpResponse response = newInstance(create().build())
                    .authPreemptive(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
                    .auth(username, token)
                    .execute(Get(props.getBaseRemoteUrl().toString() + path))
                    .returnResponse();

            // create the result object to be cached
            CachedResponse result = new CachedResponse(response);

            // if this is a multiple item result, then we need to iterate over all pages by following the Link header
            if (response.getFirstHeader("Link") != null) {
                // fetch next page
                String nextUrl = readNextLink(response.getFirstHeader("Link"));
                do {
                    response = newInstance(create().build())
                            .authPreemptive(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
                            .auth(username, token)
                            .execute(Get(nextUrl))
                            .returnResponse();

                    // flatten result
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);

                    StringBuilder additionalContent = new StringBuilder(new String(out.toByteArray(), Charset.forName("UTF-8")));
                    additionalContent.deleteCharAt(additionalContent.indexOf("["));

                    StringBuilder builder = new StringBuilder(result.getContent());
                    builder.deleteCharAt(builder.lastIndexOf("]")).append(", ").append(additionalContent);

                    result.setContent(builder.toString());

                    nextUrl = readNextLink(response.getFirstHeader("Link"));
                } while (nextUrl != null);
            }

            // calculate the cache expiration using the last response
            result.setValidUntil(calculateCacheValidity(response.getFirstHeader("Cache-Control")));

            // unfortunately for me the max size of the Consul value is 512K and we need more than that. Luckily all the
            // responses are plain text (JSON) and compress well, so we'll store them compressed
            result.setContent(compress(result.getContent()));

            return result;
        } catch (IOException e) {
            log.warn("unable to fetch " + props.getBaseRemoteUrl().toString() + path + ", " + e.getMessage(), e);
            return null;
        }
    }

    private String decompress(String src) {
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(src.getBytes()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(in)) {
            copy(gzip, out);
        } catch (Exception e) {
            log.warn("unable to decompress " + src + ", " + e.getMessage(), e);
            return "";
        }

        return new String(out.toByteArray());
    }

    private String compress(String src) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(src.length());
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            copy(new ByteArrayInputStream(src.getBytes()), gzip);
        } catch (Exception e) {
            log.warn("error compressing " + src + ", " + e.getMessage(), e);
            return "";
        }

        return new String(Base64.getEncoder().encode(out.toByteArray()));
    }

    private String readNextLink(Header linkHeader) {
        if (linkHeader == null) {
            return null;
        }

        for (HeaderElement element : linkHeader.getElements()) {
            if (element.getParameterByName("rel").getValue().equalsIgnoreCase("next")) {
                // reassemble the URL - it's separated into name and value because it contains =
                return element.getName().substring(1) +
                        "=" +
                        element.getValue().substring(0, element.getValue().lastIndexOf(">"));
            }
        }

        return null;
    }

    private long calculateCacheValidity(Header cacheControlHeader) {
        long defaultValidity = System.currentTimeMillis() + (props.getCacheMinutes() * 60 * 1000);
        if (!props.isRespectGitHubCacheConfig() || cacheControlHeader == null) {
            // either there's no cache-control header or we're not respecting it (by configuration)
            return defaultValidity;
        }

        // extract the max-age value from the cache-control header, if it's there
        String value = cacheControlHeader.getValue();
        Matcher matcher = cacheMaxAge.matcher(value == null ? "" : value);
        if (value == null || value.isEmpty() || !matcher.matches()) {
            return defaultValidity;
        }

        try {
            return System.currentTimeMillis() + (Integer.parseInt(matcher.group(1)) * 1000);
        } catch (NumberFormatException e) {
            // failed to extract - return default value
            return defaultValidity;
        }
    }

    public void rebuild() {
        metrics.counter("cache.rebuild").inc();

        for (String uri : props.getCachedUris()) {
            fetch(uri, true);
        }
    }
}
