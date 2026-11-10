package jp.co.tsubame.wholesale.common;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private static final int ITERATIONS = 120000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[24];
        RANDOM.nextBytes(salt);
        return "pbkdf2-sha1$" + ITERATIONS + "$" + Fingerprints.hex(salt) + "$"
                + Fingerprints.hex(derive(password, salt, ITERATIONS));
    }

    public static boolean matches(char[] password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        String[] fields = encoded.split("\\$");
        if (fields.length != 4 || !"pbkdf2-sha1".equals(fields[0])) {
            throw new IllegalStateException("Unsupported stored password encoding");
        }
        int rounds = Integer.parseInt(fields[1]);
        if (rounds < 100000 || rounds > 1000000) {
            throw new IllegalStateException("Stored password work factor is outside policy");
        }
        byte[] calculated = derive(password, Fingerprints.unhex(fields[2]), rounds);
        return MessageDigest.isEqual(Fingerprints.unhex(fields[3]), calculated);
    }

    public static void validateNew(char[] password) {
        Checks.state(password != null && password.length >= 12 && password.length <= 128,
                "password.length", "パスワードは12文字以上128文字以下で入力してください。");
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        for (char c : password) {
            lower |= Character.isLowerCase(c);
            upper |= Character.isUpperCase(c);
            digit |= Character.isDigit(c);
        }
        Checks.state(lower && upper && digit, "password.complexity",
                "パスワードには大文字・小文字・数字を含めてください。");
    }

    private static byte[] derive(char[] password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("The runtime must support PBKDF2WithHmacSHA1", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
