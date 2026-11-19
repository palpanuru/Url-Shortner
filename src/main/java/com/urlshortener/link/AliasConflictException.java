package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

public class AliasConflictException extends ApiException {

    public AliasConflictException(String alias) {
        super(HttpStatus.CONFLICT, "alias-taken", "Alias '" + alias + "' is already in use");
    }
}
