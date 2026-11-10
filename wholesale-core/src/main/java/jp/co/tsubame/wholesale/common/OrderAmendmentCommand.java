package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrderAmendmentCommand {
    private Long orderId;
    private int expectedOrderVersion;
    private Date requestedDate;
    private String reason;
    private List<OrderAmendmentLineCommand> lines = new ArrayList<OrderAmendmentLineCommand>();
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = value; }
    public int getExpectedOrderVersion() { return expectedOrderVersion; }
    public void setExpectedOrderVersion(int value) { expectedOrderVersion = value; }
    public Date getRequestedDate() { return requestedDate; }
    public void setRequestedDate(Date value) { requestedDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public List<OrderAmendmentLineCommand> getLines() { return lines; }
    public void setLines(List<OrderAmendmentLineCommand> value) { lines = value; }
}
