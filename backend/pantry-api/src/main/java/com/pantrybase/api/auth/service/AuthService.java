package com.pantrybase.api.auth.service;

import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.dto.LoginRequest;
import com.pantrybase.api.auth.dto.RegisterRequest;
import com.pantrybase.api.common.exception.*;
import com.pantrybase.api.common.security.JwtService;
import com.pantrybase.api.user.domain.Role;
import com.pantrybase.api.user.domain.User;
import com.pantrybase.api.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final UserRepository userRepo;
    private final TokenService tokenService;

    public AuthService(PasswordEncoder encoder,
                       JwtService jwtService,
                       AuthenticationManager authManager,
                       UserRepository userRepo,
                       TokenService tokenService) {
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.authManager = authManager;
        this.userRepo = userRepo;
        this.tokenService = tokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepo.existsByUsername(request.username()))
            throw new UsernameAlreadyExistsException(request.username());

        if (userRepo.existsByEmail(request.email()))
            throw new EmailAlreadyExistsException(request.email());

        User user = new User(
                request.username(),
                request.email(),
                encoder.encode(request.password())
        );
        user.setRoles(Set.of(Role.USER));
        user.setFirstName(request.firstName() != null ? request.firstName() : null);
        user.setLastName(request.lastName() != null ? request.lastName() : null);

        userRepo.save(user);
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.loginMethod(), request.password()));
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException();
        }
        User user = userRepo.findByUsernameOrEmail(request.loginMethod(), request.loginMethod())
                .orElseThrow(() -> new UserNotFoundException(request.loginMethod()));
        return issueTokens(user);
    }

    public AuthResponse refreshTokens(String rawToken) {
        Claims claims;
        try {
            claims = jwtService.parseToken(rawToken);
        } catch (JwtException e) {
            throw new InvalidRefreshTokenException();
        }

        if (!JwtService.TYPE_REFRESH.equals(claims.get("type", String.class)))
            throw new InvalidRefreshTokenException();

        if (!tokenService.isRefreshTokenValid(rawToken))
            throw new InvalidRefreshTokenException();

        User user = userRepo.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(InvalidRefreshTokenException::new);

        tokenService.revokeRefreshToken(rawToken);

        return issueTokens(user);
    }

    public void logout(String rawToken) {
        tokenService.revokeRefreshToken(rawToken);
    }

    public User linkOrCreateOAuthUser(Map<String, Object> attributes) {
        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is missing in OAuth2 attributes");

        Optional<User> userByGoogleId = userRepo.findByGoogleId(googleId);
        if (userByGoogleId.isPresent()) return userByGoogleId.get();

        Optional<User> userByEmail = userRepo.findByUsernameOrEmail(null, email);
        if (userByEmail.isPresent()) {
            User existingUser = userByEmail.get();
            existingUser.setUsername(email.split("@")[0]);
            existingUser.setGoogleId(googleId);
            userRepo.save(existingUser);
            return existingUser;
        }

        User newUser = new User();
        newUser.setUsername(email.split("@")[0]);
        newUser.setEmail(email);
        newUser.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        newUser.setFirstName((String) attributes.get("given_name"));
        newUser.setLastName((String) attributes.get("family_name"));
        newUser.setEnabled(true);
        newUser.setRoles(Set.of(Role.USER));
        newUser.setGoogleId(googleId);
        userRepo.save(newUser);
        return newUser;
    }


    public AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        tokenService.storeRefreshToken(user, refreshToken,
                Instant.now().plusSeconds(jwtService.getRefreshTtl().getSeconds()));

        return new AuthResponse(accessToken, refreshToken,
                "Bearer", jwtService.getAccessTtl().getSeconds());
    }
}
