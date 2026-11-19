package com.urlshortener.link;

import com.urlshortener.web.ApiException;
import org.springframework.http.HttpStatus;

/** 410 Gone: the link existed but has expired. Distinct from 404 so clients can tell why. */
public class LinkExpiredException extends ApiException {

    public LinkExpiredException(String code) {
        super(HttpStatus.GONE, "link-expired", "Link '" + code + "' has expired");
    }
}
