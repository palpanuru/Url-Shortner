package com.urlshortener.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/** Path normalisation shared by the servlet filters, aligned with Spring MVC's routing view. */
final class RequestPaths {

    private static final UrlPathHelper HELPER = new UrlPathHelper();

    static {
        HELPER.setRemoveSemicolonContent(true);
        HELPER.setUrlDecode(true);
    }

    private RequestPaths() {
    }

    static String lookupPath(HttpServletRequest request) {
        return HELPER.getLookupPathForRequest(request);
    }
}
