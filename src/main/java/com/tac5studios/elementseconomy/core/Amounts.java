package com.tac5studios.elementseconomy.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/** Reads and writes amounts. Stored amounts are whole numbers in the smallest unit. */
public final class Amounts {

    /** Longest number a player can type (about a quintillion quintillion), suffix not counted. */
    private static final int MAX_DIGITS = 40;

    private Amounts() {}

    /** 125050 with 2 decimals -> "1,250.50". */
    public static String format(BigInteger amount, int decimals) {
        BigDecimal v = new BigDecimal(amount, decimals);
        return String.format(Locale.ROOT, "%,." + decimals + "f", v);
    }

    /**
     * Typed text -> smallest unit. Accepts "1250", "1,250", "12.5", "1.5k", "2m", "1b".
     * Empty when it isn't a number, has too many decimals, or is zero or less.
     */
    public static Optional<BigInteger> parse(String text, int decimals) {
        if (text == null) return Optional.empty();
        String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        if (s.isEmpty()) return Optional.empty();
        BigDecimal mult = BigDecimal.ONE;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            mult = switch (last) {
                case 'k' -> BigDecimal.valueOf(1_000L);
                case 'm' -> BigDecimal.valueOf(1_000_000L);
                default -> BigDecimal.valueOf(1_000_000_000L);
            };
            s = s.substring(0, s.length() - 1);
        }
        // Plain digits with one optional decimal point only. No signs, no exponents ("1e9999999" would
        // build a number with millions of digits), and no more digits than any balance could need.
        if (s.length() > MAX_DIGITS || !s.matches("\\d*\\.?\\d*") || !s.matches(".*\\d.*")) return Optional.empty();
        try {
            BigDecimal v = new BigDecimal(s).multiply(mult).movePointRight(decimals);
            if (v.signum() <= 0) return Optional.empty();
            return Optional.of(v.setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact());
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    /** Like parse, but zero is allowed (for /eco set). */
    public static Optional<BigInteger> parseOrZero(String text, int decimals) {
        if (text != null && text.trim().matches("0+(\\.0+)?")) return Optional.of(BigInteger.ZERO);
        return parse(text, decimals);
    }

    /** Clamp to long for item maths (coin counts never get near the limit). */
    public static long toLong(BigInteger v) {
        if (v.bitLength() > 62) return v.signum() < 0 ? Long.MIN_VALUE / 2 : Long.MAX_VALUE / 2;
        return v.longValue();
    }
}
