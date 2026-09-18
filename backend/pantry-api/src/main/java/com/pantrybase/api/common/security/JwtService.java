package com.pantrybase.api.common.security;

import com.pantrybase.api.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-ttl}") Duration accessTtl,
                      @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TYPE_ACCESS, accessTtl);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TYPE_REFRESH, refreshTtl);
    }

    private String generateToken(User user, String type, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .claim("type", type)
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles())
                .claim("jti", UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }


}
