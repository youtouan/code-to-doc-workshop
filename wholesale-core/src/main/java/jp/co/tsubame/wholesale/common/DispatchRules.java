package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;

public final class DispatchRules {
    private DispatchRules() { }

    public static String carrier(String carrier) {
        Checks.state("OWN".equals(carrier) || "PARCEL".equals(carrier) || "FREIGHT".equals(carrier),
                "dispatch.carrier", "配送区分を選択してください。");
        return carrier;
    }

    public static List<Long> shipmentIds(List<DispatchStopCommand> stops) {
        Checks.state(stops != null && stops.size() <= 100, "dispatch.stopLimit", "配送表は100出荷までです。");
        List<Long> ids = new ArrayList<Long>();
        for (DispatchStopCommand stop : stops) {
            Checks.state(stop != null && stop.getShipmentId() != null && stop.getShipmentId() > 0,
                    "dispatch.shipment", "出荷指示を指定してください。");
            Checks.state(!ids.contains(stop.getShipmentId()), "dispatch.duplicateShipment", "同じ出荷が重複しています。");
            ids.add(stop.getShipmentId());
        }
        return ids;
    }

    public static Date plannedDate(Date date) {
        Date day = Checks.date(date, "配送予定日");
        Checks.state(!day.before(Dates.addDays(Dates.today(), -30)) && !day.after(Dates.addDays(Dates.today(), 90)),
                "dispatch.plannedDate", "配送予定日は本日の30日前から90日後までです。");
        return day;
    }

    public static Map<Long, String> tracking(List<DispatchTrackingCommand> lines) {
        Checks.state(lines != null && !lines.isEmpty() && lines.size() <= 100,
                "dispatch.tracking", "各出荷の実際の送り状番号または自社配送管理番号が必要です。");
        Map<Long, String> result = new LinkedHashMap<Long, String>();
        for (DispatchTrackingCommand line : lines) {
            Checks.state(line != null && line.getShipmentId() != null && !result.containsKey(line.getShipmentId()),
                    "dispatch.tracking", "送り状の出荷指定が不正または重複しています。");
            result.put(line.getShipmentId(), Checks.text(line.getTrackingReference(), "手入力の配送管理番号", 80));
        }
        return result;
    }

    public static String fingerprint(Shipment shipment) {
        List<String> fields = new ArrayList<String>();
        fields.add(String.valueOf(shipment.getId()));
        fields.add(shipment.getStatus());
        fields.add(shipment.getCarrier());
        fields.add(Dates.format(shipment.getPlannedDate()));
        fields.add(shipment.getOrder().getStatus());
        fields.add(String.valueOf(shipment.getOrder().getWarehouse().getId()));
        fields.add(String.valueOf(shipment.getOrder().getCustomer().getId()));
        fields.add(shipment.getOrder().getCustomerName());
        fields.add(shipment.getOrder().getDeliveryAddress());
        List<ShipmentLine> lines = new ArrayList<ShipmentLine>(shipment.getLines());
        Collections.sort(lines, new Comparator<ShipmentLine>() {
            @Override public int compare(ShipmentLine first, ShipmentLine second) {
                return first.getId().compareTo(second.getId());
            }
        });
        for (ShipmentLine line : lines) {
            SalesOrderLine orderLine = line.getOrderLine();
            fields.add(line.getId() + ":" + line.getVersion() + ":" + line.getQuantity());
            fields.add(orderLine.getId() + ":" + orderLine.getVersion() + ":" + orderLine.getProduct().getId());
            fields.add(String.valueOf(line.getLineNumber()));
            fields.add(line.getProductCode());
            fields.add(line.getProductName());
            fields.add(line.getUnit());
            fields.add(line.getUnitPrice().stripTrailingZeros().toPlainString());
            fields.add(line.getTaxRate().stripTrailingZeros().toPlainString());
        }
        return Fingerprints.of(fields.toArray(new String[fields.size()]));
    }
}
