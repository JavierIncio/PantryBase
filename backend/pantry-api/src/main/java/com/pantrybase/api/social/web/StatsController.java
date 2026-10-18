package com.pantrybase.api.social.web;

import com.pantrybase.api.social.dto.UserStatsResponse;
import com.pantrybase.api.social.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile statistics of the authenticated user.
 *
 * <p>Mounted under {@code /api/users/me} to match the profile surface, but served from
 * the social module because it is derived from the cooking history.</p>
 */
@Tag(name = "Profile stats", description = "Cooking statistics for the authenticated user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users/me")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @Operation(summary = "Get my cooking statistics",
            description = "Cooking sessions completed, distinct recipes cooked and most used ingredients")
    @GetMapping("/stats")
    public UserStatsResponse stats(@AuthenticationPrincipal Long userId) {
        return service.stats(userId);
    }
}
