package com.pantrybase.api.common.ratelimit;

import com.pantrybase.api.common.dto.ErrorResponseFactory;
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

/**
 * RateLimitFilter is a Spring filter that enforces rate limiting on incoming HTTP requests.
 *
 * <p>The filter enforces two tiers of the same token bucket algorithm, split by the
 * identity available at this point of the security chain (it runs right after
 * {@code JwtAuthFilter}): an authenticated request is limited per user id, while a
 * request that carries no identity — login, register, refresh, password reset — is
 * limited per client IP, because without a user the IP is the only subject an attacker
 * cannot rotate for free. Sensitive credential endpoints can be tightened further via
 * {@code strict-paths} without touching code.</p>
 *
 * <p>The IP is taken from the socket address ({@code remoteAddr}) and never from
 * {@code X-Forwarded-For}: trusting a client-supplied header would let an attacker
 * spoof a different IP per request and rotate buckets at will. Revisit
 * {@link #clientIp(HttpServletRequest)} only when a trusted reverse proxy sits in
 * front of the application and its address is known.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMIT_BUCKET_KEY = "rate:{subject}:{method}:{path}";
    public static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";

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
     * Capacity and refill interval of a bucket: the policy decides how generous a
     * bucket is, the limiter only applies it.
     */
    private record BucketPolicy(long capacity, long refillIntervalMillis) {}

    /**
     * A resolved bucket: its Redis key and the policy that governs it.
     */
    private record ResolvedBucket(String key, BucketPolicy policy) {}

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

        ResolvedBucket bucket = resolveBucket(request);
        TokenBucketRateLimiter.RateLimitResult result = rateLimiter.consume(
                bucket.key(), bucket.policy().capacity(), bucket.policy().refillIntervalMillis());

        response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(bucket.policy().capacity()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(result.remainingTokens()));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            objectMapper.writeValue(response.getWriter(), ErrorResponseFactory
                    .of(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded.", request));
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
     * Resolves the bucket for the request: per user id when the request is authenticated,
     * per client IP otherwise, with the anonymous tier picking up any stricter override
     * configured for the path.
     *
     * @param request the HTTP request
     * @return the resolved bucket key and its policy
     */
    private ResolvedBucket resolveBucket(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long userId) {
            return new ResolvedBucket(
                    bucketKey("user:" + userId, request),
                    new BucketPolicy(properties.capacity(), properties.refillIntervalMillis()));
        }

        return new ResolvedBucket(
                bucketKey("ip:" + clientIp(request), request),
                anonymousPolicy(request));
    }

    /**
     * Picks the anonymous policy for the request path: the first matching
     * {@code strict-paths} override, or the anonymous defaults otherwise.
     *
     * @param request the HTTP request
     * @return the policy to apply to this anonymous bucket
     */
    private BucketPolicy anonymousPolicy(HttpServletRequest request) {
        RateLimitProperties.Anonymous anonymous = properties.anonymous();
        String path = request.getRequestURI();

        return anonymous.strictPaths().stream()
                .filter(strict -> strict.paths().stream()
                        .anyMatch(pattern -> pathMatcher.match(pattern, path)))
                .findFirst()
                .map(strict -> new BucketPolicy(strict.capacity(), strict.refillIntervalMillis()))
                .orElseGet(() -> new BucketPolicy(anonymous.capacity(), anonymous.refillIntervalMillis()));
    }

    /**
     * Builds the Redis bucket key for a subject (user id or client IP) and request.
     *
     * @param subject the bucket subject, e.g. {@code user:42} or {@code ip:127.0.0.1}
     * @param request the HTTP request
     * @return the bucket key
     */
    private String bucketKey(String subject, HttpServletRequest request) {
        return RATE_LIMIT_BUCKET_KEY
                .replace("{subject}", subject)
                .replace("{method}", request.getMethod())
                .replace("{path}", request.getRequestURI());
    }

    /**
     * Returns the client address for the anonymous bucket, taken from the socket.
     *
     * <p>{@code X-Forwarded-For} is deliberately not consulted: no reverse proxy is
     * configured in front of the application, so the header is client-controlled and
     * trusting it would fragment the buckets across spoofed addresses. When a trusted
     * proxy is deployed, derive the address from its known address chain instead.</p>
     *
     * @param request the HTTP request
     * @return the remote address of the socket the request arrived on
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
