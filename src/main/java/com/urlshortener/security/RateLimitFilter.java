package com.urlshortener.security;

import com.urlshortener.web.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies two limits: link creation per API client (abuse: mass-generating spam links) and
 * redirects per client IP (abuse: scraping or enumerating codes). Runs after authentication so
 * creation is keyed by the authenticated client, not a spoofable header.
 *
 * <p>Client IP is the socket peer address. Behind a load balancer, enable
 * {@code server.forward-headers-strategy=native} with a trusted proxy list; never trust
 * X-Forwarded-For from arbitrary callers.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern REDIRECT_PATH = Pattern.compile("^/[A-Za-z0-9_-]{1,32}$");

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final ProblemWriter problems;

    public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties,
            ProblemWriter problems) {
        this.limiter = limiter;
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long retryAfter = 0;
        String path = RequestPaths.lookupPath(request);
        if ("POST".equals(request.getMethod()) && "/api/v1/links".equals(path)) {
            Object clientId = request.getAttribute(ApiKeyAuthFilter.CLIENT_ID_ATTRIBUTE);
            retryAfter = limiter.tryAcquire("create:" + clientId, properties.create());
        } else if ("GET".equals(request.getMethod()) && REDIRECT_PATH.matcher(path).matches()) {
            retryAfter = limiter.tryAcquire("redirect:" + request.getRemoteAddr(),
                    properties.redirect());
        }
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "rate-limited",
                    "Too many requests; retry after " + retryAfter + "s");
            return;
        }
        chain.doFilter(request, response);
    }
}
