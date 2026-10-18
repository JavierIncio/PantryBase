package com.pantrybase.api.social.web;

import com.pantrybase.api.social.dto.HistoryItemResponse;
import com.pantrybase.api.social.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the user's cooking history.
 */
@Tag(name = "History", description = "Cooking history of the authenticated user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final SocialService service;

    public HistoryController(SocialService service) {
        this.service = service;
    }

    @Operation(summary = "List my cooking history", description = "Returns the authenticated user's cooking history (most recent first)")
    @GetMapping
    public ResponseEntity<List<HistoryItemResponse>> history(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.history(userId));
    }
}
