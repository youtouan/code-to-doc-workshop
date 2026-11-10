package jp.co.tsubame.wholesale.common;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;

public class DeliverySummary {
    private final Shipment shipment;
    private final DeliveryAttempt effectiveAttempt;
    private final Long latestEventId;
    private final Date asOfRecordedAt;
    private final long shippedQuantity;
    private final long currentReturnedQuantity;
    public DeliverySummary(Shipment shipment, DeliveryAttempt effectiveAttempt, Long latestEventId, Date asOfRecordedAt) {
        this.shipment = shipment;
        this.effectiveAttempt = effectiveAttempt;
        this.latestEventId = latestEventId;
        this.asOfRecordedAt = asOfRecordedAt;
        long shipped = 0;
        long returned = 0;
        for (ShipmentLine line : shipment.getLines()) {
            shipped += line.getQuantity();
            returned += line.getReturnedQuantity();
        }
        shippedQuantity = shipped;
        currentReturnedQuantity = returned;
    }
    public Long getShipmentId() { return shipment.getId(); }
    public String getShipmentNumber() { return shipment.getNumber(); }
    public String getOrderNumber() { return shipment.getOrder().getNumber(); }
    public Long getWarehouseId() { return shipment.getWarehouse().getId(); }
    public String getWarehouseCode() { return shipment.getWarehouse().getCode(); }
    public String getCustomerName() { return shipment.getOrder().getCustomerName(); }
    public String getCarrier() { return shipment.getCarrier(); }
    public String getTrackingReference() { return shipment.getTrackingNumber(); }
    public Date getShippedBusinessDate() { return shipment.getShippedDate(); }
    public DeliveryAttempt getEffectiveAttempt() { return effectiveAttempt; }
    public Long getLatestEventId() { return latestEventId; }
    public Date getAsOfRecordedAt() { return asOfRecordedAt; }
    public String getOutcome() { return effectiveAttempt == null ? "NONE" : effectiveAttempt.getOutcome(); }
    public Date getAttemptAt() { return effectiveAttempt == null ? null : effectiveAttempt.getAttemptAt(); }
    public Date getBusinessDate() { return effectiveAttempt == null ? null : effectiveAttempt.getBusinessDate(); }
    public Date getNextAttemptDate() { return effectiveAttempt == null ? null : effectiveAttempt.getNextAttemptDate(); }
    public long getShippedQuantity() { return shippedQuantity; }
    public long getCurrentReturnedQuantity() { return currentReturnedQuantity; }
    public String getReturnQuantityBasis() { return "CURRENT_RECEIVED_RETURNS_NOT_UNDELIVERED_QUANTITY"; }
    public String getOutcomeBasis() { return "MANUAL_UNVERIFIED_REPORT"; }
    public String getAsOfBasis() { return "KNOWN_BY_RECORDED_AT_LATEST_EFFECTIVE_ATTEMPT_AT"; }
}
