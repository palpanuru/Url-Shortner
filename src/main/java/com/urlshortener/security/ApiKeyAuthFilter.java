package com.urlshortener.security;

import com.urlshortener.web.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates management API calls ({@code /api/**}) with an {@code X-API-Key} header and
 * exposes the resolved client id as a request attribute. The public redirect is unauthenticated.
 *
 * <p>Prototype scope: in production this would be Spring Security with OAuth2 client
 * credentials or mTLS at the gateway; the ownership model (client id per link) carries over.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String CLIENT_ID_ATTRIBUTE = "urlshortener.clientId";

    private final List<SecurityProperties.ApiClient> clients;
    private final ProblemWriter problems;

    public ApiKeyAuthFilter(SecurityProperties properties, ProblemWriter problems) {
        this.clients = properties.apiKeys();
        this.problems = problems;
    }

    /**
     * Matches on the same normalised path Spring MVC routes on (URL-decoded, ";params" removed).
     * Checking the raw request URI let "/api;x/v1/links" and "/%61pi/v1/links" skip this filter
     * while still reaching the controller - found in review and covered by a regression test.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RequestPaths.lookupPath(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        String clientId = key == null || key.isBlank() ? null : authenticate(key);
        if (clientId == null) {
            response.setHeader("WWW-Authenticate", "ApiKey header=\"" + HEADER + "\"");
            problems.write(response, HttpStatus.UNAUTHORIZED, "unauthorized",
                    "A valid " + HEADER + " header is required");
            return;
        }
        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientId);
        chain.doFilter(request, response);
    }

    /** Constant-time comparison of digests; checks every client to avoid timing differences. */
    private String authenticate(String presentedKey) {
        byte[] presented = sha256(presentedKey);
        String match = null;
        for (SecurityProperties.ApiClient client : clients) {
            if (MessageDigest.isEqual(presented, HexFormat.of().parseHex(client.sha256()))) {
                match = client.clientId();
            }
        }
        return match;
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
