package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidExpiryException extends ApiException {

    public InvalidExpiryException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-expiry", reason);
    }
}
