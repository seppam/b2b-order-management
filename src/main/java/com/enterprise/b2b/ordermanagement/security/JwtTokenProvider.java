package com.enterprise.b2b.ordermanagement.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationInMs;

    private Key key;

    @PostConstruct
    protected void init() {
        // Initialize the HMAC key using the Base64 encoded secret key
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT token for the authenticated user.
     *
     * @param authentication The Spring Security Authentication object
     * @return Generated JWT string
     */
    public String generateToken(Authentication authentication) {
        // Extract principal username/details from authentication
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        // Put custom claims here if needed (e.g., roles, userId)
        Map<String, Object> claims = new HashMap<>();
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority()).findFirst().orElse("ROLE_CLIENT");
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract the username (subject) from a given JWT.
     *
     * @param token JWT token string
     * @return Subject string (username)
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    /**
     * Extract the role claim (e.g. ROLE_CLIENT) from a given JWT.
     */
    public String getRoleFromToken(String token) {
        Object role = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().get("role");
        return role != null ? role.toString() : "ROLE_CLIENT";
    }

    /**
     * Validate the structure, signature, and expiration of the JWT.
     *
     * @param authToken JWT token string
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            // Log security / signature exceptions
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Log token expiration
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            // Log unsupported token
        } catch (IllegalArgumentException e) {
            // Log empty claims string
        }
        return false;
    }
}
