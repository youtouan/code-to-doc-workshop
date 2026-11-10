package jp.co.tsubame.wholesale.batch;

public final class RowOutcome {
    private final String code;
    private final String message;
    private final String entityType;
    private final Long entityId;
    private final String reference;

    public RowOutcome(String code, String message, String entityType, Long entityId, String reference) {
        this.code = code;
        this.message = message;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reference = reference;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getReference() { return reference; }
}
