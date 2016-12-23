package ajk.ghcache.services.repositories;

import ajk.ghcache.config.GHCacheProperties;
import ajk.ghcache.services.CachedResponse;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.apache.commons.logging.LogFactory.getLog;
import static org.apache.http.client.fluent.Executor.newInstance;
import static org.apache.http.client.fluent.Request.Delete;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Put;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;

@Component
public class ConsulResponseRepository implements ResponseRepository {
    private Log log = getLog(getClass());

    @Autowired
    private GHCacheProperties props;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ConsulUtils consulUtils;

    @Autowired
    private MetricRegistry metrics;

    public CachedResponse store(String path, CachedResponse result) {
        log.info("caching " + path);

        try {
            String storeUrl = props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + path;
            HttpResponse response = newInstance().execute(Put(storeUrl)
                    .bodyString(mapper.writeValueAsString(result), TEXT_PLAIN)).returnResponse();
            log.info(path + ": " + response.getStatusLine());
        } catch (IOException e) {
            log.warn("couldn't cache path " + path + ", " + e.getMessage(), e);
        }

        return result;
    }

    public CachedResponse fetch(String path) {
        try {
            // do some error validation - if the value doesn't exist in Consul - return null
            HttpResponse rawResponse = newInstance().execute(Get(getStoreUrl(path))).returnResponse();
            String decoded = consulUtils.responseToJson(rawResponse);
            if (decoded == null) {
                // cache miss - this path isn't cached yet
                metrics.meter("responses.cache-miss").mark();
                return null;
            }

            // before returning - validate the freshness of the value, it may be stale
            CachedResponse cachedResponse = mapper.readValue(decoded, CachedResponse.class);
            if (cachedResponse.getValidUntil() >= System.currentTimeMillis()) {
                // cache hit - good to go
                metrics.meter("responses.cache-hit").mark();
                return cachedResponse;
            } else {
                // cache miss - value is stale, evict it and return nothing
                metrics.meter("responses.cache-miss").mark();
                evict(path);
                return null;
            }
        } catch (IOException e) {
            // if something's wrong, let's just say we don't have this value to allow for path-through to work
            log.warn("error fetching " + path + " from cache, " + e.getMessage(), e);
            return null;
        }
    }

    private String getStoreUrl(String path) {
        return props.getConsulUrl().toString() + "/v1/kv/" + props.getConsulKVRoot() + path;
    }

    private void evict(String path) {
        try {
            newInstance().execute(Delete(getStoreUrl(path))).discardContent();
        } catch (IOException e) {
            log.warn("couldn't evict " + path, e);
            // no consequences, the value won't be used because it's stale
        }
    }
}
