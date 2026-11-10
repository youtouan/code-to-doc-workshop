package jp.co.tsubame.wholesale.batch.entity;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.BaseEntity;

public class BatchRow extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long runId;
    private int rowNumber;
    private long sourceLine;
    private String payloadHash;
    private String result;
    private String code;
    private String message;
    private String entityType;
    private Long entityId;
    private String entityReference;
    private Date finishedAt;

    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int value) { rowNumber = value; }
    public long getSourceLine() { return sourceLine; }
    public void setSourceLine(long value) { sourceLine = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String value) { entityType = value; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long value) { entityId = value; }
    public String getEntityReference() { return entityReference; }
    public void setEntityReference(String value) { entityReference = value; }
    public Date getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Date value) { finishedAt = value; }
}
