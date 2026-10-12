package com.pantrybase.api.cooking.web;

import com.pantrybase.api.cooking.dto.CookingSessionResponse;
import com.pantrybase.api.cooking.dto.CreateCookingSessionRequest;
import com.pantrybase.api.cooking.dto.UpdateCookingSessionStateRequest;
import com.pantrybase.api.cooking.service.CookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cooking sessions for the authenticated user.
 *
 * <p>The owner comes from the authentication principal, never from the body or the
 * path, so no caller can open or advance another user's session. The state endpoint is
 * a PUT on {@code /state} rather than a PATCH on the session because it drives a state
 * machine — the set of allowed moves is the resource, and a merge patch could not
 * express "complete, and accept the shortfall".</p>
 */
@RestController
@RequestMapping("/api/cooking/sessions")
public class CookingController {

    private final CookingService cookingService;

    public CookingController(CookingService cookingService) {
        this.cookingService = cookingService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CookingSessionResponse create(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody CreateCookingSessionRequest request) {
        return cookingService.create(userId, request);
    }

    @GetMapping
    public List<CookingSessionResponse> list(@AuthenticationPrincipal Long userId) {
        return cookingService.list(userId);
    }

    @GetMapping("/{sessionId}")
    public CookingSessionResponse get(@AuthenticationPrincipal Long userId,
                                      @PathVariable long sessionId) {
        return cookingService.get(userId, sessionId);
    }

    /**
     * Advances a session; completing it deducts the recipe from the pantry.
     *
     * <p>A completed response carries the {@code deduction} breakdown so the caller does
     * not have to re-read the pantry to tell the user what just changed.</p>
     */
    @PutMapping("/{sessionId}/state")
    public CookingSessionResponse updateState(@AuthenticationPrincipal Long userId,
                                              @PathVariable long sessionId,
                                              @Valid @RequestBody UpdateCookingSessionStateRequest request) {
        return cookingService.updateState(userId, sessionId, request);
    }
}