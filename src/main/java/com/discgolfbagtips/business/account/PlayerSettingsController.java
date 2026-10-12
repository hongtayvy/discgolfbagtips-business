package com.discgolfbagtips.business.account;

import com.discgolfbagtips.business.config.OpenApiConfig;
import com.discgolfbagtips.business.profile.BagProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * The signed-in player's standing preferences. Covered by the same {@code /api/v1/account/**} rule
 * as {@link AccountController}, so {@code user} is never null here.
 */
@RestController
@RequestMapping("/api/v1/account/settings")
@Tag(name = "Account", description = "The signed-in user (Supabase Auth bearer token required)")
@SecurityRequirement(name = OpenApiConfig.SUPABASE_BEARER)
public class PlayerSettingsController {

    private final PlayerSettingsService service;

    public PlayerSettingsController(PlayerSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The player's saved preferences",
            description = "Every field may be null: nothing has been chosen yet.")
    public PlayerSettings get(@AuthenticationPrincipal Jwt user) {
        return service.get(BagProfileService.userOwnerKey(user.getSubject()));
    }

    @PatchMapping
    @Operation(summary = "Change some preferences",
            description = "Fields present are set, an explicit null clears one, and omitted fields are left "
                    + "alone. Returns the full settings after the change.")
    public PlayerSettings update(@AuthenticationPrincipal Jwt user, @RequestBody JsonNode patch) {
        return service.update(BagProfileService.userOwnerKey(user.getSubject()), patch);
    }
}
