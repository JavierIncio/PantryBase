package com.pantrybase.api.common.ratelimit;

import com.pantrybase.api.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * RateLimitFilter is a Spring filter that enforces rate limiting on incoming HTTP requests.
 *
 * <p>This filter uses a token bucket algorithm to determine if a request should be allowed or rejected based
 * on the user's or remote address's rate limit.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMIT_BUCKET_KEY = "rate:{user}:{method}:{path}";

    private final TokenBucketRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(TokenBucketRateLimiter rateLimiter,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Filters incoming HTTP requests to enforce rate limiting.
     *
     * <p>If the request path is excluded from rate limiting, it is passed through without checks.
     * Otherwise, the filter checks if the request is allowed based on the token bucket algorithm.</p>
     *
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if an error occurs during filtering
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = resolveBucketKey(request);
        TokenBucketRateLimiter.RateLimitResult result = rateLimiter.consume(bucketKey);

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));


        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                    Instant.now(), 429, "Too Many Requests",
                    "Rate limit exceeded.", request.getRequestURI()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Checks if the request path is excluded from rate limiting based on the configured excluded paths.
     *
     * @param request the HTTP request
     * @return true if the request path is excluded, false otherwise
     */
    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.excludedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Resolves the bucket key for rate limiting based on the authenticated user or the request's remote address.
     *
     * @param request the HTTP request
     * @return the resolved bucket key
     */
    private String resolveBucketKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long userId) {
            return RATE_LIMIT_BUCKET_KEY
                    .replace("{user}", userId.toString())
                    .replace("{method}", request.getMethod())
                    .replace("{path}", request.getRequestURI());
        }

        // Fallback to using the remote address if the user is not authenticated
        return RATE_LIMIT_BUCKET_KEY
                .replace("{user}", request.getRemoteAddr())
                .replace("{method}", request.getMethod())
                .replace("{path}", request.getRequestURI());
    }
}
