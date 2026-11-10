package jp.co.tsubame.wholesale.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderStates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.dao.InventoryLedger;
import jp.co.tsubame.wholesale.dao.OrderLocks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.SalesReturnLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import jp.co.tsubame.wholesale.entity.StockBalance;
import org.hibernate.Hibernate;

public class ShippingService extends BaseService {
    private InventoryService inventoryService;
    private InventoryLedger ledger;
    private BillingService billingService;
    private Map<String, String> carriers = new LinkedHashMap<String, String>();

    public void setInventoryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public void setLedger(InventoryLedger ledger) {
        this.ledger = ledger;
    }

    public void setBillingService(BillingService billingService) {
        this.billingService = billingService;
    }

    public void setCarriers(Map<String, String> carriers) {
        this.carriers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(carriers));
    }

    public Map<String, String> listCarriers(Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BATCH");
        return carriers;
    }

    public Shipment instruct(Long orderId, int expectedOrderVersion, Date plannedDate, String carrier,
                             String note, List<ShipmentLineInput> inputs, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER");
        SalesOrder order = lockOrder(orderId);
        Checks.version(order.getVersion(), expectedOrderVersion);
        Checks.state(OrderStates.isApproved(order.getStatus()), "shipping.orderState", "承認済みの未完了受注だけ出荷指示できます。");
        validateShippingAllowed(order);
        Date date = Checks.date(plannedDate, "出荷予定日");
        Checks.state(!date.before(order.getOrderDate()), "shipping.date", "出荷予定日は受注日以降です。");
        Checks.state(carriers.containsKey(carrier), "shipping.carrier", "配送区分を選択してください。");
        Checks.nonempty(inputs, "出荷明細");
        Shipment shipment = new Shipment();
        shipment.setNumber("NEW-" + UUID.randomUUID().toString());
        shipment.setOrder(order);
        shipment.setStatus("INSTRUCTED");
        shipment.setPlannedDate(date);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber("");
        shipment.setNote(Checks.optionalText(note, "出荷備考", 500));
        shipment.setCreatedBy(actor.getLogin());
        shipment.setCreatedAt(new Date());
        Set<Long> selected = new HashSet<Long>();
        for (ShipmentLineInput input : inputs) {
            Checks.state(input != null, "shipping.line", "出荷明細が不正です。");
            SalesOrderLine orderLine = dao.get(SalesOrderLine.class, input.getOrderLineId());
            Checks.state(orderLine.getOrder().getId().equals(orderId), "shipping.otherOrder", "別受注の明細を指定しています。");
            Checks.state(selected.add(orderLine.getId()), "shipping.duplicateLine", "出荷明細が重複しています。");
            int quantity = Checks.quantity(input.getQuantity(), "指示数量");
            Checks.state(quantity % orderLine.getPackSize() == 0, "shipping.pack", "指示数量は受注時の入数の倍数にしてください。");
            long pending = dao.count("select sum(l.quantity) from ShipmentLine l "
                    + "where l.orderLine.id=:line and l.shipment.status='INSTRUCTED'",
                    WholesaleDao.params("line", orderLine.getId()));
            Checks.state(quantity + pending <= orderLine.getAllocatedQuantity(), "shipping.excessInstruction",
                    orderLine.getProductCode() + "の引当数量を超える出荷指示です。");
            ShipmentLine line = new ShipmentLine();
            line.setShipment(shipment);
            line.setOrderLine(orderLine);
            line.setLineNumber(shipment.getLines().size() + 1);
            line.setQuantity(quantity);
            line.setReturnedQuantity(0);
            line.setProductCode(orderLine.getProductCode());
            line.setProductName(orderLine.getProductName());
            line.setUnit(orderLine.getUnit());
            line.setUnitPrice(orderLine.getUnitPrice());
            line.setTaxRate(orderLine.getTaxRate());
            shipment.getLines().add(line);
        }
        dao.save(shipment);
        shipment.setNumber(documentNumber("SH", shipment.getId()));
        audit(actor, "SHIPMENT_INSTRUCT", shipment, order.getNumber());
        dao.flush();
        return shipment;
    }

    public Shipment confirm(Long id, int expectedVersion, Date shippedDate, String trackingNumber, Actor actor) {
        require(actor, "WAREHOUSE", "BATCH");
        Shipment shipment = lockShipment(id);
        Checks.version(shipment.getVersion(), expectedVersion);
        Checks.state("INSTRUCTED".equals(shipment.getStatus()), "shipping.notInstructed", "未確定の出荷指示だけ確定できます。");
        SalesOrder order = shipment.getOrder();
        Checks.state(OrderStates.isApproved(order.getStatus()), "shipping.orderState", "受注が取消または完了しています。");
        validateShippingAllowed(order);
        Date date = Checks.date(shippedDate, "出荷日");
        Checks.state(!date.before(order.getOrderDate()) && !date.after(Dates.today()),
                "shipping.date", "出荷日は受注日以降かつ本日以前にしてください。");
        String tracking = Checks.text(trackingNumber, "送り状番号", 80);
        List<ShipmentLine> sorted = new ArrayList<ShipmentLine>(shipment.getLines());
        Collections.sort(sorted, new Comparator<ShipmentLine>() {
            @Override
            public int compare(ShipmentLine first, ShipmentLine second) {
                return first.getOrderLine().getProduct().getId().compareTo(second.getOrderLine().getProduct().getId());
            }
        });
        for (ShipmentLine line : sorted) {
            Checks.state(line.getQuantity() <= line.getOrderLine().getOpenQuantity(),
                    "shipping.excess", "受注残数量を超えた出荷です。");
            inventoryService.consume(line.getOrderLine(), line.getQuantity(), id, shipment.getNumber(), actor);
        }
        shipment.setShippedDate(date);
        shipment.setTrackingNumber(tracking);
        shipment.setConfirmedAt(new Date());
        shipment.setConfirmedBy(actor.getLogin());
        shipment.setStatus("CONFIRMED");
        OrderStates.updateFulfilment(order);
        audit(actor, "SHIPMENT_CONFIRM", shipment, tracking);
        dao.flush();
        return shipment;
    }

    public Shipment cancelInstruction(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER");
        Shipment shipment = lockShipment(id);
        Checks.version(shipment.getVersion(), expectedVersion);
        Checks.state("INSTRUCTED".equals(shipment.getStatus()), "shipping.confirmed",
                "出荷確定後は取消できません。返品処理を使用してください。");
        String message = Checks.text(reason, "取消理由", 500);
        shipment.setStatus("CANCELLED");
        shipment.setCancellationReason(message);
        audit(actor, "SHIPMENT_CANCEL", shipment, message);
        dao.flush();
        return shipment;
    }

    public Shipment getShipment(Long id, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        return initialize(dao.get(Shipment.class, id));
    }

    public List<Shipment> listOrderShipments(Long orderId, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        List<Shipment> shipments = dao.list("from Shipment s where s.order.id=:order order by s.id desc",
                WholesaleDao.params("order", orderId));
        for (Shipment shipment : shipments) {
            initialize(shipment);
        }
        return shipments;
    }

    public Page<Shipment> searchShipments(Search search, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        Map<String, Object> params = WholesaleDao.params("text", search.getLikeText());
        String where = " from Shipment s where (s.number like :text escape '!' "
                + "or s.order.customerName like :text escape '!' or s.trackingNumber like :text escape '!')";
        if (search.getCustomerId() != null) {
            where += " and s.order.customer.id=:customer";
            params.put("customer", search.getCustomerId());
        }
        if (search.getWarehouseId() != null) {
            where += " and s.order.warehouse.id=:warehouse";
            params.put("warehouse", search.getWarehouseId());
        }
        if (search.getStatus().length() != 0) {
            where += " and s.status=:status";
            params.put("status", search.getStatus());
        }
        if (search.getFrom() != null) {
            where += " and s.plannedDate>=:from";
            params.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and s.plannedDate<=:to";
            params.put("to", search.getTo());
        }
        Page<Shipment> result = dao.page("select s" + where + " order by s.id desc",
                "select count(s.id)" + where, params, search);
        for (Shipment shipment : result.getItems()) {
            initialize(shipment);
        }
        return result;
    }

    public SalesReturn requestReturn(Long shipmentId, String reason, String notes,
                                     List<ReturnLineInput> inputs, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER");
        Shipment shipment = lockShipment(shipmentId);
        Checks.state("CONFIRMED".equals(shipment.getStatus()), "return.notShipped", "出荷確定済みの伝票だけ返品できます。");
        Checks.state(!Dates.today().after(Dates.addDays(shipment.getShippedDate(), 30)),
                "return.window", "返品申請は出荷日から30日以内です。");
        Checks.state("DAMAGED".equals(reason) || "CUSTOMER_CHANGE".equals(reason)
                || "MISSHIP".equals(reason) || "EXPIRED".equals(reason), "return.reason", "返品理由区分が不正です。");
        Checks.nonempty(inputs, "返品明細");
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setNumber("NEW-" + UUID.randomUUID().toString());
        salesReturn.setShipment(shipment);
        salesReturn.setStatus("REQUESTED");
        salesReturn.setReason(reason);
        salesReturn.setRequestedDate(Dates.today());
        salesReturn.setCreatedBy(actor.getLogin());
        salesReturn.setCreatedAt(new Date());
        salesReturn.setNotes(Checks.optionalText(notes, "返品備考", 1000));
        Set<Long> selected = new HashSet<Long>();
        for (ReturnLineInput input : inputs) {
            Checks.state(input != null, "return.line", "返品明細が不正です。");
            ShipmentLine source = dao.get(ShipmentLine.class, input.getShipmentLineId());
            Checks.state(source.getShipment().getId().equals(shipmentId), "return.otherShipment", "別出荷の明細を指定しています。");
            Checks.state(selected.add(source.getId()), "return.duplicateLine", "返品明細が重複しています。");
            int quantity = Checks.quantity(input.getQuantity(), "返品数量");
            long pending = dao.count("select sum(l.quantity) from SalesReturnLine l "
                    + "where l.shipmentLine.id=:line and l.salesReturn.status in ('REQUESTED','APPROVED')",
                    WholesaleDao.params("line", source.getId()));
            Checks.state(quantity + pending <= source.getReturnableQuantity(),
                    "return.excess", "既申請・受入済み数量を含めると出荷数量を超えます。");
            Checks.state(!input.isRestock() || (!"DAMAGED".equals(reason) && !"EXPIRED".equals(reason)),
                    "return.disposition", "破損品・期限切れ品を販売可能在庫に戻すことはできません。");
            Checks.state(!input.isRestock() || (shipment.getWarehouse().isActive()
                    && source.getOrderLine().getProduct().isActive()), "return.inactive",
                    "停止中の商品・倉庫には返品入庫できません。在庫へ戻さない返品を選択してください。");
            SalesReturnLine line = new SalesReturnLine();
            line.setSalesReturn(salesReturn);
            line.setShipmentLine(source);
            line.setLineNumber(salesReturn.getLines().size() + 1);
            line.setQuantity(quantity);
            line.setRestock(input.isRestock());
            line.setUnitPrice(source.getUnitPrice());
            line.setTaxRate(source.getTaxRate());
            line.setProductCode(source.getProductCode());
            line.setProductName(source.getProductName());
            salesReturn.getLines().add(line);
        }
        dao.save(salesReturn);
        salesReturn.setNumber(documentNumber("RT", salesReturn.getId()));
        audit(actor, "RETURN_REQUEST", salesReturn, reason);
        dao.flush();
        return salesReturn;
    }

    public SalesReturn approveReturn(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        SalesReturn salesReturn = lockReturn(id);
        Checks.version(salesReturn.getVersion(), expectedVersion);
        Checks.state("REQUESTED".equals(salesReturn.getStatus()), "return.notRequested", "申請中の返品だけ承認できます。");
        Checks.state(!salesReturn.getCreatedBy().equals(actor.getLogin()), "approval.self", "自分で申請した返品は承認できません。");
        salesReturn.setStatus("APPROVED");
        salesReturn.setApprovedBy(actor.getLogin());
        salesReturn.setApprovedAt(new Date());
        audit(actor, "RETURN_APPROVE", salesReturn, salesReturn.getReason());
        dao.flush();
        return salesReturn;
    }

    public SalesReturn rejectReturn(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        SalesReturn salesReturn = lockReturn(id);
        Checks.version(salesReturn.getVersion(), expectedVersion);
        Checks.state("REQUESTED".equals(salesReturn.getStatus()), "return.notRequested", "申請中の返品だけ却下できます。");
        salesReturn.setStatus("REJECTED");
        audit(actor, "RETURN_REJECT", salesReturn, Checks.text(reason, "却下理由", 500));
        dao.flush();
        return salesReturn;
    }

    public SalesReturn cancelReturn(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER");
        SalesReturn salesReturn = lockReturn(id);
        Checks.version(salesReturn.getVersion(), expectedVersion);
        Checks.state("REQUESTED".equals(salesReturn.getStatus()) || "APPROVED".equals(salesReturn.getStatus()),
                "return.received", "受入済み・取消済みの返品は取消できません。");
        salesReturn.setStatus("CANCELLED");
        audit(actor, "RETURN_CANCEL", salesReturn, Checks.text(reason, "取消理由", 500));
        dao.flush();
        return salesReturn;
    }

    public SalesReturn receiveReturn(Long id, int expectedVersion, Date receivedDate, Actor actor) {
        require(actor, "WAREHOUSE");
        SalesReturn salesReturn = lockReturn(id);
        Checks.version(salesReturn.getVersion(), expectedVersion);
        Checks.state("APPROVED".equals(salesReturn.getStatus()), "return.notApproved", "承認済みの返品だけ受入できます。");
        Date date = Checks.date(receivedDate, "返品受入日");
        Checks.state(!date.before(salesReturn.getRequestedDate()) && !date.after(Dates.today()),
                "return.date", "受入日は申請日以降かつ本日以前にしてください。");
        List<SalesReturnLine> sorted = new ArrayList<SalesReturnLine>(salesReturn.getLines());
        Collections.sort(sorted, new Comparator<SalesReturnLine>() {
            @Override
            public int compare(SalesReturnLine first, SalesReturnLine second) {
                return first.getShipmentLine().getOrderLine().getProduct().getId()
                        .compareTo(second.getShipmentLine().getOrderLine().getProduct().getId());
            }
        });
        for (SalesReturnLine line : sorted) {
            ShipmentLine source = line.getShipmentLine();
            Checks.state(line.getQuantity() <= source.getReturnableQuantity(), "return.excess", "返品可能数量を超えています。");
            source.setReturnedQuantity(source.getReturnedQuantity() + line.getQuantity());
            if (line.isRestock()) {
                Checks.state(salesReturn.getShipment().getWarehouse().isActive(),
                        "return.warehouse", "停止中の倉庫に返品入庫できません。");
                StockBalance balance = ledger.lock(salesReturn.getShipment().getWarehouse().getId(),
                        source.getOrderLine().getProduct().getId());
                ledger.post(balance, line.getQuantity(), 0, "RETURN", "SalesReturn", id,
                        salesReturn.getNumber(), salesReturn.getReason(), actor);
            }
        }
        salesReturn.setStatus("RECEIVED");
        salesReturn.setReceivedDate(date);
        salesReturn.setReceivedAt(new Date());
        salesReturn.setReceivedBy(actor.getLogin());
        billingService.issueReturnCredit(salesReturn, actor);
        audit(actor, "RETURN_RECEIVE", salesReturn, salesReturn.getReason());
        dao.flush();
        return salesReturn;
    }

    public SalesReturn getReturn(Long id, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        return initializeReturn(dao.get(SalesReturn.class, id));
    }

    public Page<SalesReturn> searchReturns(Search search, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        Map<String, Object> params = WholesaleDao.params("text", search.getLikeText());
        String where = " from SalesReturn r where (r.number like :text escape '!' "
                + "or r.shipment.order.customerName like :text escape '!')";
        if (search.getStatus().length() != 0) {
            where += " and r.status=:status";
            params.put("status", search.getStatus());
        }
        if (search.getCustomerId() != null) {
            where += " and r.shipment.order.customer.id=:customer";
            params.put("customer", search.getCustomerId());
        }
        if (search.getWarehouseId() != null) {
            where += " and r.shipment.order.warehouse.id=:warehouse";
            params.put("warehouse", search.getWarehouseId());
        }
        if (search.getFrom() != null) {
            where += " and r.requestedDate>=:from";
            params.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and r.requestedDate<=:to";
            params.put("to", search.getTo());
        }
        Page<SalesReturn> result = dao.page("select r" + where + " order by r.id desc",
                "select count(r.id)" + where, params, search);
        for (SalesReturn salesReturn : result.getItems()) {
            initializeReturn(salesReturn);
        }
        return result;
    }

    private SalesOrder lockOrder(Long id) {
        return OrderLocks.lock(dao, id);
    }

    private Shipment lockShipment(Long id) {
        Shipment found = dao.get(Shipment.class, id);
        lockOrder(found.getOrder().getId());
        return initialize(dao.lock(Shipment.class, id));
    }

    private SalesReturn lockReturn(Long id) {
        SalesReturn found = dao.get(SalesReturn.class, id);
        lockShipment(found.getShipment().getId());
        return initializeReturn(dao.lock(SalesReturn.class, id));
    }

    private Shipment initialize(Shipment shipment) {
        Hibernate.initialize(shipment.getLines());
        Hibernate.initialize(shipment.getOrder().getLines());
        return shipment;
    }

    private SalesReturn initializeReturn(SalesReturn salesReturn) {
        Hibernate.initialize(salesReturn.getLines());
        initialize(salesReturn.getShipment());
        return salesReturn;
    }

    private void validateShippingAllowed(SalesOrder order) {
        Checks.state(order.getCustomer().isActive() && !order.getCustomer().isOnHold(),
                "shipping.customer", "受注停止中の得意先には出荷できません。");
        Checks.state(order.getWarehouse().isActive(), "shipping.warehouse", "停止中の倉庫から出荷できません。");
        for (SalesOrderLine line : order.getLines()) {
            Checks.state(line.getProduct().isActive(), "shipping.product", "停止中の商品が含まれています。");
        }
    }
}
