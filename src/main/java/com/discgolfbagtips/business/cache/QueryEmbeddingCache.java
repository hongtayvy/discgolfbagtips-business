package com.discgolfbagtips.business.cache;

import com.discgolfbagtips.business.config.BagTipsProperties;
import com.discgolfbagtips.business.embedding.EmbeddingVector;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Remembers the vector for a query passage so identical text is never embedded twice.
 *
 * <p>This sits <em>below</em> {@link AnalysisCache}, and that is the point. The analysis cache keys on
 * the whole request, so changing a brand filter or switching bag models is a miss there even though
 * neither touches the passage that gets embedded. The passage is built from the gap and the player
 * profile and nothing else, so those requests rebuild byte-identical text and, without this, pay for
 * the same embedding again. The lineup and recommendation calls for one bag also share gaps.
 *
 * <p>Keyed on the exact text plus the model name. A vector depends only on those two things, so a hit
 * is always correct, and switching models can never serve a vector from the old one.
 *
 * <p>Only successful, real vectors are stored: a failure throws out of the loader and leaves nothing
 * behind, and the local hashing stand-in is skipped by the caller because it is cheap and would
 * otherwise inflate the hit rate with answers that cost nothing to produce.
 */
@Component
public class QueryEmbeddingCache {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingCache.class);

    private final Cache<String, EmbeddingVector> cache;
    private final boolean enabled;

    @Autowired
    public QueryEmbeddingCache(BagTipsProperties properties) {
        this(properties.cache().enabled(), properties.cache().ttl(), properties.cache().maxEntries());
    }

    public QueryEmbeddingCache(boolean enabled, Duration ttl, int maxEntries) {
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntries)
                .recordStats()
                .build();
        log.info("Query embedding cache: enabled={} ttl={} maxEntries={}", enabled, ttl, maxEntries);
    }

    /**
     * Returns the cached vector, or computes and stores it. Caffeine runs the loader once per key, so
     * concurrent requests for the same passage — the lineup and recommendation calls the front end
     * fires together — share a single model call instead of racing to make the same one.
     *
     * <p>The returned vector is shared between callers and must be treated as read-only.
     */
    public EmbeddingVector get(String model, String text, Supplier<EmbeddingVector> compute) {
        if (!enabled) {
            return compute.get();
        }
        return cache.get(model + '\n' + text, key -> compute.get());
    }

    public AnalysisCache.Stats stats() {
        CacheStats raw = cache.stats();
        return new AnalysisCache.Stats(enabled, cache.estimatedSize(), raw.hitCount(), raw.missCount(),
                Math.round(raw.hitRate() * 1000.0) / 10.0, raw.evictionCount());
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
