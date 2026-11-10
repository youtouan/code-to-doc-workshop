package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Money;

public class SalesOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Customer customer;
    private Warehouse warehouse;
    private String customerName;
    private String deliveryAddress;
    private Date orderDate;
    private Date requestedDate;
    private String status;
    private String externalReference;
    private String notes;
    private String taxRounding;
    private BigDecimal netAmount = Money.ZERO;
    private BigDecimal taxAmount = Money.ZERO;
    private String createdBy;
    private Date createdAt;
    private String approvedBy;
    private Date approvedAt;
    private String cancellationReason;
    private List<SalesOrderLine> lines = new ArrayList<SalesOrderLine>();

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public Date getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

    public Date getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(Date requestedDate) {
        this.requestedDate = requestedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTaxRounding() {
        return taxRounding;
    }

    public void setTaxRounding(String taxRounding) {
        this.taxRounding = taxRounding;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return netAmount.add(taxAmount);
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Date getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Date approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public List<SalesOrderLine> getLines() {
        return lines;
    }

    public void setLines(List<SalesOrderLine> lines) {
        this.lines = lines;
    }

    public boolean isEditable() {
        return "DRAFT".equals(status);
    }

    public int getOpenQuantity() {
        int total = 0;
        for (SalesOrderLine line : lines) {
            total += line.getOpenQuantity();
        }
        return total;
    }

    public int getAllocatedQuantity() {
        int total = 0;
        for (SalesOrderLine line : lines) {
            total += line.getAllocatedQuantity();
        }
        return total;
    }

    public int getShippedQuantity() {
        int total = 0;
        for (SalesOrderLine line : lines) {
            total += line.getShippedQuantity();
        }
        return total;
    }
}
