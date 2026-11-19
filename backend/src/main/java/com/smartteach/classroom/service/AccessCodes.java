package com.smartteach.classroom.service;

import com.smartteach.common.security.Hashing;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Student access codes: 10 random characters from a 31-character alphabet (about 49 bits). Letters that
 * look alike on a projector (0/O, 1/I/L) are left out. Only the SHA-256 hash is stored: a plain
 * hash (not BCrypt) is what lets an assignment find the student by code with one indexed lookup,
 * and the code is random rather than chosen by a person, so it is not guessable from a word list.
 */
public final class AccessCodes {

    static final int LENGTH = 10;
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccessCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Students may type the code in lowercase or with spaces/dashes. */
    public static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static String hash(String code) {
        return Hashing.sha256Hex(normalize(code));
    }

    public static String hint(String code) {
        return code.substring(code.length() - 4);
    }
}
