package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class StockAdjustment extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Warehouse warehouse;
    private Product product;
    private int quantityChange;
    private String reason;
    private String status = "PROPOSED";
    private BigDecimal unitCost;
    private Long proposedById;
    private String proposedBy;
    private Date proposedAt;
    private String decidedBy;
    private Date decidedAt;
    private String decisionReason = "";

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getQuantityChange() { return quantityChange; }
    public void setQuantityChange(int value) { quantityChange = value; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal cost) { unitCost = cost; }
    public Long getProposedById() { return proposedById; }
    public void setProposedById(Long id) { proposedById = id; }
    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String value) { proposedBy = value; }
    public Date getProposedAt() { return proposedAt; }
    public void setProposedAt(Date value) { proposedAt = value; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String value) { decidedBy = value; }
    public Date getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Date value) { decidedAt = value; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String value) { decisionReason = value; }
    public String getCostBasis() { return "STANDARD_COST_AT_PROPOSAL"; }
    public BigDecimal getValueChange() { return unitCost.multiply(BigDecimal.valueOf(quantityChange)); }
}
