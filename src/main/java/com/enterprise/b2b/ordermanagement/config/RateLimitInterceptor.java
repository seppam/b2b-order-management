package com.enterprise.b2b.ordermanagement.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${app.rate-limit.capacity:100}")
    private int capacity;

    @Value("${app.rate-limit.refill-tokens:100}")
    private int refillTokens;

    @Value("${app.rate-limit.duration-seconds:3600}")
    private int durationSeconds;

    // Concurrent map to hold Buckets per client key (IP-address or username)
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        // Defines a rate limit with a maximum capacity, refilling tokens at specified intervals
        Refill refill = Refill.intervally(refillTokens, Duration.ofSeconds(durationSeconds));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket getBucket(String key) {
        return cache.computeIfAbsent(key, k -> createNewBucket());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Resolve target key: using Remote Address for IP rate limiting.
        // In a production reverse proxy environment (AWS ALB, Cloudflare), retrieve via standard 'X-Forwarded-For' header
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        Bucket bucket = getBucket(ipAddress);

        if (bucket.tryConsume(1)) {
            // Token successfully consumed, allow request to proceed
            return true;
        } else {
            // Rate limit exceeded
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"API rate limit exceeded. Please try again later.\"}");
            return false;
        }
    }
}
