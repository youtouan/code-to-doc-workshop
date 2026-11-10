package jp.co.tsubame.wholesale.batch.entity;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.BaseEntity;

public class BatchRun extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String runKey;
    private String command;
    private String invocation;
    private String payloadHash;
    private String inputSha256;
    private Long actorId;
    private String actorLogin;
    private String status;
    private int totalRows;
    private int successRows;
    private int rejectedRows;
    private boolean hasMore;
    private int exitCode;
    private String message;
    private Date startedAt;
    private Date endedAt;

    public String getRunKey() { return runKey; }
    public void setRunKey(String value) { runKey = value; }
    public String getCommand() { return command; }
    public void setCommand(String value) { command = value; }
    public String getInvocation() { return invocation; }
    public void setInvocation(String value) { invocation = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }
    public String getInputSha256() { return inputSha256; }
    public void setInputSha256(String value) { inputSha256 = value; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { actorId = value; }
    public String getActorLogin() { return actorLogin; }
    public void setActorLogin(String value) { actorLogin = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int value) { totalRows = value; }
    public int getSuccessRows() { return successRows; }
    public void setSuccessRows(int value) { successRows = value; }
    public int getRejectedRows() { return rejectedRows; }
    public void setRejectedRows(int value) { rejectedRows = value; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean value) { hasMore = value; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int value) { exitCode = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date value) { startedAt = value; }
    public Date getEndedAt() { return endedAt; }
    public void setEndedAt(Date value) { endedAt = value; }
}
