package com.urlshortener.link;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Rules for caller-chosen aliases (vanity codes). */
@Component
public class AliasPolicy {

    static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{4,32}$");

    /** Paths owned by the application itself; an alias must never shadow them. */
    static final Set<String> RESERVED = Set.of(
            "api", "actuator", "health", "swagger-ui", "swagger-ui.html", "v3", "docs",
            "admin", "login", "logout", "static", "favicon.ico", "error");

    public void validate(String alias) {
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidAliasException(
                    "Alias must be 4-32 characters of letters, digits, '-' or '_'");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidAliasException("Alias '" + alias + "' is reserved");
        }
    }
}
