package com.discgolfbagtips.business.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.discgolfbagtips.business.embedding.EmbeddingVector;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QueryEmbeddingCacheTest {

    private static EmbeddingVector vector(float value) {
        return new EmbeddingVector(new float[] {value}, "m", false);
    }

    private static QueryEmbeddingCache cache() {
        return new QueryEmbeddingCache(true, Duration.ofHours(1), 100);
    }

    @Test
    void identicalTextIsEmbeddedOnce() {
        var cache = cache();
        var calls = new AtomicInteger();

        var first = cache.get("model-a", "stable putter", () -> { calls.incrementAndGet(); return vector(1); });
        var second = cache.get("model-a", "stable putter", () -> { calls.incrementAndGet(); return vector(2); });

        assertThat(calls).hasValue(1);
        assertThat(second).isSameAs(first);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void differentTextIsEmbeddedSeparately() {
        var cache = cache();
        var calls = new AtomicInteger();

        cache.get("m", "stable putter", () -> { calls.incrementAndGet(); return vector(1); });
        cache.get("m", "overstable putter", () -> { calls.incrementAndGet(); return vector(2); });

        assertThat(calls).hasValue(2);
    }

    @Test
    void theModelIsPartOfTheKey() {
        // A vector from one model is meaningless against another's index; switching models must
        // never be served the old model's answer.
        var cache = cache();
        var calls = new AtomicInteger();

        cache.get("model-a", "same text", () -> { calls.incrementAndGet(); return vector(1); });
        cache.get("model-b", "same text", () -> { calls.incrementAndGet(); return vector(2); });

        assertThat(calls).hasValue(2);
    }

    @Test
    void aFailureIsNotCached() {
        var cache = cache();
        var calls = new AtomicInteger();

        assertThatThrownBy(() -> cache.get("m", "text", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("embedding service unavailable");
        })).isInstanceOf(IllegalStateException.class);

        // The next attempt must reach the model again rather than replay the failure.
        var recovered = cache.get("m", "text", () -> { calls.incrementAndGet(); return vector(7); });

        assertThat(calls).hasValue(2);
        assertThat(recovered.values()[0]).isEqualTo(7f);
    }

    @Test
    void disabledCacheAlwaysComputes() {
        var cache = new QueryEmbeddingCache(false, Duration.ofHours(1), 100);
        var calls = new AtomicInteger();

        cache.get("m", "text", () -> { calls.incrementAndGet(); return vector(1); });
        cache.get("m", "text", () -> { calls.incrementAndGet(); return vector(1); });

        assertThat(calls).hasValue(2);
        assertThat(cache.stats().enabled()).isFalse();
    }

    @Test
    void concurrentRequestsForTheSamePassageShareOneModelCall() throws Exception {
        var cache = cache();
        var calls = new AtomicInteger();
        int callers = 8;
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<EmbeddingVector>> tasks = java.util.stream.IntStream.range(0, callers)
                    .<Callable<EmbeddingVector>>mapToObj(i -> () -> {
                        start.await();
                        return cache.get("m", "same passage", () -> {
                            calls.incrementAndGet();
                            try {
                                Thread.sleep(50); // long enough for the others to arrive
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return vector(1);
                        });
                    }).toList();
            List<Future<EmbeddingVector>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<EmbeddingVector> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(calls).hasValue(1);
    }

    @Test
    void invalidateAllDropsEverything() {
        var cache = cache();
        var calls = new AtomicInteger();
        cache.get("m", "text", () -> { calls.incrementAndGet(); return vector(1); });

        cache.invalidateAll();
        cache.get("m", "text", () -> { calls.incrementAndGet(); return vector(1); });

        assertThat(calls).hasValue(2);
    }
}
