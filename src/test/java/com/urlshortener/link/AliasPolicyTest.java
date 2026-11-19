package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AliasPolicyTest {

    private final AliasPolicy policy = new AliasPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"sale", "Spring_Sale-2026", "a1b2c3d4"})
    void acceptsValidAliases(String alias) {
        assertThatCode(() -> policy.validate(alias)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "has space", "slash/es", "dot.ted", "ümlaut"})
    void rejectsMalformedAliases(String alias) {
        assertThatThrownBy(() -> policy.validate(alias)).isInstanceOf(InvalidAliasException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "API", "actuator", "swagger-ui", "health"})
    void rejectsReservedPaths(String alias) {
        assertThatThrownBy(() -> policy.validate(alias))
                .isInstanceOf(InvalidAliasException.class);
    }
}
