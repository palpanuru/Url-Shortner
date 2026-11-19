package com.urlshortener.support;

/** API keys configured (as SHA-256 hashes) in src/test/resources/application-test.yml. */
public final class TestKeys {

    public static final String HEADER = "X-API-Key";
    public static final String ALPHA = "test-key-alpha";
    public static final String BETA = "test-key-beta";

    private TestKeys() {
    }
}
