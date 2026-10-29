package io.paycore.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrenciesTest {

    @ParameterizedTest
    @ValueSource(strings = {"usd", "USD", "Inr", "eur"})
    void realCurrenciesAreNormalizedToUpperCase(String code) {
        assertThat(Currencies.normalize(code)).isEqualTo(code.toUpperCase());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"US", "USDT", "XYZ", "123"})
    void unknownCodesAreRejected(String code) {
        assertThatThrownBy(() -> Currencies.normalize(code)).isInstanceOf(ApiException.class);
    }
}
