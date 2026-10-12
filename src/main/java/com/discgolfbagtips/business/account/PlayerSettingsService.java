package com.discgolfbagtips.business.account;

import com.discgolfbagtips.business.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A player's standing preferences, kept per signed-in user.
 *
 * <p>Updates are a merge, not a replace: a field that is present is set, an explicit {@code null}
 * clears it, and a field that is absent is left alone. That is what lets two browsers change
 * different things without one wiping the other's change with a stale full copy.
 */
@Service
public class PlayerSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PlayerSettingsService.class);

    private static final List<String> FIELDS = List.of(
            "skillLevel", "skillInputMode", "pdgaRating", "throwingStyle", "courseType", "theme");

    private final PlayerSettingsRepository repository;
    private final ObjectMapper objectMapper;

    public PlayerSettingsService(PlayerSettingsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PlayerSettings get(String ownerKey) {
        return repository.findById(ownerKey).map(this::read).orElse(PlayerSettings.EMPTY);
    }

    @Transactional
    public PlayerSettings update(String ownerKey, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw invalid("Send a JSON object of the settings to change.");
        }

        ObjectNode merged = repository.findById(ownerKey)
                .map(entity -> storedTree(entity))
                .orElseGet(objectMapper::createObjectNode);

        for (Map.Entry<String, JsonNode> field : patch.properties()) {
            if (!FIELDS.contains(field.getKey())) {
                throw invalid("Unknown setting '%s'. Known settings: %s.".formatted(field.getKey(), FIELDS));
            }
            if (field.getValue().isNull()) {
                merged.remove(field.getKey());
            } else {
                merged.set(field.getKey(), field.getValue());
            }
        }

        PlayerSettings settings = parse(merged);
        validate(settings);

        String json = objectMapper.writeValueAsString(settings);
        repository.findById(ownerKey).ifPresentOrElse(
                entity -> entity.replace(json),
                () -> repository.save(new PlayerSettingsEntity(ownerKey, json)));
        return settings;
    }

    private PlayerSettings read(PlayerSettingsEntity entity) {
        try {
            return objectMapper.readValue(entity.settings(), PlayerSettings.class);
        } catch (JacksonException ex) {
            // A document from an older build that no longer parses should not lock the player out.
            log.warn("Unreadable stored player settings, treating as empty: {}", ex.getOriginalMessage());
            return PlayerSettings.EMPTY;
        }
    }

    private ObjectNode storedTree(PlayerSettingsEntity entity) {
        try {
            JsonNode tree = objectMapper.readTree(entity.settings());
            if (tree instanceof ObjectNode object) {
                // Drop anything this build no longer knows, so one stale key cannot block every save.
                List<String> stale = new ArrayList<>();
                object.propertyNames().forEach(name -> {
                    if (!FIELDS.contains(name)) {
                        stale.add(name);
                    }
                });
                stale.forEach(object::remove);
                return object;
            }
        } catch (JacksonException ex) {
            log.warn("Unreadable stored player settings, starting over: {}", ex.getOriginalMessage());
        }
        return objectMapper.createObjectNode();
    }

    private PlayerSettings parse(ObjectNode merged) {
        try {
            return objectMapper.treeToValue(merged, PlayerSettings.class);
        } catch (JacksonException ex) {
            throw invalid("A setting has a value this API does not accept: " + ex.getOriginalMessage());
        }
    }

    private static void validate(PlayerSettings settings) {
        Integer rating = settings.pdgaRating();
        if (rating != null
                && (rating < PlayerSettings.MIN_PDGA_RATING || rating > PlayerSettings.MAX_PDGA_RATING)) {
            throw invalid("pdgaRating must be between %d and %d."
                    .formatted(PlayerSettings.MIN_PDGA_RATING, PlayerSettings.MAX_PDGA_RATING));
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid-settings", message);
    }
}
