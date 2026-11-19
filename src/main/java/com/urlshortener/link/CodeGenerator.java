package com.urlshortener.link;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates random Base62 short codes.
 *
 * <p>Random (rather than sequential, ID-encoded) codes are a deliberate choice: sequential codes
 * make every link in the system enumerable, which leaks private links. At 7 characters the space
 * is 62^7 (about 3.5 trillion), so collisions are rare and are handled by a retry on the database
 * unique constraint.
 */
@Component
public class CodeGenerator {

    static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
