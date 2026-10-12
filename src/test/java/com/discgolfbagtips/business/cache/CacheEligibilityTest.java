package com.discgolfbagtips.business.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheEligibilityTest {

    private static final String VECTOR = "VECTOR_SIMILARITY";
    private static final String FALLBACK = "FLIGHT_NUMBER_FALLBACK";

    @Test
    void aHealthyRecommendationIsKept() {
        assertThat(CacheEligibility.recommendation(VECTOR, false)).isTrue();
    }

    @Test
    void aRecommendationWhoseRetrievalFellBackIsNotKept() {
        // Regression: only the model failure used to be checked, so an embedding outage (or an
        // account out of credits) was cached for 24h and kept serving non-semantic results after
        // the cause was fixed.
        assertThat(CacheEligibility.recommendation(FALLBACK, false)).isFalse();
    }

    @Test
    void aTransientModelFailureIsStillNotKept() {
        assertThat(CacheEligibility.recommendation(VECTOR, true)).isFalse();
    }

    @Test
    void bothFailuresTogetherAreNotKept() {
        assertThat(CacheEligibility.recommendation(FALLBACK, true)).isFalse();
    }

    @Test
    void aLineupIsKeptUnlessRetrievalFellBack() {
        assertThat(CacheEligibility.lineup(VECTOR)).isTrue();
        assertThat(CacheEligibility.lineup(FALLBACK)).isFalse();
    }

    @Test
    void theTwoEndpointsAgreeOnRetrieval() {
        // The bug was the two paths drifting apart. For retrieval they must give the same verdict.
        for (String mode : new String[] {VECTOR, FALLBACK}) {
            assertThat(CacheEligibility.recommendation(mode, false)).isEqualTo(CacheEligibility.lineup(mode));
        }
    }

    @Test
    void anUnknownModeIsTreatedAsHealthy() {
        // Only the one known degraded mode is excluded; this must not silently stop caching if a
        // new healthy mode is ever added.
        assertThat(CacheEligibility.recommendation("SOMETHING_NEW", false)).isTrue();
        assertThat(CacheEligibility.lineup(null)).isTrue();
    }
}
