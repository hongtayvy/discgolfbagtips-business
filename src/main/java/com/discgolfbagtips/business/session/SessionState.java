package com.discgolfbagtips.business.session;

import com.discgolfbagtips.business.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.business.recommendation.dto.RecommendationResponse;
import java.time.Instant;

/** What a visitor accumulates without an account: their last bag and their last answer. */
public record SessionState(
        BagAnalysisRequest lastRequest,
        RecommendationResponse lastRecommendation,
        Instant updatedAt) {

    public static SessionState empty() {
        return new SessionState(null, null, null);
    }

    public boolean hasSavedBag() {
        return lastRequest != null;
    }
}
