package com.urlshortener.link;

import static com.urlshortener.security.ApiKeyAuthFilter.CLIENT_ID_ATTRIBUTE;

import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.LinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Links", description = "Create, inspect and delete short links (owner only)")
@SecurityRequirement(name = "apiKey")
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final ShortLinkService service;

    public LinkController(ShortLinkService service) {
        this.service = service;
    }

    @Operation(summary = "Create a short link (random code or custom alias)")
    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request,
            @RequestAttribute(CLIENT_ID_ATTRIBUTE) String clientId) {
        LinkResponse created = service.create(request, clientId);
        return ResponseEntity.created(URI.create("/api/v1/links/" + created.code())).body(created);
    }

    @Operation(summary = "Get link metadata")
    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code,
            @RequestAttribute(CLIENT_ID_ATTRIBUTE) String clientId) {
        return service.get(code, clientId);
    }

    @Operation(summary = "Delete a link")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code,
            @RequestAttribute(CLIENT_ID_ATTRIBUTE) String clientId) {
        service.delete(code, clientId);
        return ResponseEntity.noContent().build();
    }
}
