package com.pantrybase.api.user.service;

import com.pantrybase.api.common.exception.UnknownAllergenCodeException;
import com.pantrybase.api.common.exception.UserNotFoundException;
import com.pantrybase.api.common.exception.UsernameAlreadyExistsException;
import com.pantrybase.api.user.domain.Allergen;
import com.pantrybase.api.user.domain.User;
import com.pantrybase.api.user.domain.UserPreferences;
import com.pantrybase.api.user.dto.AllergenResponse;
import com.pantrybase.api.user.dto.AllergyExclusionsResponse;
import com.pantrybase.api.user.dto.ReplaceAllergyExclusionsRequest;
import com.pantrybase.api.user.dto.UpdateProfileRequest;
import com.pantrybase.api.user.dto.UserPreferencesRequest;
import com.pantrybase.api.user.dto.UserPreferencesResponse;
import com.pantrybase.api.user.dto.UserResponse;
import com.pantrybase.api.user.repository.AllergenRepository;
import com.pantrybase.api.user.repository.UserPreferencesRepository;
import com.pantrybase.api.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for handling user-related operations.
 */
@Service
public class UserService {

    private final UserRepository userRepo;
    private final AllergenRepository allergenRepo;
    private final UserPreferencesRepository preferencesRepo;

    public UserService(UserRepository userRepo,
                       AllergenRepository allergenRepo,
                       UserPreferencesRepository preferencesRepo) {
        this.userRepo = userRepo;
        this.allergenRepo = allergenRepo;
        this.preferencesRepo = preferencesRepo;
    }

    /**
     * Retrieves the user information for the specified user ID.
     *
     * @param id the ID of the user to retrieve
     * @return a UserResponse containing the user's information
     * @throws UserNotFoundException if no user with the specified ID is found
     */
    public UserResponse me(Long id) {
        return toUserResponse(findUser(id));
    }

    /**
     * Updates the user's profile information based on the provided request.
     *
     * <p>If the username is provided and is already taken by another user, a
     * {@link UsernameAlreadyExistsException} is thrown.</p>
     *
     * <p>First name and last name are updated to the provided values, or cleared
     * if null.</p>
     *
     * @param userId  the ID of the user whose profile is to be updated
     * @param request the request containing the new profile information
     * @return a UserResponse containing the updated user's information
     * @throws UsernameAlreadyExistsException if the requested username is already taken by another user
     */
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        if (request.username() != null) {
            if (userRepo.existsByUsernameAndIdNot(request.username(), user.getId())) {
                throw new UsernameAlreadyExistsException(request.username());
            }
            user.setUsername(request.username());
        }
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        userRepo.save(user);
        return toUserResponse(user);
    }

    /**
     * Retrieves the user preferences for the specified user ID.
     * If no preferences are found, returns default preferences without persisting.
     *
     * @param userId the ID of the user whose preferences are to be retrieved
     * @return a UserPreferencesResponse containing the user's preferences
     */
    public UserPreferencesResponse getPreferences(Long userId) {
        UserPreferences prefs = preferencesRepo.findById(userId)
                .orElseGet(UserPreferences::new);
        return toPreferencesResponse(prefs);
    }

    /**
     * Saves or updates the user preferences for the specified user ID.
     *
     * <p>If no preferences row exists, a new one is created and associated with
     * the user. The preferences row is created lazily rather than during user
     * registration.</p>
     *
     * @param userId  the ID of the user whose preferences are to be saved
     * @param request the requested preference values
     * @return the saved user preferences
     * @throws UserNotFoundException if no user exists with the specified ID
     */
    @Transactional
    public UserPreferencesResponse savePreferences(Long userId, UserPreferencesRequest request) {
        User user = findUser(userId);
        UserPreferences prefs = preferencesRepo.findById(userId)
                .orElseGet(() -> {
                    UserPreferences newPrefs = new UserPreferences();
                    newPrefs.setUser(user);
                    newPrefs.setId(user.getId());
                    return newPrefs;
                });

        prefs.setFilterMode(request.filterMode());
        prefs.setCoverageThreshold(request.coverageThreshold());
        prefs.setDiet(request.diet());

        preferencesRepo.save(prefs);
        return toPreferencesResponse(prefs);
    }

    /**
     * Retrieves the allergy exclusions for the specified user ID.
     *
     * <p>Transactional to ensure that the lazy-loaded collection (allergyExclusions)
     * is properly initialized.</p>
     *
     * @param userId the ID of the user whose allergy exclusions are to be retrieved
     * @return an AllergyExclusionsResponse containing the user's allergy exclusions
     * @throws UserNotFoundException if no user with the specified ID is found
     */
    @Transactional
    public AllergyExclusionsResponse getExclusions(Long userId) {
        User user = findUser(userId);
        return toExclusionsResponse(user.getAllergyExclusions());
    }


    /**
     * Replaces all allergy exclusions configured for the user.
     *
     * <p>The operation is atomic: either all supplied allergen codes are valid
     * and the user's exclusions are completely replaced, or no changes are made.</p>
     *
     * <p>Unknown allergen codes result in an {@link UnknownAllergenCodeException}.
     * Duplicate codes are ignored because exclusions are represented as a set.</p>
     *
     * @param userId  the ID of the user whose allergy exclusions are to be replaced
     * @param request the ReplaceAllergyExclusionsRequest containing the new allergen codes
     * @return the user's updated allergy exclusions
     * @throws UnknownAllergenCodeException   if any of the provided allergen codes are unknown
     */
    @Transactional
    public AllergyExclusionsResponse replaceExclusions(Long userId, ReplaceAllergyExclusionsRequest request) {
        User user = findUser(userId);

        List<Allergen> requestedAllergens = allergenRepo.findByCodeIn(request.codes());

        Set<String> knownCodes = requestedAllergens.stream()
                .map(Allergen::getCode)
                .collect(Collectors.toSet());

        List<String> missingCodes = request.codes().stream()
                .distinct()
                .filter(code -> !knownCodes.contains(code))
                .toList();

        if (!missingCodes.isEmpty()) {
            throw new UnknownAllergenCodeException(missingCodes);
        }

        Set<Allergen> exclusions = new HashSet<>(requestedAllergens);
        user.setAllergyExclusions(exclusions);

        return toExclusionsResponse(exclusions);
    }

    /**
     * Returns the full allergen ontology sorted by name, so clients can
     * render checklists with a stable, deterministic order.
     */
    public List<AllergenResponse> getAllergenCatalog() {
        return allergenRepo.findAll(Sort.by("name")).stream()
                .map(this::toAllergenResponse)
                .toList();
    }

    private User findUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getRoles()
        );
    }

    private UserPreferencesResponse toPreferencesResponse(UserPreferences prefs) {
        return new UserPreferencesResponse(
                prefs.getFilterMode(), prefs.getCoverageThreshold(), prefs.getDiet()
        );
    }

    private AllergenResponse toAllergenResponse(Allergen allergen) {
        return new AllergenResponse(
                allergen.getId(), allergen.getCode(), allergen.getName()
        );
    }

    private AllergyExclusionsResponse toExclusionsResponse(Set<Allergen> allergens) {
        return new AllergyExclusionsResponse(allergens
                .stream()
                .map(this::toAllergenResponse)
                .collect(Collectors.toSet())
        );
    }
}
