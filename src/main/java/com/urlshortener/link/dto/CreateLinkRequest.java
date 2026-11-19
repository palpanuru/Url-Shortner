package com.urlshortener.link.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @Schema(example = "https://www.example.com/some/long/path?with=query")
        @NotBlank @Size(max = 2048) String url,
        @Schema(description = "Optional custom alias (4-32 chars: letters, digits, '-', '_')",
                example = "spring-sale")
        @Size(max = 32) String alias) {
}
