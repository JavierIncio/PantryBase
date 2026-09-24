package com.pantrybase.api.common.config;

import com.pantrybase.api.common.ratelimit.RateLimitFilter;
import com.pantrybase.api.common.security.JwtAuthFilter;
import com.pantrybase.api.common.security.OAuth2AuthenticationFailureHandler;
import com.pantrybase.api.common.security.OAuth2AuthenticationSuccessHandler;
import com.pantrybase.api.common.security.RestAuthenticationEntryPoint;
import com.pantrybase.api.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration class for the application.
 */
@Configuration
public class SecurityConfig {

    /**
     * Bean for password encoding using BCrypt.
     *
     * @return a PasswordEncoder instance
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the security filter chain for the application.
     *
     * <ol>
     *     <li>Disables CSRF protection.</li>
     *     <li>Sets session management to create sessions only if required.</li>
     *     <li>Configures exception handling with a custom authentication entry point.</li>
     *     <li>Configures OAuth2 login with custom success and failure handlers.</li>
     *     <li>Configures authorization rules for different endpoints.</li>
     *     <li>Adds custom filters for JWT authentication and rate limiting.</li>
     * </ol>
     *
     * @param http          the HttpSecurity object to configure
     * @param entryPoint    the authentication entry point for handling unauthorized access
     * @param successHandler the handler for successful OAuth2 authentication
     * @param failureHandler the handler for failed OAuth2 authentication
     * @param jwtAuthFilter  the JWT authentication filter
     * @param rateLimitFilter the rate limiting filter
     * @return a configured SecurityFilterChain instance
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    RestAuthenticationEntryPoint entryPoint,
                                    OAuth2AuthenticationSuccessHandler successHandler,
                                    OAuth2AuthenticationFailureHandler failureHandler,
                                    JwtAuthFilter jwtAuthFilter,
                                    RateLimitFilter rateLimitFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout"
                        ).permitAll()
                        .requestMatchers(
                                "/oauth2/authorization/**", "/login/oauth2/code/**"
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/health", "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) settings for the application.
     *
     * @return a CorsConfigurationSource instance with the configured CORS settings
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration conf = new CorsConfiguration();
        conf.setAllowedOrigins(List.of(
                "http://localhost:4200"
        ));
        conf.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH",
                "DELETE", "OPTIONS"
        ));
        conf.setAllowedHeaders(List.of("*"));
        conf.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", conf);

        return source;
    }

    /**
     * Creates a UserDetailsService bean.
     *
     * @param repo the UserRepository to use for fetching user details
     * @return a UserDetailsService instance
     */
    @Bean
    UserDetailsService userDetailsService(UserRepository repo) {
        return identifier -> repo.findByUsernameOrEmail(identifier, identifier)
                .map(user -> User
                        .withUsername(user.getUsername())
                        .password(user.getPasswordHash() == null ? "" : user.getPasswordHash())
                        .disabled(!user.isEnabled())
                        .authorities(user.getRoles().stream()
                                .map(role -> "ROLE_" + role.name())
                                .toArray(String[]::new))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(identifier));
    }

    /**
     * Creates a DaoAuthenticationProvider bean.
     *
     * @param userDetailsService the UserDetailsService to use for authentication
     * @param passwordEncoder    the PasswordEncoder to use for password encoding
     * @return a DaoAuthenticationProvider instance
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Creates an AuthenticationManager bean.
     *
     * @param conf the AuthenticationConfiguration to use for obtaining the AuthenticationManager
     * @return an AuthenticationManager instance
     * @throws Exception if an error occurs while obtaining the AuthenticationManager
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration conf) throws Exception {
        return conf.getAuthenticationManager();
    }
}
