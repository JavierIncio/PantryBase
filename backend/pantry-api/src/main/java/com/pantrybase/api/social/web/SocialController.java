package com.pantrybase.api.social.web;

import com.pantrybase.api.social.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Favorite and saved-recipe toggles for the authenticated user.
 *
 * <p>Every operation is idempotent and owner-scoped: the caller id comes from the token,
 * never from the path, so a client can never act on another user's marks.</p>
 */
@Tag(name = "Social", description = "Favorites and saved recipes")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class SocialController {

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    @Operation(summary = "Mark a recipe as favorite (idempotent)")
    @PutMapping("/recipes/{recipeId}/favorite")
    public ResponseEntity<Void> addFavorite(@AuthenticationPrincipal Long userId,
                                            @PathVariable long recipeId) {
        service.addFavorite(userId, recipeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove the favorite mark (idempotent)")
    @DeleteMapping("/recipes/{recipeId}/favorite")
    public ResponseEntity<Void> removeFavorite(@AuthenticationPrincipal Long userId,
                                               @PathVariable long recipeId) {
        service.removeFavorite(userId, recipeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Save a recipe for later (idempotent)")
    @PutMapping("/recipes/{recipeId}/saved")
    public ResponseEntity<Void> addSaved(@AuthenticationPrincipal Long userId,
                                         @PathVariable long recipeId) {
        service.addSaved(userId, recipeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove the saved mark (idempotent)")
    @DeleteMapping("/recipes/{recipeId}/saved")
    public ResponseEntity<Void> removeSaved(@AuthenticationPrincipal Long userId,
                                            @PathVariable long recipeId) {
        service.removeSaved(userId, recipeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List my favorite recipe ids, most recent first")
    @GetMapping("/favorites")
    public ResponseEntity<List<Long>> favorites(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.favoriteIds(userId));
    }

    @Operation(summary = "List my saved recipe ids, most recent first")
    @GetMapping("/saved")
    public ResponseEntity<List<Long>> saved(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.savedIds(userId));
    }
}
