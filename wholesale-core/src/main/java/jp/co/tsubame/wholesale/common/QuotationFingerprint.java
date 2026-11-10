package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public final class QuotationFingerprint {
    private final StringBuilder content = new StringBuilder();
    public QuotationFingerprint add(Object value) {
        if (value == null) {
            content.append("-;");
            return this;
        }
        String text;
        if (value instanceof BigDecimal) { text = ((BigDecimal) value).stripTrailingZeros().toPlainString(); }
        else if (value instanceof Date) { text = Dates.format((Date) value); }
        else { text = value.toString(); }
        content.append(text.length()).append(':').append(text).append(';');
        return this;
    }
    public String finish() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                int unsigned = value & 255;
                if (unsigned < 16) { hex.append('0'); }
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", ex);
        }
    }
}
