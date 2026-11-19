package com.urlshortener.link;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redirect endpoint. Uses 302 rather than 301: browsers cache 301s permanently, which
 * would hide repeat clicks from analytics and make deleted links keep working client-side.
 */
@Tag(name = "Redirect")
@RestController
public class RedirectController {

    private final ShortLinkService service;

    public RedirectController(ShortLinkService service) {
        this.service = service;
    }

    @Operation(summary = "Redirect to the target URL")
    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = service.resolveAndCount(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .header("Cache-Control", "private, max-age=0")
                .build();
    }
}
