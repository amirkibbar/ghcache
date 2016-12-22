package ajk.ghcache.services.repositories;

import ajk.ghcache.services.CachedResponse;

public interface ResponseRepository {
    /**
     * store the result for a path
     *
     * @param path   request path
     * @param result result to store
     * @return the result (as-is for easier usage)
     */
    CachedResponse store(String path, CachedResponse result);

    /**
     * fetch a result for path, unless it's stale or doesn't exist. A stale result should be evicted from the cache
     *
     * @param path request path
     * @return the result if it exists and it's still fresh in the cache. Freshness is determined by the validUntil
     * field of the cached result, otherwise null
     */
    CachedResponse fetch(String path);
}
