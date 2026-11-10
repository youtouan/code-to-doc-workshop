package jp.co.tsubame.wholesale.service;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.DispatchConfirmationCommand;
import jp.co.tsubame.wholesale.common.DispatchManifestCommand;
import jp.co.tsubame.wholesale.common.DispatchPrintView;
import jp.co.tsubame.wholesale.common.DispatchReadiness;
import jp.co.tsubame.wholesale.common.DispatchRules;
import jp.co.tsubame.wholesale.common.DispatchStopCommand;
import jp.co.tsubame.wholesale.common.OrderStates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.DispatchLocks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.DispatchManifest;
import jp.co.tsubame.wholesale.entity.DispatchStop;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.Warehouse;

public class DispatchManifestService extends BaseService {
    private DispatchLocks locks;
    private ShippingService shippingService;
    public void setLocks(DispatchLocks locks) { this.locks = locks; }
    public void setShippingService(ShippingService shippingService) { this.shippingService = shippingService; }

    public DispatchManifest get(Actor actor, Long id) {
        reader(actor);
        return DispatchLocks.initialize(dao.get(DispatchManifest.class, id));
    }

    public Page<DispatchManifest> search(Actor actor, Search search) {
        reader(actor);
        Checks.state(search != null, "validation.search", "検索条件が必要です。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String filter = " from DispatchManifest m where 1=1";
        if (search.getWarehouseId() != null) {
            filter += " and m.warehouse.id=:warehouse";
            parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getCustomerId() != null) {
            filter += " and exists (select s.id from DispatchStop s where s.manifest=m and s.shipment.order.customer.id=:customer)";
            parameters.put("customer", search.getCustomerId());
        }
        if (!search.getStatus().isEmpty()) {
            Checks.state(java.util.Arrays.asList("DRAFT","RELEASED","DISPATCHED","CANCELLED").contains(search.getStatus()),
                    "dispatch.status", "配送表の状態条件が不正です。");
            filter += " and m.status=:status";
            parameters.put("status", search.getStatus());
        }
        if (!search.getText().isEmpty()) {
            filter += " and m.number like :text escape '!'";
            parameters.put("text", search.getLikeText());
        }
        if (search.getFrom() != null) { filter += " and m.plannedDispatchDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { filter += " and m.plannedDispatchDate<=:to"; parameters.put("to", search.getTo()); }
        Page<DispatchManifest> page = dao.page("select m" + filter + " order by m.plannedDispatchDate,m.id",
                "select count(m.id)" + filter, parameters, search);
        for (DispatchManifest manifest : page.getItems()) { DispatchLocks.initialize(manifest); }
        return page;
    }

    public Page<Shipment> listCandidates(Actor actor, Search search) {
        reader(actor);
        Checks.state(search != null && (search.getStatus().isEmpty() || "INSTRUCTED".equals(search.getStatus())),
                "dispatch.status", "候補は未確定の出荷指示です。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String filter = " from Shipment s where s.status='INSTRUCTED' and not exists "
                + "(select d.id from DispatchStop d where d.shipment=s and d.active=true)";
        if (search.getWarehouseId() != null) {
            filter += " and s.order.warehouse.id=:warehouse"; parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getCustomerId() != null) {
            filter += " and s.order.customer.id=:customer"; parameters.put("customer", search.getCustomerId());
        }
        if (!search.getText().isEmpty()) {
            filter += " and (s.number like :text escape '!' or s.order.number like :text escape '!')";
            parameters.put("text", search.getLikeText());
        }
        if (search.getFrom() != null) { filter += " and s.plannedDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { filter += " and s.plannedDate<=:to"; parameters.put("to", search.getTo()); }
        Page<Shipment> page = dao.page("select s" + filter + " order by s.plannedDate,s.id",
                "select count(s.id)" + filter, parameters, search);
        for (Shipment shipment : page.getItems()) { DispatchLocks.initialize(shipment); }
        return page;
    }

    public DispatchManifest saveDraft(Actor actor, DispatchManifestCommand command) {
        require(actor, "WAREHOUSE", "MANAGER");
        Checks.state(command != null && command.getWarehouseId() != null, "dispatch.command", "配送元倉庫が必要です。");
        List<Long> ids = DispatchRules.shipmentIds(command.getStops());
        String carrier = DispatchRules.carrier(command.getCarrier());
        Date planned = DispatchRules.plannedDate(command.getPlannedDispatchDate());
        DispatchManifest manifest;
        boolean fresh = command.getId() == null;
        if (fresh) {
            Checks.version(0, command.getExpectedVersion());
            locks.sources(ids, Collections.singleton(command.getWarehouseId()));
            manifest = new DispatchManifest();
            manifest.setNumber("NEW-" + UUID.randomUUID().toString());
            manifest.setCreatedBy(actor.getLogin());
            manifest.setCreatedAt(new Date());
        } else {
            manifest = locks.manifest(command.getId(), command.getExpectedVersion(), ids, command.getWarehouseId());
            state(manifest, "DRAFT");
        }
        Warehouse warehouse = dao.get(Warehouse.class, command.getWarehouseId());
        Checks.state(warehouse.isActive(), "dispatch.warehouse", "停止中の倉庫は配送元に指定できません。");
        for (DispatchStopCommand input : command.getStops()) {
            Shipment shipment = DispatchLocks.initialize(dao.get(Shipment.class, input.getShipmentId()));
            Checks.version(shipment.getVersion(), input.getExpectedShipmentVersion());
            Checks.state("INSTRUCTED".equals(shipment.getStatus()), "dispatch.sourceStatus", "未確定の出荷指示のみ計画できます。");
            Checks.nonempty(shipment.getLines(), "出荷明細");
            Checks.state(shipment.getWarehouse().getId().equals(warehouse.getId()), "dispatch.mixedWarehouse", "倉庫が異なる出荷は混載できません。");
            Checks.state(carrier.equals(shipment.getCarrier()), "dispatch.mixedCarrier", "配送区分が異なる出荷は混載できません。");
            Checks.state(!planned.before(shipment.getPlannedDate()) && !planned.before(shipment.getOrder().getOrderDate()),
                    "dispatch.plannedDate", "配送予定日は出荷予定日・受注日以降です。");
            Checks.state(OrderStates.isApproved(shipment.getOrder().getStatus()), "dispatch.sourceOrder", "取消・完了受注は計画できません。");
            Checks.state(shipment.getOrder().getCustomer().isActive() && !shipment.getOrder().getCustomer().isOnHold(),
                    "dispatch.customer", "停止中の得意先は配送できません。");
            for (SalesOrderLine line : shipment.getOrder().getLines()) {
                Checks.state(line.getProduct().isActive(), "dispatch.product", "停止中の商品があります。");
            }
            Map<String, Object> claim = WholesaleDao.params("shipment", shipment.getId());
            String filter = "select count(s.id) from DispatchStop s where s.shipment.id=:shipment and s.active=true";
            if (!fresh) { filter += " and s.manifest.id<>:manifest"; claim.put("manifest", manifest.getId()); }
            Checks.state(dao.count(filter, claim) == 0, "dispatch.claimed", "別の有効な配送表に割当済みの出荷です。");
        }
        if (!fresh) { manifest.getStops().clear(); dao.flush(); }
        manifest.setWarehouse(warehouse);
        manifest.setCarrier(carrier);
        manifest.setPlannedDispatchDate(planned);
        manifest.setNote(Checks.optionalText(command.getNote(), "配送備考", 500));
        manifest.setUpdatedAt(nextUpdate(manifest.getUpdatedAt()));
        for (DispatchStopCommand input : command.getStops()) {
            Shipment shipment = dao.get(Shipment.class, input.getShipmentId());
            DispatchStop stop = new DispatchStop();
            stop.setManifest(manifest);
            stop.setShipment(shipment);
            stop.setStopSequence(manifest.getStops().size() + 1);
            stop.setSourceShipmentVersion(shipment.getVersion());
            stop.setSourceOrderVersion(shipment.getOrder().getVersion());
            stop.setSourceFingerprint(DispatchRules.fingerprint(shipment));
            stop.setCustomerName(shipment.getOrder().getCustomerName());
            stop.setDeliveryAddress(shipment.getOrder().getDeliveryAddress());
            stop.setNote(Checks.optionalText(input.getNote(), "配送先備考", 250));
            manifest.getStops().add(stop);
        }
        dao.save(manifest);
        if (fresh) { manifest.setNumber(documentNumber("DSP", manifest.getId())); }
        return finish(actor, fresh ? "DISPATCH_PLAN_CREATE" : "DISPATCH_PLAN_EDIT", manifest, manifest.getNote());
    }

    public DispatchManifest release(Actor actor, Long id, int expectedVersion) {
        require(actor, "WAREHOUSE", "MANAGER");
        DispatchManifest manifest = locked(id, expectedVersion);
        state(manifest, "DRAFT");
        requireReady(manifest);
        manifest.setStatus("RELEASED");
        manifest.setReleasedBy(actor.getLogin());
        manifest.setReleasedAt(new Date());
        return finish(actor, "DISPATCH_RELEASE", manifest, "No additional stock reservation");
    }

    public DispatchManifest replan(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "WAREHOUSE", "MANAGER");
        DispatchManifest manifest = locked(id, expectedVersion);
        state(manifest, "RELEASED");
        String detail = Checks.text(reason, "再計画理由", 500);
        manifest.setStatus("DRAFT");
        manifest.setReleasedBy(null);
        manifest.setReleasedAt(null);
        return finish(actor, "DISPATCH_REPLAN", manifest, detail);
    }

    public DispatchManifest cancel(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "WAREHOUSE", "MANAGER");
        DispatchManifest manifest = locked(id, expectedVersion);
        Checks.state("DRAFT".equals(manifest.getStatus()) || "RELEASED".equals(manifest.getStatus()),
                "dispatch.state", "出発前の配送表のみ取消できます。");
        manifest.setCancellationReason(Checks.text(reason, "取消理由", 500));
        for (DispatchStop stop : manifest.getStops()) { stop.setActive(false); }
        manifest.setStatus("CANCELLED");
        return finish(actor, "DISPATCH_PLAN_CANCEL", manifest, manifest.getCancellationReason());
    }

    public DispatchManifest confirm(Actor actor, Long id, int expectedVersion, DispatchConfirmationCommand command) {
        require(actor, "WAREHOUSE", "BATCH");
        Checks.state(command != null, "dispatch.command", "配送確定内容が必要です。");
        Date date = Checks.date(command.getDispatchDate(), "実出荷日");
        Map<Long, String> tracking = DispatchRules.tracking(command.getTracking());
        DispatchManifest manifest = locked(id, expectedVersion);
        state(manifest, "RELEASED");
        Checks.state(!date.before(manifest.getPlannedDispatchDate()) && !date.after(Dates.today()),
                "dispatch.date", "実出荷日は配送予定日以降かつ本日以前です。");
        requireReady(manifest);
        Checks.state(tracking.size() == manifest.getStops().size(), "dispatch.tracking", "全出荷の配送管理番号を指定してください。");
        for (DispatchStop stop : manifest.getStops()) {
            Checks.state(tracking.containsKey(stop.getShipment().getId()), "dispatch.tracking", "別の出荷の配送管理番号があります。");
        }
        // All source orders/shipments are already locked in ascending order. Shared confirmation owns stock/accounting.
        for (DispatchStop stop : manifest.getStops()) {
            Shipment shipment = stop.getShipment();
            shippingService.confirm(shipment.getId(), shipment.getVersion(), date, tracking.get(shipment.getId()), actor);
            stop.setActive(false);
        }
        manifest.setStatus("DISPATCHED");
        manifest.setDispatchDate(date);
        manifest.setDispatchedBy(actor.getLogin());
        manifest.setDispatchedAt(new Date());
        return finish(actor, "DISPATCH_CONFIRM", manifest, manifest.getTrackingReferenceBasis());
    }

    public DispatchReadiness getReadiness(Actor actor, Long id) {
        reader(actor);
        return readiness(DispatchLocks.initialize(dao.get(DispatchManifest.class, id)));
    }

    public DispatchPrintView getPrintView(Actor actor, Long id) {
        reader(actor);
        DispatchManifest manifest = DispatchLocks.initialize(dao.get(DispatchManifest.class, id));
        return new DispatchPrintView(manifest, readiness(manifest));
    }

    private DispatchReadiness readiness(DispatchManifest manifest) {
        DispatchReadiness report = new DispatchReadiness(manifest.getId(), manifest.getVersion());
        if (!"DRAFT".equals(manifest.getStatus()) && !"RELEASED".equals(manifest.getStatus())) {
            report.add(null, "dispatch.state", "配送表は出発前の状態ではありません。");
            return report;
        }
        if (manifest.getStops().isEmpty()) { report.add(null, "dispatch.empty", "出荷指示が選択されていません。"); }
        if (!manifest.getWarehouse().isActive()) { report.add(null, "dispatch.warehouse", "配送元倉庫が停止中です。"); }
        Map<Long, Long> stockNeeded = new TreeMap<Long, Long>();
        Map<Long, Long> allocationNeeded = new TreeMap<Long, Long>();
        Map<Long, SalesOrderLine> orderLines = new LinkedHashMap<Long, SalesOrderLine>();
        for (DispatchStop stop : manifest.getStops()) {
            Shipment shipment = stop.getShipment();
            Long id = shipment.getId();
            SalesOrder order = shipment.getOrder();
            if (shipment.getLines().isEmpty()) { report.add(id, "dispatch.sourceEmpty", "出荷明細がありません。"); }
            if (!"INSTRUCTED".equals(shipment.getStatus())) {
                report.add(id, "dispatch.source." + shipment.getStatus(), "出荷指示が取消または確定されています。");
            }
            if (!stop.isActive()) { report.add(id, "dispatch.claim", "配送割当が解除されています。"); }
            if (!manifest.getWarehouse().getId().equals(shipment.getWarehouse().getId())
                    || !manifest.getCarrier().equals(shipment.getCarrier())) {
                report.add(id, "dispatch.sourceRoute", "倉庫または配送区分が一致しません。");
            }
            if (stop.getSourceShipmentVersion() != shipment.getVersion()
                    || stop.getSourceOrderVersion() != order.getVersion()
                    || !stop.getSourceFingerprint().equals(DispatchRules.fingerprint(shipment))) {
                report.add(id, "dispatch.sourceChanged", "計画後に出荷または受注が変更されました。再計画してください。");
            }
            if (!OrderStates.isApproved(order.getStatus())) { report.add(id, "dispatch.order", "受注が取消または完了しています。"); }
            if (!order.getCustomer().isActive() || order.getCustomer().isOnHold()) {
                report.add(id, "dispatch.customer", "得意先が受注停止中です。");
            }
            for (SalesOrderLine line : order.getLines()) {
                if (!line.getProduct().isActive()) { report.add(id, "dispatch.product", "停止中の商品を含みます。"); break; }
            }
            for (ShipmentLine line : shipment.getLines()) {
                Long product = line.getOrderLine().getProduct().getId();
                Long orderLine = line.getOrderLine().getId();
                stockNeeded.put(product, total(stockNeeded, product) + line.getQuantity());
                allocationNeeded.put(orderLine, total(allocationNeeded, orderLine) + line.getQuantity());
                orderLines.put(orderLine, line.getOrderLine());
            }
        }
        for (Map.Entry<Long, Long> required : allocationNeeded.entrySet()) {
            SalesOrderLine line = orderLines.get(required.getKey());
            if (required.getValue() > line.getAllocatedQuantity() || required.getValue() > line.getOpenQuantity()) {
                report.add(null, "dispatch.allocation", line.getProductCode() + "の出荷指示合計が受注残・引当数量を超えます。");
            }
        }
        if (!stockNeeded.isEmpty()) {
            Map<String, Object> parameters = WholesaleDao.params("warehouse", manifest.getWarehouse().getId(),
                    "products", stockNeeded.keySet());
            List<StockBalance> balances = dao.list("from StockBalance b where b.warehouse.id=:warehouse and b.product.id in (:products)",
                    parameters);
            Map<Long, StockBalance> byProduct = new LinkedHashMap<Long, StockBalance>();
            for (StockBalance balance : balances) { byProduct.put(balance.getProduct().getId(), balance); }
            for (Map.Entry<Long, Long> required : stockNeeded.entrySet()) {
                StockBalance balance = byProduct.get(required.getKey());
                if (balance == null || balance.isBlocked() || balance.getOnHand() < required.getValue()
                        || balance.getReserved() < required.getValue()) {
                    report.add(null, "dispatch.stock", "在庫・引当不足または棚卸凍結の商品ID: " + required.getKey());
                }
            }
        }
        return report;
    }

    private long total(Map<Long, Long> quantities, Long id) { return quantities.containsKey(id) ? quantities.get(id) : 0L; }
    private void requireReady(DispatchManifest manifest) {
        DispatchReadiness report = readiness(manifest);
        Checks.state(report.isReady(), "dispatch.notReady",
                report.isReady() ? "" : report.getIssues().get(0).getMessage());
    }
    private DispatchManifest locked(Long id, int version) {
        return locks.manifest(id, version, Collections.<Long>emptyList(), null);
    }
    private void state(DispatchManifest manifest, String state) {
        Checks.state(state.equals(manifest.getStatus()), "dispatch.state", "現在の配送表状態では実行できません。");
    }
    private Date nextUpdate(Date previous) {
        return new Date(Math.max(System.currentTimeMillis(), previous == null ? 0L : previous.getTime() + 1L));
    }
    private DispatchManifest finish(Actor actor, String operation, DispatchManifest manifest, String detail) {
        manifest.setUpdatedAt(nextUpdate(manifest.getUpdatedAt()));
        audit(actor, operation, manifest, detail);
        dao.flush();
        return DispatchLocks.initialize(manifest);
    }
    private void reader(Actor actor) { require(actor, "WAREHOUSE", "MANAGER", "SALES", "BILLING", "BATCH"); }
}
