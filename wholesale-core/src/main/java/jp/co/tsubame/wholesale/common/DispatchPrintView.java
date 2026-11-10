package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.entity.DispatchManifest;
import jp.co.tsubame.wholesale.entity.DispatchStop;
import jp.co.tsubame.wholesale.entity.ShipmentLine;

public class DispatchPrintView {
    private final DispatchManifest manifest;
    private final DispatchReadiness readiness;
    private final Date generatedAt = new Date();
    private final List<Stop> stops = new ArrayList<Stop>();
    public DispatchPrintView(DispatchManifest manifest, DispatchReadiness readiness) {
        this.manifest = manifest;
        this.readiness = readiness;
        for (DispatchStop stop : manifest.getStops()) { stops.add(new Stop(stop)); }
    }
    public DispatchManifest getManifest() { return manifest; }
    public DispatchReadiness getReadiness() { return readiness; }
    public Date getGeneratedAt() { return generatedAt; }
    public List<Stop> getStops() { return Collections.unmodifiableList(stops); }
    public String getRouteBasis() { return "MANUAL_STOP_ORDER_EXISTING_ORDER_ADDRESS"; }
    public static class Stop {
        private final DispatchStop source;
        private final List<Item> items = new ArrayList<Item>();
        private long quantity;
        private long returnedQuantity;
        private Stop(DispatchStop source) {
            this.source = source;
            for (ShipmentLine line : source.getShipment().getLines()) {
                items.add(new Item(line));
                quantity += line.getQuantity();
                returnedQuantity += line.getReturnedQuantity();
            }
        }
        public int getSequence() { return source.getStopSequence(); }
        public String getShipmentNumber() { return source.getShipment().getNumber(); }
        public Long getShipmentId() { return source.getShipment().getId(); }
        public String getOrderNumber() { return source.getShipment().getOrder().getNumber(); }
        public String getCustomerName() { return source.getCustomerName(); }
        public String getDeliveryAddress() { return source.getDeliveryAddress(); }
        public String getNote() { return source.getNote(); }
        public String getShipmentStatus() { return source.getShipment().getStatus(); }
        public String getTrackingReference() { return source.getShipment().getTrackingNumber(); }
        public List<Item> getItems() { return Collections.unmodifiableList(items); }
        public long getQuantity() { return quantity; }
        public long getCurrentReturnedQuantity() { return returnedQuantity; }
    }
    public static class Item {
        private final ShipmentLine source;
        private Item(ShipmentLine source) { this.source = source; }
        public String getProductCode() { return source.getProductCode(); }
        public String getProductName() { return source.getProductName(); }
        public String getUnit() { return source.getUnit(); }
        public int getQuantity() { return source.getQuantity(); }
        public int getCurrentReturnedQuantity() { return source.getReturnedQuantity(); }
    }
}
