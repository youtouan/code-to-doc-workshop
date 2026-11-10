package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public class OrderLineInput {
    private Long productId;
    private int quantity;
    private BigDecimal priceOverride;
    private String priceReason;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPriceOverride() {
        return priceOverride;
    }

    public void setPriceOverride(BigDecimal priceOverride) {
        this.priceOverride = priceOverride;
    }

    public String getPriceReason() {
        return priceReason;
    }

    public void setPriceReason(String priceReason) {
        this.priceReason = priceReason;
    }
}
