package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidAliasException extends ApiException {

    public InvalidAliasException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-alias", reason);
    }
}
