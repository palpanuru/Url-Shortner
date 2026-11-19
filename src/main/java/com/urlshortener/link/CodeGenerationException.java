package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

/** Raised when repeated random-code collisions suggest the code space is saturated. */
public class CodeGenerationException extends ApiException {

    public CodeGenerationException(int attempts) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "code-generation-failed",
                "Could not allocate a unique code after " + attempts + " attempts");
    }
}
