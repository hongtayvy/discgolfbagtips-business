package com.discgolfbagtips.business.account;

import com.discgolfbagtips.business.player.CourseType;
import com.discgolfbagtips.business.player.SkillLevel;
import com.discgolfbagtips.business.player.ThrowingStyle;

/**
 * A signed-in player's standing preferences. Every field is optional: a missing one means the player
 * has not chosen yet and the front end falls back to its own default.
 *
 * @param skillLevel      the level sent with each analysis
 * @param skillInputMode  how the player prefers to enter skill; the UI opens in this mode
 * @param pdgaRating      their rating when they have one, kept so it need not be retyped
 * @param theme           light or dark, applied wherever they sign in
 */
public record PlayerSettings(
        SkillLevel skillLevel,
        SkillInputMode skillInputMode,
        Integer pdgaRating,
        ThrowingStyle throwingStyle,
        CourseType courseType,
        Theme theme) {

    /** Outside this a number is a typo, not a rating; matches the front end's input check. */
    public static final int MIN_PDGA_RATING = 200;
    public static final int MAX_PDGA_RATING = 1200;

    public static final PlayerSettings EMPTY = new PlayerSettings(null, null, null, null, null, null);

    public enum SkillInputMode {
        LEVEL, PDGA
    }

    public enum Theme {
        LIGHT, DARK
    }
}
