package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.Warehouse;

/** A point-in-time planning view, not an inventory reservation or a purchase commitment. */
public class PurchasingReorderSuggestion {
    private Product product;
    private Warehouse warehouse;
    private Supplier supplier;
    private long onHand;
    private long reserved;
    private long openPurchaseQuantity;
    private long overduePurchaseQuantity;
    private long projectedAvailable;
    private int suggestedQuantity;
    private int orderPackSize;
    private int leadTimeDays;
    private BigDecimal unitCost;
    private BigDecimal suggestedAmount;
    private Date expectedDate;
    private String warning = "";

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public long getOnHand() { return onHand; }
    public void setOnHand(long onHand) { this.onHand = onHand; }
    public long getReserved() { return reserved; }
    public void setReserved(long reserved) { this.reserved = reserved; }
    public long getOpenPurchaseQuantity() { return openPurchaseQuantity; }
    public void setOpenPurchaseQuantity(long quantity) { this.openPurchaseQuantity = quantity; }
    public long getOverduePurchaseQuantity() { return overduePurchaseQuantity; }
    public void setOverduePurchaseQuantity(long quantity) { this.overduePurchaseQuantity = quantity; }
    public long getProjectedAvailable() { return projectedAvailable; }
    public void setProjectedAvailable(long projectedAvailable) { this.projectedAvailable = projectedAvailable; }
    public int getSuggestedQuantity() { return suggestedQuantity; }
    public void setSuggestedQuantity(int suggestedQuantity) { this.suggestedQuantity = suggestedQuantity; }
    public int getOrderPackSize() { return orderPackSize; }
    public void setOrderPackSize(int orderPackSize) { this.orderPackSize = orderPackSize; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getSuggestedAmount() { return suggestedAmount; }
    public void setSuggestedAmount(BigDecimal suggestedAmount) { this.suggestedAmount = suggestedAmount; }
    public Date getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Date expectedDate) { this.expectedDate = expectedDate; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
