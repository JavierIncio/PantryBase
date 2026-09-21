package com.pantrybase.api.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * A filter that intercepts incoming HTTP requests to authenticate users based on JWT tokens.
 *
 * <p>This filter checks for the presence of a Bearer token in the Authorization header, validates it,
 * and sets the authentication context if the token is valid.</p>
 *
 * <p>It extends {@link OncePerRequestFilter} to ensure that the filter is executed only once per request.</p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Filters incoming HTTP requests to authenticate users based on JWT tokens.
     *
     * <ol>
     *   <li>Checks for the presence of a Bearer token in the Authorization header</li>
     *   <li>Validates the token</li>
     *   <li>Creates an authentication containing the user ID and granted authorities</li>
     *   <li>Stores the authentication in the security context for the current request</li>
     * </ol>
     *
     * <p>Storing the authentication in the security context allows Spring Security
     * to recognize the request as authenticated and makes the authenticated principal
     * available through mechanisms such as {@code @AuthenticationPrincipal}.</p>
     *
     * <p>If the token is invalid, the security context is cleared and the request
     * continues through the filter chain without authentication.</p>
     *
     * @param request     the HttpServletRequest object
     * @param response    the HttpServletResponse object
     * @param filterChain the FilterChain object to pass the request and response to the next filter
     * @throws ServletException if an error occurs during filtering
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                Claims claims = jwtService.parseToken(token);

                if (JwtService.TYPE_ACCESS.equals(claims.get("type", String.class))) {

                    Object rolesClaim = claims.get("roles");

                    List<SimpleGrantedAuthority> authorities =
                            rolesClaim instanceof List<?> roles
                                    ? roles.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                    .toList()
                                    : List.of();

                    Long userId = Long.valueOf(claims.getSubject());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
