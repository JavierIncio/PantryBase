package com.pantrybase.api.user.repository;

import com.pantrybase.api.user.domain.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores the optional 1:1 preferences row. The entity uses the user id
 * as its primary key (@MapsId), so findById() is already the lookup by user.
 */
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
}
