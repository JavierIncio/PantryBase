package com.pantrybase.api.user.service;

import com.pantrybase.api.common.exception.UserNotFoundException;
import com.pantrybase.api.user.domain.User;
import com.pantrybase.api.user.dto.UserResponse;
import com.pantrybase.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Service for handling user-related operations.
 */
@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    /**
     * Retrieves the user information for the specified user ID.
     *
     * @param id the ID of the user to retrieve
     * @return a UserResponse containing the user's information
     * @throws UserNotFoundException if no user with the specified ID is found
     */
    public UserResponse me(Long id) {
        return repo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new UserNotFoundException(String.valueOf(id)));
    }

    private UserResponse toDto(User user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getRoles()
        );
    }
}
