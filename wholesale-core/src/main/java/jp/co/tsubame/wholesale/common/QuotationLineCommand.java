package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public class QuotationLineCommand {
    private Long productId;
    private int quantity;
    private BigDecimal negotiatedUnitPrice;
    private String negotiationReason = "";
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public BigDecimal getNegotiatedUnitPrice() { return negotiatedUnitPrice; }
    public void setNegotiatedUnitPrice(BigDecimal value) { negotiatedUnitPrice = value; }
    public String getNegotiationReason() { return negotiationReason; }
    public void setNegotiationReason(String value) { negotiationReason = value; }
}
