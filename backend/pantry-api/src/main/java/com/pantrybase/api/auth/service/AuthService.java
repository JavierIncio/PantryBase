package com.pantrybase.api.auth.service;

import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.dto.ChangePasswordRequest;
import com.pantrybase.api.auth.dto.LoginRequest;
import com.pantrybase.api.auth.dto.RegisterRequest;
import com.pantrybase.api.auth.repository.RefreshTokenRepository;
import com.pantrybase.api.common.exception.CurrentPasswordMismatchException;
import com.pantrybase.api.common.exception.EmailAlreadyExistsException;
import com.pantrybase.api.common.exception.InvalidCredentialsException;
import com.pantrybase.api.common.exception.InvalidRefreshTokenException;
import com.pantrybase.api.common.exception.PasswordNotSetException;
import com.pantrybase.api.common.exception.UserNotFoundException;
import com.pantrybase.api.common.exception.UsernameAlreadyExistsException;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service class for handling authentication-related operations.
 */
@Service
public class AuthService {

    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final UserRepository userRepo;
    private final RefreshTokenRepository rtRepo;
    private final TokenService tokenService;

    public AuthService(PasswordEncoder encoder,
                       JwtService jwtService,
                       AuthenticationManager authManager,
                       UserRepository userRepo,
                       RefreshTokenRepository rtRepo,
                       TokenService tokenService) {
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.authManager = authManager;
        this.userRepo = userRepo;
        this.rtRepo = rtRepo;
        this.tokenService = tokenService;
    }

    /**
     * Registers a new user based on the provided registration request.
     *
     * @param request the registration request containing user details
     * @return an AuthResponse containing access and refresh tokens
     * @throws UsernameAlreadyExistsException if the username is already taken
     * @throws EmailAlreadyExistsException    if the email is already registered
     */
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

    /**
     * Authenticates a user based on the provided login request.
     *
     * @param request the login request containing user credentials
     * @return an AuthResponse containing access and refresh tokens
     * @throws InvalidCredentialsException if the credentials are invalid
     */
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

    /**
     * Refreshes the access and refresh tokens for a user.
     *
     * <ol>
     *   <li>Parse the raw refresh token to extract its claims.</li>
     *   <li>Validate the token type and its validity.</li>
     *   <li>Retrieve the user associated with the token.</li>
     *   <li>Revoke the old refresh token.</li>
     *   <li>Issue new access and refresh tokens for the user.</li>
     * </ol>
     *
     * @param rawToken the raw refresh token
     * @return an AuthResponse containing the new access and refresh tokens
     * @throws InvalidRefreshTokenException if the refresh token is invalid
     */
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

    /**
     * Logs out a user by revoking their refresh token.
     *
     * @param rawToken the raw refresh token
     */
    public void logout(String rawToken) {
        tokenService.revokeRefreshToken(rawToken);
    }

    /**
     * Links an existing user account with an OAuth2 provider or creates a new user if none exists.
     *
     * @param attributes the attributes received from the OAuth2 provider
     * @return the linked or newly created User
     * @throws IllegalArgumentException if the email is missing in the OAuth2 attributes
     */
    public User linkOrCreateOAuthUser(Map<String, Object> attributes) {
        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is missing in OAuth2 attributes");
        }

        Optional<User> userByGoogleId = userRepo.findByGoogleId(googleId);
        if (userByGoogleId.isPresent()) {
            return userByGoogleId.get();
        }

        Optional<User> userByEmail = userRepo.findByUsernameOrEmail(null, email);
        if (userByEmail.isPresent()) {
            User existingUser = userByEmail.get();
            existingUser.setGoogleId(googleId);
            userRepo.save(existingUser);
            return existingUser;
        }

        User newUser = new User();
        newUser.setUsername(deriveUniqueUsername(email));
        newUser.setEmail(email);
        newUser.setFirstName((String) attributes.get("given_name"));
        newUser.setLastName((String) attributes.get("family_name"));
        newUser.setEnabled(true);
        newUser.setRoles(Set.of(Role.USER));
        newUser.setGoogleId(googleId);

        return userRepo.save(newUser);
    }

    /**
     * Issues access and refresh tokens for the given user.
     *
     * @param user the user for whom to issue tokens
     * @return an AuthResponse containing the issued tokens
     */
    public AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        tokenService.storeRefreshToken(user, refreshToken,
                Instant.now().plusSeconds(jwtService.getRefreshTtl().getSeconds()));

        return new AuthResponse(accessToken, refreshToken,
                "Bearer", jwtService.getAccessTtl().getSeconds());
    }

    /**
     * Changes the password of the specified user.
     *
     * <p>If the user already has a password, the current password must be
     * provided and must match the stored password hash. If the user does not
     * have a password, the current password must not be provided.</p>
     *
     * <p>After successfully changing the password, revokes all existing refresh tokens
     * for the user.</p>
     *
     * <p>The operation is transactional to ensure that the password update and
     * refresh-token deletion are performed atomically.</p>
     *
     * @param userId  the ID of the user whose password is being changed
     * @param request the request containing the current password and the new password
     * @throws UserNotFoundException            if no user exists with the specified ID
     * @throws PasswordNotSetException          if the user has no password but a current password was provided
     * @throws CurrentPasswordMismatchException if the current password is missing or does not match the stored password
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String passwordHash = user.getPasswordHash();
        String currentPassword = request.currentPassword();

        if (passwordHash == null) {
            if (currentPassword != null && !currentPassword.isBlank())
                throw new PasswordNotSetException();
        } else {
            if (currentPassword == null
                    || currentPassword.isBlank()
                    || !encoder.matches(currentPassword, passwordHash))
                throw new CurrentPasswordMismatchException();
        }

        user.setPasswordHash(encoder.encode(request.newPassword()));
        rtRepo.deleteByUserId(user.getId());
    }

    private String deriveUniqueUsername(String email) {
        String baseUsername = email.split("@")[0];
        String username = baseUsername;
        int counter = 1;

        while (userRepo.existsByUsername(username)) {
            username = baseUsername + counter++;
        }

        return username;
    }
}
