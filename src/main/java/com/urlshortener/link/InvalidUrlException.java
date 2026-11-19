package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidUrlException extends ApiException {

    public InvalidUrlException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-url", reason);
    }
}
