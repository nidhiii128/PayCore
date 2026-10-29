package io.paycore.common;

import java.util.Currency;
import java.util.Locale;

public final class Currencies {

    private Currencies() {
    }

    /** Returns the upper-cased ISO 4217 code, or throws if it is not a real currency. */
    public static String normalize(String code) {
        if (code == null || code.length() != 3) {
            throw unsupported(code);
        }
        String upper = code.toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(upper).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw unsupported(code);
        }
    }

    private static ApiException unsupported(String code) {
        return new ApiException(ErrorCode.UNSUPPORTED_CURRENCY, "Unsupported currency: " + code);
    }
}
