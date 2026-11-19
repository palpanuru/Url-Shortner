package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

public class LinkNotFoundException extends ApiException {

    public LinkNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, "link-not-found", "No link exists for code '" + code + "'");
    }
}
