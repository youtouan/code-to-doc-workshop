package jp.co.tsubame.wholesale.common;

public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String field;

    public BusinessException(String code, String message) {
        this(code, null, message);
    }

    public BusinessException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
