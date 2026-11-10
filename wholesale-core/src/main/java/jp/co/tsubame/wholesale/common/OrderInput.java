package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrderInput {
    private Long customerId;
    private Long warehouseId;
    private Date orderDate;
    private Date requestedDate;
    private String deliveryAddress;
    private String externalReference;
    private String notes;
    private List<OrderLineInput> lines = new ArrayList<OrderLineInput>();

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
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

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
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

    public List<OrderLineInput> getLines() {
        return lines;
    }

    public void setLines(List<OrderLineInput> lines) {
        this.lines = lines;
    }
}
