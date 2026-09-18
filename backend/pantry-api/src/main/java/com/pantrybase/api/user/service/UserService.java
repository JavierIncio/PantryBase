package com.pantrybase.api.user.service;

import com.pantrybase.api.common.exception.UserNotFoundException;
import com.pantrybase.api.user.domain.User;
import com.pantrybase.api.user.dto.UserResponse;
import com.pantrybase.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;


@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    public UserResponse me(String identifier) {
        return repo.findByUsernameOrEmail(identifier, identifier)
                .map(this::toDto)
                .orElseThrow(() -> new UserNotFoundException(identifier));
    }

    private UserResponse toDto(User user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getRoles()
        );
    }
}
