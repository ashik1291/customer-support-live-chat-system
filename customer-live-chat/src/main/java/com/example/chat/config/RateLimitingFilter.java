package com.example.chat.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ChatSecurityProperties securityProperties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(ChatSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!securityProperties.isRateLimitingEnabled() || isAsyncDispatch(request) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(resolveKey(request), this::newBucket);
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRateLimitResponse(response);
    }

    private Bucket newBucket(String key) {
        ChatSecurityProperties.RateLimit limitConfig = securityProperties.getRateLimit();
        Duration refillPeriod = limitConfig.getRefillPeriod();
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            refillPeriod = Duration.ofSeconds(60);
        }

        long capacity = Math.max(limitConfig.getCapacity(), 1);
        long refillTokens = Math.max(limitConfig.getRefillTokens(), 1);

        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, refillPeriod));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        long retryAfterSeconds = Math.max(securityProperties.getRateLimit().getRefillPeriod().toSeconds(), 1);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter()
                .write("{\"error\":\"too_many_requests\",\"message\":\"Request rate exceeded. Please retry later.\"}");
    }

    private String resolveKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = forwardedFor != null && !forwardedFor.isBlank()
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        String path = request.getRequestURI();
        return clientIp + ":" + path;
    }
}

