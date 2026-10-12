package com.discgolfbagtips.business.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.discgolfbagtips.business.common.ApiException;
import com.discgolfbagtips.business.player.CourseType;
import com.discgolfbagtips.business.player.SkillLevel;
import com.discgolfbagtips.business.player.ThrowingStyle;
import com.discgolfbagtips.business.account.PlayerSettings.SkillInputMode;
import com.discgolfbagtips.business.account.PlayerSettings.Theme;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PlayerSettingsServiceTest {

    private static final String OWNER = "user:abc";

    private final PlayerSettingsRepository repository = mock(PlayerSettingsRepository.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PlayerSettingsService service = new PlayerSettingsService(repository, mapper);

    private JsonNode json(String text) {
        return mapper.readTree(text);
    }

    private void stored(String settingsJson) {
        when(repository.findById(OWNER)).thenReturn(Optional.of(new PlayerSettingsEntity(OWNER, settingsJson)));
    }

    @Test
    void aPlayerWhoHasChosenNothingGetsAnEmptyDocument() {
        when(repository.findById(OWNER)).thenReturn(Optional.empty());

        assertThat(service.get(OWNER)).isEqualTo(PlayerSettings.EMPTY);
    }

    @Test
    void theFirstUpdateCreatesTheRow() {
        when(repository.findById(OWNER)).thenReturn(Optional.empty());

        PlayerSettings result = service.update(OWNER,
                json("{\"skillInputMode\":\"PDGA\",\"pdgaRating\":925,\"skillLevel\":\"ADVANCED\"}"));

        assertThat(result.skillInputMode()).isEqualTo(SkillInputMode.PDGA);
        assertThat(result.pdgaRating()).isEqualTo(925);
        assertThat(result.skillLevel()).isEqualTo(SkillLevel.ADVANCED);
        verify(repository).save(any(PlayerSettingsEntity.class));
    }

    /** Two browsers changing different things must not wipe each other out. */
    @Test
    void anUpdateChangesOnlyTheFieldsItNames() {
        stored("{\"skillLevel\":\"BEGINNER\",\"theme\":\"DARK\",\"throwingStyle\":\"FOREHAND\"}");

        PlayerSettings result = service.update(OWNER, json("{\"skillLevel\":\"PROFESSIONAL\"}"));

        assertThat(result.skillLevel()).isEqualTo(SkillLevel.PROFESSIONAL);
        assertThat(result.theme()).isEqualTo(Theme.DARK);
        assertThat(result.throwingStyle()).isEqualTo(ThrowingStyle.FOREHAND);
    }

    @Test
    void anExplicitNullClearsAField() {
        stored("{\"pdgaRating\":925,\"theme\":\"DARK\"}");

        PlayerSettings result = service.update(OWNER, json("{\"pdgaRating\":null}"));

        assertThat(result.pdgaRating()).isNull();
        assertThat(result.theme()).isEqualTo(Theme.DARK);
    }

    @Test
    void aValueTheApiDoesNotKnowIsRefused() {
        when(repository.findById(OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OWNER, json("{\"skillLevel\":\"WIZARD\"}")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.update(OWNER, json("{\"courseType\":42}")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void anUnknownSettingIsRefusedRatherThanSilentlyDropped() {
        when(repository.findById(OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OWNER, json("{\"favouriteDisc\":\"Buzzz\"}")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("favouriteDisc");
    }

    @Test
    void aRatingOutsideThePlausibleRangeIsRefused() {
        when(repository.findById(OWNER)).thenReturn(Optional.empty());

        for (String bad : new String[] {"199", "1201", "0", "-900"}) {
            assertThatThrownBy(() -> service.update(OWNER, json("{\"pdgaRating\":" + bad + "}")))
                    .as(bad).isInstanceOf(ApiException.class);
        }
        assertThat(service.update(OWNER, json("{\"pdgaRating\":200}")).pdgaRating()).isEqualTo(200);
        assertThat(service.update(OWNER, json("{\"pdgaRating\":1200}")).pdgaRating()).isEqualTo(1200);
    }

    @Test
    void aBodyThatIsNotAnObjectIsRefused() {
        assertThatThrownBy(() -> service.update(OWNER, json("[1,2]"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.update(OWNER, null)).isInstanceOf(ApiException.class);
    }

    /** A document left by an older build must not lock the player out of saving new settings. */
    @Test
    void aStoredDocumentWithRetiredKeysStillAcceptsUpdates() {
        stored("{\"retiredKey\":1,\"courseType\":\"OPEN\"}");

        PlayerSettings result = service.update(OWNER, json("{\"theme\":\"LIGHT\"}"));

        assertThat(result.courseType()).isEqualTo(CourseType.OPEN);
        assertThat(result.theme()).isEqualTo(Theme.LIGHT);
    }

    @Test
    void anUnreadableStoredDocumentReadsAsEmptyInsteadOfFailing() {
        stored("{\"skillLevel\":\"NOT_A_LEVEL\"}");

        assertThat(service.get(OWNER)).isEqualTo(PlayerSettings.EMPTY);
    }
}
