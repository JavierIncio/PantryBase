package com.pantrybase.api.user.web;

import com.pantrybase.api.user.dto.AllergenResponse;
import com.pantrybase.api.user.dto.AllergyExclusionsResponse;
import com.pantrybase.api.user.dto.ReplaceAllergyExclusionsRequest;
import com.pantrybase.api.user.dto.UserPreferencesRequest;
import com.pantrybase.api.user.dto.UserPreferencesResponse;
import com.pantrybase.api.user.dto.UserResponse;
import com.pantrybase.api.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for handling user-related endpoints.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param userId ID of the authenticated user
     * @return the user's profile
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.me(userId);
    }

    /**
     * Returns the preferences of the currently authenticated user.
     *
     * @param userId ID of the authenticated user
     * @return the user's preferences
     */
    @GetMapping("/preferences")
    public UserPreferencesResponse preferences(@AuthenticationPrincipal Long userId) {
        return userService.getPreferences(userId);
    }

    /**
     * Replaces the whole preferences resource; the row is created on first save.
     *
     * @param userId  the ID of the authenticated user
     * @param request the full preference set (PUT semantics)
     * @return the applied preferences
     */
    @PutMapping("/preferences")
    public UserPreferencesResponse savePreferences(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody UserPreferencesRequest request) {
        return userService.savePreferences(userId, request);
    }

    /**
     * Returns the allergy exclusions of the currently authenticated user.
     *
     * @param userId ID of the authenticated user
     * @return the user's allergy exclusions
     */
    @GetMapping("/allergy-exclusions")
    public AllergyExclusionsResponse allergyExclusions(@AuthenticationPrincipal Long userId) {
        return userService.getExclusions(userId);
    }

    /**
     * Replaces the allergy exclusions of the currently authenticated user.
     *
     * <p>All supplied allergen codes must correspond to known allergens.
     * Unknown codes result in a validation error.</p>
     *
     * @param userId  ID of the authenticated user
     * @param request new allergy exclusions
     * @return the updated allergy exclusions
     */
    @PutMapping("/allergy-exclusions")
    public AllergyExclusionsResponse replaceAllergyExclusions(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody ReplaceAllergyExclusionsRequest request) {
        return userService.replaceExclusions(userId, request);
    }

    @GetMapping("/allergens")
    public List<AllergenResponse> allergens() {
        return userService.getAllergenCatalog();
    }
}
