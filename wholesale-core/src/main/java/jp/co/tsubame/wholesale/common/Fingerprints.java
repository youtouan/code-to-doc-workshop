package jp.co.tsubame.wholesale.common;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Fingerprints {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Fingerprints() {
    }

    public static String of(String... fields) {
        MessageDigest digest = sha256();
        for (String field : fields) {
            String value = field == null ? "" : field;
            byte[] bytes = value.getBytes(UTF8);
            digest.update(Integer.toString(bytes.length).getBytes(UTF8));
            digest.update((byte) ':');
            digest.update(bytes);
        }
        return hex(digest.digest());
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("The runtime must support SHA-256", ex);
        }
    }

    public static String hex(byte[] bytes) {
        char[] text = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            text[i * 2] = HEX[(bytes[i] & 0xff) >>> 4];
            text[i * 2 + 1] = HEX[bytes[i] & 0x0f];
        }
        return new String(text);
    }

    public static byte[] unhex(String value) {
        if (value == null || value.length() % 2 != 0 || !value.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException("Invalid hexadecimal value");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
