package jp.co.tsubame.wholesale.common;

public class OrderAmendmentLineCommand {
    private Long orderLineId;
    private int targetQuantity;
    public Long getOrderLineId() { return orderLineId; }
    public void setOrderLineId(Long value) { orderLineId = value; }
    public int getTargetQuantity() { return targetQuantity; }
    public void setTargetQuantity(int value) { targetQuantity = value; }
}
