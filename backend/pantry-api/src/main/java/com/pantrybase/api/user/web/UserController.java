package com.pantrybase.api.user.web;

import com.pantrybase.api.user.dto.UserResponse;
import com.pantrybase.api.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * Retrieves the authenticated user's information.
     *
     * @param userId the ID of the authenticated user, injected by Spring Security
     * @return a UserResponse containing the user's information
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.me(userId);
    }
}
