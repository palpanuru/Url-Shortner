package com.urlshortener.analytics;

import static com.urlshortener.security.ApiKeyAuthFilter.CLIENT_ID_ATTRIBUTE;

import com.urlshortener.analytics.dto.LinkStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics")
@SecurityRequirement(name = "apiKey")
@RestController
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Click statistics for a link over the last N days (UTC)")
    @GetMapping("/api/v1/links/{code}/stats")
    public LinkStats stats(@PathVariable String code,
            @RequestParam(defaultValue = "7") int days,
            @RequestAttribute(CLIENT_ID_ATTRIBUTE) String clientId) {
        return service.stats(code, days, clientId);
    }
}
