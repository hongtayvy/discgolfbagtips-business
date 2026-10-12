package com.discgolfbagtips.business.cache;

import com.discgolfbagtips.business.retrieval.RetrievalResult;

/**
 * Which analyses are worth keeping, decided in one place so the recommendation and lineup endpoints
 * cannot disagree.
 *
 * <p>A degraded answer is returned to the caller but not stored. Caching it would turn a short outage
 * into up to a day of quietly worse suggestions, long after the cause has been fixed.
 *
 * <p>Two things count as degraded:
 * <ul>
 *   <li><b>Retrieval fell back to flight-number search.</b> The embedding service failed, whether
 *       briefly or because the account ran out of credits. The answer is usable but not semantic, and
 *       the fix is something outside this process, so it must not outlive that fix.</li>
 *   <li><b>The reasoning model failed transiently</b> (recommendations only; the lineup never calls
 *       one). A missing API key is a settled state, not a blip, so that response does cache.</li>
 * </ul>
 */
public final class CacheEligibility {

    private static final String FALLBACK = RetrievalResult.RetrievalMode.FLIGHT_NUMBER_FALLBACK.name();

    private CacheEligibility() {
    }

    public static boolean recommendation(String retrievalMode, boolean generationFailedTransiently) {
        return !isFallback(retrievalMode) && !generationFailedTransiently;
    }

    public static boolean lineup(String retrievalMode) {
        return !isFallback(retrievalMode);
    }

    private static boolean isFallback(String retrievalMode) {
        return FALLBACK.equals(retrievalMode);
    }
}
