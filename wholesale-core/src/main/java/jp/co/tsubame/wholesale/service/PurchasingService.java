package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.CatalogRules;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.PurchasingReceiptCommand;
import jp.co.tsubame.wholesale.common.PurchasingReceiptLineCommand;
import jp.co.tsubame.wholesale.common.PurchasingReorderSuggestion;
import jp.co.tsubame.wholesale.common.PurchasingRules;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseOrderLine;
import jp.co.tsubame.wholesale.entity.PurchaseReceipt;
import jp.co.tsubame.wholesale.entity.PurchaseReceiptLine;
import jp.co.tsubame.wholesale.entity.StockReceipt;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.entity.Warehouse;

public class PurchasingService extends BaseService {
    private InventoryService inventoryService;

    public void setInventoryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public Supplier getSupplier(Long id, Actor actor) {
        read(actor);
        return dao.get(Supplier.class, id);
    }

    public List<Supplier> listActiveSuppliers(Actor actor) {
        read(actor);
        return dao.list("from Supplier s where s.active = true order by s.code");
    }

    public Page<Supplier> searchSuppliers(Search search, Actor actor) {
        read(actor);
        Checks.state(search != null, "validation.search", "検索条件を指定してください。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String where = " from Supplier s where 1 = 1";
        if (search.getText().length() > 0) {
            where += " and (lower(s.code) like :text escape '!' or lower(s.name) like :text escape '!')";
            parameters.put("text", search.getLikeText().toLowerCase(java.util.Locale.ROOT));
        }
        String status = search.getStatus();
        Checks.state(status.length() == 0 || "ACTIVE".equals(status) || "INACTIVE".equals(status)
                || "HOLD".equals(status), "validation.status", "仕入先状態が不正です。");
        if ("ACTIVE".equals(status)) { where += " and s.active = true"; }
        if ("INACTIVE".equals(status)) { where += " and s.active = false"; }
        if ("HOLD".equals(status)) { where += " and s.onHold = true"; }
        return dao.page("select s" + where + " order by s.code", "select count(s.id)" + where, parameters, search);
    }

    public Supplier saveSupplier(Supplier input, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        PurchasingRules.supplier(input);
        Supplier target = input.getId() == null ? new Supplier() : dao.lock(Supplier.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        String code = Checks.code(input.getCode(), "仕入先コード");
        dao.lockKey("purchasing.supplierCode", code);
        Map<String, Object> parameters = WholesaleDao.params("code", code);
        String hql = "select count(s.id) from Supplier s where s.code = :code";
        if (target.getId() != null) {
            hql += " and s.id <> :id";
            parameters.put("id", target.getId());
        }
        Checks.state(dao.count(hql, parameters) == 0, "supplier.duplicateCode", "仕入先コードが重複しています。");
        target.setCode(code);
        target.setName(input.getName().trim());
        target.setActive(input.isActive());
        target.setOnHold(input.isOnHold());
        target.setClosingDay(input.getClosingDay());
        target.setPaymentTermDays(input.getPaymentTermDays());
        target.setDefaultLeadTimeDays(input.getDefaultLeadTimeDays());
        target.setMinimumOrderAmount(Checks.money(input.getMinimumOrderAmount(), "最低発注金額", true));
        target.setTaxRounding(input.getTaxRounding());
        target.setPostalCode(Checks.optionalText(input.getPostalCode(), "郵便番号", 12));
        target.setAddress(Checks.optionalText(input.getAddress(), "住所", 250));
        target.setTelephone(Checks.optionalText(input.getTelephone(), "電話番号", 30));
        target.setOrderingInstructions(Checks.optionalText(input.getOrderingInstructions(), "発注条件", 1000));
        target.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        dao.save(target);
        audit(actor, "SUPPLIER_SAVE", target, code);
        dao.flush();
        return target;
    }

    public SupplierProduct saveSupplierProduct(SupplierProduct input, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        PurchasingRules.supplierProduct(input);
        dao.lockReferences(Collections.<Long>emptyList(), Collections.singleton(input.getProduct().getId()));
        Supplier supplier = dao.lock(Supplier.class, input.getSupplier().getId());
        Product product = dao.get(Product.class, input.getProduct().getId());
        SupplierProduct target = input.getId() == null ? new SupplierProduct() : dao.get(SupplierProduct.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        Checks.state(target.getId() == null || target.getSupplier().getId().equals(supplier.getId()),
                "supplierProduct.supplierImmutable", "仕入契約の仕入先は変更できません。");
        Checks.state(input.getOrderPackSize() % product.getPackSize() == 0,
                "supplierProduct.productPack", "仕入入数は商品入数の倍数にしてください。");
        Map<String, Object> parameters = WholesaleDao.params("supplier", supplier, "product", product);
        parameters.put("minimum", Integer.valueOf(input.getMinimumQuantity()));
        List<SupplierProduct> peers = dao.list("from SupplierProduct s where s.supplier = :supplier"
                + " and s.product = :product and s.minimumQuantity = :minimum and s.active = true", parameters);
        if (input.isActive()) {
            for (SupplierProduct peer : peers) {
                Checks.state(peer.getId().equals(target.getId())
                        || !CatalogRules.overlaps(input.getValidFrom(), input.getValidTo(),
                                peer.getValidFrom(), peer.getValidTo()),
                        "supplierProduct.overlap", "同じ数量段階の仕入契約期間が重複しています。");
            }
        }
        target.setSupplier(supplier);
        target.setProduct(product);
        target.setSupplierProductCode(Checks.optionalText(input.getSupplierProductCode(), "仕入先商品コード", 60));
        target.setValidFrom(Dates.day(input.getValidFrom()));
        target.setValidTo(Dates.day(input.getValidTo()));
        target.setMinimumQuantity(input.getMinimumQuantity());
        target.setOrderPackSize(input.getOrderPackSize());
        target.setLeadTimeDays(input.getLeadTimeDays());
        target.setUnitCost(Checks.money(input.getUnitCost(), "仕入単価", true));
        target.setPreferred(input.isPreferred());
        target.setActive(input.isActive());
        target.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        dao.save(target);
        audit(actor, "SUPPLIER_PRODUCT_SAVE", target, supplier.getCode() + "/" + product.getCode());
        dao.flush();
        return target;
    }

    public List<SupplierProduct> listSupplierProducts(Long supplierId, Actor actor) {
        read(actor);
        Supplier supplier = dao.get(Supplier.class, supplierId);
        return dao.list("from SupplierProduct s where s.supplier = :supplier"
                + " order by s.product.code, s.minimumQuantity, s.validFrom desc",
                WholesaleDao.params("supplier", supplier));
    }

    public SupplierProduct previewSupplierQuote(Long supplierId, Long productId, int quantity, Date date, Actor actor) {
        read(actor);
        Checks.quantity(quantity, "発注数");
        return quote(dao.get(Supplier.class, supplierId), dao.get(Product.class, productId),
                quantity, Checks.date(date, "発注日"));
    }

    public PurchaseOrder getOrder(Long id, Actor actor) {
        read(actor);
        return initialize(dao.get(PurchaseOrder.class, id));
    }

    public Page<PurchaseOrder> searchOrders(Search search, Actor actor) {
        read(actor);
        Checks.state(search != null, "validation.search", "検索条件を指定してください。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String where = " from PurchaseOrder o where 1 = 1";
        if (search.getText().length() > 0) {
            where += " and (lower(o.number) like :text escape '!'"
                    + " or lower(o.supplier.code) like :text escape '!'"
                    + " or lower(o.supplierName) like :text escape '!')";
            parameters.put("text", search.getLikeText().toLowerCase(java.util.Locale.ROOT));
        }
        if (search.getStatus().length() > 0) {
            Checks.state(isStatus(search.getStatus()), "validation.status", "発注状態が不正です。");
            where += " and o.status = :status";
            parameters.put("status", search.getStatus());
        }
        if (search.getWarehouseId() != null) {
            where += " and o.warehouse.id = :warehouse";
            parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getFrom() != null) {
            where += " and o.orderDate >= :from";
            parameters.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and o.orderDate <= :to";
            parameters.put("to", search.getTo());
        }
        Checks.state(search.getFrom() == null || search.getTo() == null
                || !search.getTo().before(search.getFrom()), "validation.interval", "検索期間が逆転しています。");
        Page<PurchaseOrder> page = dao.page("select o" + where + " order by o.orderDate desc, o.id desc",
                "select count(o.id)" + where, parameters, search);
        for (PurchaseOrder order : page.getItems()) { initialize(order); }
        return page;
    }

    /**
     * The input is a command-shaped detached bean. Only header input and product/quantity/date/notes
     * are accepted; prices, fulfillment, approval and identity fields are always server-generated.
     */
    public PurchaseOrder saveOrder(PurchaseOrder input, int expectedVersion, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER");
        Checks.state(input != null && input.getSupplier() != null && input.getWarehouse() != null,
                "validation.purchaseOrder", "仕入先と入荷倉庫を指定してください。");
        Date orderDate = Checks.date(input.getOrderDate(), "発注日");
        Date expectedDate = Checks.date(input.getExpectedDate(), "入荷予定日");
        Checks.state(!expectedDate.before(orderDate) && !expectedDate.after(Dates.addDays(orderDate, 730)),
                "purchasing.expectedDate", "入荷予定日は発注日から730日以内にしてください。");
        Checks.state(!orderDate.after(Dates.today()), "purchasing.futureOrder", "未来日付の発注はできません。");
        Checks.nonempty(input.getLines(), "発注明細");
        lockOrderReferences(input);
        PurchaseOrder target = input.getId() == null ? new PurchaseOrder() : dao.lock(PurchaseOrder.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        Checks.state(target.isEditable(), "purchasing.notEditable", "下書きまたは差戻し発注のみ編集できます。");
        Supplier supplier = dao.lock(Supplier.class, input.getSupplier().getId());
        Warehouse warehouse = dao.get(Warehouse.class, input.getWarehouse().getId());
        orderable(supplier, warehouse);
        List<PurchaseOrderLine> planned = planLines(input.getLines(), supplier, orderDate, expectedDate);
        BigDecimal total = Money.ZERO;
        for (PurchaseOrderLine line : planned) { total = total.add(line.getLineAmount()); }
        Checks.money(total, "発注金額", true);
        target.setSupplier(supplier);
        target.setWarehouse(warehouse);
        target.setOrderDate(orderDate);
        target.setExpectedDate(expectedDate);
        target.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        target.setTotalAmount(total);
        snapshotTerms(target, supplier);
        if (target.getId() == null) {
            target.setCreatedBy(actor.getLogin());
            target.setCreatedAt(new Date());
            target.setNumber("NEW-" + java.util.UUID.randomUUID().toString());
        } else {
            target.getLines().clear();
            // Hibernate schedules inserts before deletes. Flush orphan removal before reusing line numbers.
            dao.flush();
        }
        target.setStatus("DRAFT");
        target.setSubmittedBy(null);
        target.setSubmittedAt(null);
        target.setApprovedBy(null);
        target.setApprovedAt(null);
        target.setDecisionReason("");
        changed(target, actor);
        dao.save(target);
        if (target.getNumber().startsWith("NEW-")) { target.setNumber(documentNumber("PO", target.getId())); }
        for (PurchaseOrderLine line : planned) {
            line.setOrder(target);
            target.getLines().add(line);
        }
        audit(actor, "PURCHASE_DRAFT_SAVE", target, target.getNumber() + " amount=" + total.toPlainString());
        dao.flush();
        return initialize(target);
    }

    public PurchaseOrder submitOrder(Long id, int expectedVersion, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER");
        PurchaseOrder target = lockedOrder(id, expectedVersion);
        Checks.state(target.isEditable(), "purchasing.notEditable", "下書きまたは差戻し発注のみ申請できます。");
        Supplier supplier = dao.lock(Supplier.class, target.getSupplier().getId());
        orderable(supplier, target.getWarehouse());
        Checks.nonempty(target.getLines(), "発注明細");
        Checks.state(target.getTotalAmount().compareTo(supplier.getMinimumOrderAmount()) >= 0,
                "purchasing.minimumAmount", "仕入先の最低発注金額に達していません。");
        for (PurchaseOrderLine line : target.getLines()) {
            Checks.state(line.getProduct().isActive(), "purchasing.inactiveProduct", "無効な商品が含まれています。");
            SupplierProduct current = quote(supplier, line.getProduct(), line.getQuantity(), target.getOrderDate());
            Checks.state(current.getUnitCost().compareTo(line.getUnitCost()) == 0
                    && current.getOrderPackSize() == line.getOrderPackSize()
                    && current.getLeadTimeDays() == line.getLeadTimeDays(),
                    "purchasing.quoteChanged", "仕入条件が更新されています。発注を再保存してください。");
        }
        snapshotTerms(target, supplier);
        target.setStatus("SUBMITTED");
        target.setSubmittedBy(actor.getLogin());
        target.setSubmittedAt(new Date());
        target.setDecisionReason("");
        changed(target, actor);
        audit(actor, "PURCHASE_SUBMIT", target, target.getNumber());
        dao.flush();
        return target;
    }

    public PurchaseOrder approveOrder(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        PurchaseOrder target = lockedOrder(id, expectedVersion);
        Checks.state("SUBMITTED".equals(target.getStatus()), "purchasing.notSubmitted", "申請済みの発注のみ承認できます。");
        Checks.state(!actor.getLogin().equals(target.getCreatedBy())
                && !actor.getLogin().equals(target.getSubmittedBy()),
                "approval.self", "発注作成者・申請者は自分の発注を承認できません。");
        Supplier supplier = dao.lock(Supplier.class, target.getSupplier().getId());
        orderable(supplier, target.getWarehouse());
        Checks.state(target.getTotalAmount().compareTo(supplier.getMinimumOrderAmount()) >= 0,
                "purchasing.minimumAmount", "現在の最低発注金額に達していません。差し戻してください。");
        for (PurchaseOrderLine line : target.getLines()) {
            Checks.state(line.getProduct().isActive(), "purchasing.inactiveProduct", "無効な商品が含まれています。");
        }
        target.setStatus("APPROVED");
        target.setApprovedBy(actor.getLogin());
        target.setApprovedAt(new Date());
        changed(target, actor);
        audit(actor, "PURCHASE_APPROVE", target, target.getNumber());
        dao.flush();
        return target;
    }

    public PurchaseOrder rejectOrder(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        PurchaseOrder target = lockedOrder(id, expectedVersion);
        Checks.state("SUBMITTED".equals(target.getStatus()), "purchasing.notSubmitted", "申請済みの発注のみ差戻しできます。");
        target.setDecisionReason(Checks.text(reason, "差戻し理由", 500));
        target.setStatus("REJECTED");
        changed(target, actor);
        audit(actor, "PURCHASE_REJECT", target, target.getDecisionReason());
        dao.flush();
        return target;
    }

    public PurchaseOrder cancelOrder(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER");
        PurchaseOrder target = lockedOrder(id, expectedVersion);
        Checks.state(target.isEditable() || "SUBMITTED".equals(target.getStatus())
                || "APPROVED".equals(target.getStatus()), "purchasing.notCancellable",
                "入荷済みまたは終了済みの発注は取消できません。残数打切りを使用してください。");
        if ("APPROVED".equals(target.getStatus())) { require(actor, "MANAGER"); }
        Checks.state(dao.count("select count(r.id) from PurchaseReceipt r where r.order = :order",
                WholesaleDao.params("order", target)) == 0,
                "purchasing.receiptExists", "検品記録がある発注は残数打切りを使用してください。");
        target.setDecisionReason(Checks.text(reason, "取消理由", 500));
        for (PurchaseOrderLine line : target.getLines()) { line.setCancelledQuantity(line.getOutstandingQuantity()); }
        target.setStatus("CANCELLED");
        changed(target, actor);
        audit(actor, "PURCHASE_CANCEL", target, target.getDecisionReason());
        dao.flush();
        return target;
    }

    public PurchaseOrder closeOutstanding(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        PurchaseOrder target = lockedOrder(id, expectedVersion);
        Checks.state(target.isReceivable() && target.getOutstandingQuantity() > 0,
                "purchasing.notOpen", "未入荷残数がある承認済み発注のみ打切りできます。");
        target.setDecisionReason(Checks.text(reason, "打切り理由", 500));
        long cancelled = 0L;
        for (PurchaseOrderLine line : target.getLines()) {
            int outstanding = line.getOutstandingQuantity();
            cancelled += outstanding;
            line.setCancelledQuantity(line.getCancelledQuantity() + outstanding);
        }
        target.setStatus("CLOSED");
        changed(target, actor);
        audit(actor, "PURCHASE_CLOSE_SHORT", target,
                "cancelled=" + cancelled + " reason=" + target.getDecisionReason());
        dao.flush();
        return target;
    }

    public PurchaseReceipt receive(PurchasingReceiptCommand command, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "BATCH");
        final List<PurchasingReceiptLineCommand> inputs = PurchasingRules.receiptLines(command);
        String key = Checks.text(command.getRequestKey(), "要求キー", 100);
        String hash = PurchasingRules.receiptHash(command);
        lockOrderReferences(initialize(dao.get(PurchaseOrder.class, command.getOrderId())));
        dao.lockKey("purchasing.receipt", key);
        List<PurchaseReceipt> existing = dao.list("from PurchaseReceipt r where r.requestKey = :key",
                WholesaleDao.params("key", key));
        if (!existing.isEmpty()) {
            PurchaseReceipt receipt = existing.get(0);
            Checks.state(hash.equals(receipt.getPayloadHash()),
                    "purchasing.idempotencyConflict", "同じ要求キーで異なる入荷内容が送信されています。");
            return initialize(receipt);
        }
        PurchaseOrder order = lockedOrder(command.getOrderId(), command.getExpectedVersion());
        Checks.state(order.isReceivable(), "purchasing.notReceivable", "承認済みまたは一部入荷の発注のみ入荷できます。");
        Checks.state(order.getWarehouse().isActive(), "purchasing.inactiveWarehouse", "入荷倉庫が無効です。");
        Date receiptDate = Checks.date(command.getReceiptDate(), "入荷日");
        Checks.state(!receiptDate.before(order.getOrderDate()) && !receiptDate.after(Dates.today()),
                "purchasing.receiptDate", "入荷日は発注日以降かつ本日以前にしてください。");
        final Map<Long, PurchaseOrderLine> orderLines = new HashMap<Long, PurchaseOrderLine>();
        for (PurchaseOrderLine line : order.getLines()) { orderLines.put(line.getId(), line); }
        for (PurchasingReceiptLineCommand input : inputs) {
            PurchaseOrderLine line = orderLines.get(input.getOrderLineId());
            Checks.state(line != null, "purchasing.foreignLine", "この発注に属さない明細です。");
            Checks.state((long) input.getAcceptedQuantity() + input.getRejectedQuantity() <= line.getOutstandingQuantity(),
                    "purchasing.overReceipt", "入荷数量が発注残数を超えています。");
            Checks.state((long) line.getRejectedQuantity() + input.getRejectedQuantity() <= Integer.MAX_VALUE,
                    "purchasing.rejectionLimit", "累計不良品数が上限を超えています。");
            if (input.getAcceptedQuantity() > 0) {
                Checks.state(line.getProduct().isActive(), "purchasing.inactiveProduct", "無効な商品は良品入荷できません。");
            }
        }
        // All purchasing receipts acquire inventory locks in the same product order.
        Collections.sort(inputs, new Comparator<PurchasingReceiptLineCommand>() {
            public int compare(PurchasingReceiptLineCommand a, PurchasingReceiptLineCommand b) {
                return orderLines.get(a.getOrderLineId()).getProduct().getId()
                        .compareTo(orderLines.get(b.getOrderLineId()).getProduct().getId());
            }
        });
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setNumber("NEW-" + java.util.UUID.randomUUID().toString());
        receipt.setRequestKey(key);
        receipt.setPayloadHash(hash);
        receipt.setOrder(order);
        receipt.setReceiptDate(receiptDate);
        receipt.setSupplierDeliveryNumber(Checks.optionalText(command.getSupplierDeliveryNumber(), "仕入先納品書番号", 60));
        receipt.setNotes(Checks.optionalText(command.getNotes(), "備考", 1000));
        receipt.setReceivedBy(actor.getLogin());
        receipt.setReceivedAt(new Date());
        dao.save(receipt);
        receipt.setNumber(documentNumber("PR", receipt.getId()));
        int lineNumber = 0;
        BigDecimal acceptedAmount = Money.ZERO;
        for (PurchasingReceiptLineCommand input : inputs) {
            PurchaseOrderLine orderLine = orderLines.get(input.getOrderLineId());
            PurchaseReceiptLine line = new PurchaseReceiptLine();
            line.setReceipt(receipt);
            line.setLineNumber(++lineNumber);
            line.setOrderLine(orderLine);
            line.setAcceptedQuantity(input.getAcceptedQuantity());
            line.setRejectedQuantity(input.getRejectedQuantity());
            line.setRejectionReason(Checks.optionalText(input.getRejectionReason(), "不良理由", 250));
            line.setNotes(Checks.optionalText(input.getNotes(), "明細備考", 250));
            line.setUnitCost(orderLine.getUnitCost());
            line.setAcceptedAmount(orderLine.getUnitCost().multiply(BigDecimal.valueOf(input.getAcceptedQuantity())));
            if (input.getAcceptedQuantity() > 0) {
                StockReceipt inventoryReceipt = inventoryService.receive(
                        "PORECEIPT-" + receipt.getId() + "-" + lineNumber, order.getWarehouse().getId(),
                        orderLine.getProduct().getId(), input.getAcceptedQuantity(), line.getUnitCost(), receiptDate,
                        order.getNumber() + "/" + receipt.getNumber(), line.getNotes(), actor);
                line.setStockReceipt(inventoryReceipt);
            }
            receipt.getLines().add(line);
            orderLine.setReceivedQuantity(orderLine.getReceivedQuantity() + input.getAcceptedQuantity());
            // Rejected deliveries remain outstanding; replacement goods can be received later.
            orderLine.setRejectedQuantity(orderLine.getRejectedQuantity() + input.getRejectedQuantity());
            acceptedAmount = acceptedAmount.add(line.getAcceptedAmount());
        }
        receipt.setAcceptedAmount(Checks.money(acceptedAmount, "入荷金額", true));
        order.setStatus(order.getOutstandingQuantity() == 0 ? "RECEIVED" : "PART_RECEIVED");
        changed(order, actor);
        audit(actor, "PURCHASE_RECEIVE", receipt, order.getNumber() + " accepted="
                + receipt.getAcceptedQuantity() + " rejected=" + receipt.getRejectedQuantity());
        dao.flush();
        return initialize(receipt);
    }

    public PurchaseReceipt getReceipt(Long id, Actor actor) {
        read(actor);
        return initialize(dao.get(PurchaseReceipt.class, id));
    }

    public List<PurchaseReceipt> listReceipts(Long orderId, Actor actor) {
        read(actor);
        PurchaseOrder order = dao.get(PurchaseOrder.class, orderId);
        List<PurchaseReceipt> result = dao.list("from PurchaseReceipt r where r.order = :order"
                + " order by r.receiptDate, r.id", WholesaleDao.params("order", order));
        for (PurchaseReceipt receipt : result) { initialize(receipt); }
        return result;
    }

    public List<PurchaseOrderLine> listOverdueLines(Long warehouseId, Date onDate, Actor actor) {
        read(actor);
        Map<String, Object> parameters = WholesaleDao.params("day", Checks.date(onDate, "基準日"));
        String hql = "from PurchaseOrderLine l where l.order.status in ('APPROVED','PART_RECEIVED')"
                + " and l.quantity > l.receivedQuantity + l.cancelledQuantity and l.expectedDate < :day";
        if (warehouseId != null) {
            parameters.put("warehouse", dao.get(Warehouse.class, warehouseId));
            hql += " and l.order.warehouse = :warehouse";
        }
        return dao.list(hql + " order by l.expectedDate, l.order.number, l.lineNumber", parameters);
    }

    public List<PurchasingReorderSuggestion> previewReorder(Long warehouseId, Date onDate, Actor actor) {
        read(actor);
        Warehouse warehouse = dao.get(Warehouse.class, warehouseId);
        Date day = Checks.date(onDate, "発注予定日");
        Checks.state(warehouse.isActive(), "purchasing.inactiveWarehouse", "無効な倉庫の発注提案は作成できません。");
        Map<String, Object> parameters = WholesaleDao.params("warehouse", warehouse);
        List<Object[]> balances = dao.list("select b.product.id, b.onHand, b.reserved, b.blocked"
                + " from StockBalance b where b.warehouse = :warehouse", parameters);
        Map<Long, Object[]> stock = index(balances);
        List<Object[]> commitments = dao.list("select l.product.id, sum(l.quantity-l.receivedQuantity-l.cancelledQuantity)"
                + " from PurchaseOrderLine l where l.order.warehouse = :warehouse"
                + " and l.order.status in ('APPROVED','PART_RECEIVED') group by l.product.id", parameters);
        Map<Long, Object[]> open = index(commitments);
        parameters.put("day", day);
        List<Object[]> overdueRows = dao.list("select l.product.id, sum(l.quantity-l.receivedQuantity-l.cancelledQuantity)"
                + " from PurchaseOrderLine l where l.order.warehouse = :warehouse"
                + " and l.order.status in ('APPROVED','PART_RECEIVED') and l.expectedDate < :day"
                + " group by l.product.id", parameters);
        Map<Long, Object[]> overdue = index(overdueRows);
        List<Product> products = dao.list("from Product p where p.active = true"
                + " and (p.reorderPoint > 0 or p.reorderQuantity > 0) order by p.code");
        List<SupplierProduct> offers = dao.list("from SupplierProduct s where s.active = true"
                + " and s.supplier.active = true and s.supplier.onHold = false and s.product.active = true"
                + " and s.validFrom <= :day and (s.validTo is null or s.validTo >= :day)"
                + " order by s.product.id, s.supplier.id, s.minimumQuantity desc, s.id",
                WholesaleDao.params("day", day));
        Map<Long, List<SupplierProduct>> byProduct = new HashMap<Long, List<SupplierProduct>>();
        for (SupplierProduct offer : offers) {
            List<SupplierProduct> group = byProduct.get(offer.getProduct().getId());
            if (group == null) {
                group = new ArrayList<SupplierProduct>();
                byProduct.put(offer.getProduct().getId(), group);
            }
            group.add(offer);
        }
        List<PurchasingReorderSuggestion> result = new ArrayList<PurchasingReorderSuggestion>();
        for (Product product : products) {
            Object[] balance = stock.get(product.getId());
            long onHand = balance == null ? 0L : ((Number) balance[1]).longValue();
            long reserved = balance == null ? 0L : ((Number) balance[2]).longValue();
            boolean blocked = balance != null && Boolean.TRUE.equals(balance[3]);
            long committed = aggregate(open, product.getId());
            long projected = (blocked ? 0L : onHand - reserved) + committed;
            if (projected > product.getReorderPoint()) { continue; }
            PurchasingReorderSuggestion suggestion = new PurchasingReorderSuggestion();
            suggestion.setProduct(product);
            suggestion.setWarehouse(warehouse);
            suggestion.setOnHand(onHand);
            suggestion.setReserved(reserved);
            suggestion.setOpenPurchaseQuantity(committed);
            suggestion.setOverduePurchaseQuantity(aggregate(overdue, product.getId()));
            suggestion.setProjectedAvailable(projected);
            long need = Math.max((long) product.getReorderQuantity(), (long) product.getReorderPoint() + 1L - projected);
            chooseOffer(suggestion, byProduct.get(product.getId()), need, day);
            if (blocked) { warning(suggestion, "在庫が出庫停止中です。"); }
            if (suggestion.getOverduePurchaseQuantity() > 0) {
                warning(suggestion, "納期超過の発注残があります。二重発注前に仕入先へ確認してください。");
            }
            result.add(suggestion);
        }
        return result;
    }

    private List<PurchaseOrderLine> planLines(List<PurchaseOrderLine> inputs, Supplier supplier,
            Date orderDate, Date headerExpectedDate) {
        List<PurchaseOrderLine> result = new ArrayList<PurchaseOrderLine>();
        Set<Long> productIds = new HashSet<Long>();
        int number = 0;
        for (PurchaseOrderLine input : inputs) {
            Checks.state(input != null && input.getProduct() != null,
                    "validation.purchaseLine", "商品を指定してください。");
            Product product = dao.get(Product.class, input.getProduct().getId());
            Checks.state(productIds.add(product.getId()), "purchasing.duplicateProduct", "同じ商品は1明細にまとめてください。");
            Checks.state(product.isActive(), "purchasing.inactiveProduct", "無効な商品は発注できません。");
            int quantity = Checks.quantity(input.getQuantity(), "発注数");
            SupplierProduct contract = quote(supplier, product, quantity, orderDate);
            Date expected = input.getExpectedDate() == null ? headerExpectedDate : Dates.day(input.getExpectedDate());
            Checks.state(!expected.before(Dates.addDays(orderDate, contract.getLeadTimeDays()))
                    && !expected.after(Dates.addDays(orderDate, 730)),
                    "purchasing.leadTime", "入荷予定日が調達日数を満たしていないか730日を超えています。");
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setLineNumber(++number);
            line.setProduct(product);
            line.setProductCode(product.getCode());
            line.setProductName(product.getName());
            line.setUnit(product.getUnit());
            line.setSupplierProductCode(contract.getSupplierProductCode());
            line.setQuantity(quantity);
            line.setOrderPackSize(contract.getOrderPackSize());
            line.setLeadTimeDays(contract.getLeadTimeDays());
            line.setUnitCost(contract.getUnitCost());
            line.setLineAmount(PurchasingRules.lineAmount(contract.getUnitCost(), quantity));
            line.setExpectedDate(expected);
            line.setNotes(Checks.optionalText(input.getNotes(), "明細備考", 250));
            result.add(line);
        }
        return result;
    }

    private SupplierProduct quote(Supplier supplier, Product product, int quantity, Date date) {
        Map<String, Object> parameters = WholesaleDao.params("supplier", supplier, "product", product);
        parameters.put("quantity", Integer.valueOf(quantity));
        parameters.put("date", date);
        List<SupplierProduct> contracts = dao.list("from SupplierProduct s where s.supplier = :supplier"
                + " and s.product = :product and s.active = true and s.minimumQuantity <= :quantity"
                + " and s.validFrom <= :date and (s.validTo is null or s.validTo >= :date)"
                + " order by s.minimumQuantity desc, s.validFrom desc, s.id desc", parameters);
        Checks.state(!contracts.isEmpty(), "purchasing.noQuote", "適用可能な仕入契約がありません。最低数量と契約期間を確認してください。");
        SupplierProduct contract = contracts.get(0);
        Checks.state(quantity % contract.getOrderPackSize() == 0 && quantity % product.getPackSize() == 0,
                "purchasing.orderPack", "発注数は仕入入数・商品入数の倍数にしてください。");
        return contract;
    }

    private void chooseOffer(PurchasingReorderSuggestion suggestion, List<SupplierProduct> offers,
            long need, Date day) {
        if (need > 1000000L) {
            warning(suggestion, "必要数が1回の発注上限を超えています。複数発注へ分割してください。");
            return;
        }
        SupplierProduct best = null;
        int bestQuantity = 0;
        if (offers != null) {
            for (SupplierProduct offer : offers) {
                if (offer.getOrderPackSize() % suggestion.getProduct().getPackSize() != 0) { continue; }
                long raw = Math.max(need, (long) offer.getMinimumQuantity());
                long rounded = ((raw + offer.getOrderPackSize() - 1L) / offer.getOrderPackSize()) * offer.getOrderPackSize();
                if (rounded > 1000000L) { continue; }
                int quantity = (int) rounded;
                // A rounded order may enter a higher tier; never propose a lower tier that the order would not use.
                SupplierProduct effective = effectiveOffer(offers, offer.getSupplier().getId(), quantity);
                if (effective != offer || quantity % effective.getOrderPackSize() != 0) { continue; }
                BigDecimal amount = effective.getUnitCost().multiply(BigDecimal.valueOf(quantity));
                if (amount.compareTo(new BigDecimal("999999999999.99")) > 0) { continue; }
                if (best == null || better(effective, quantity, best, bestQuantity)) {
                    best = effective;
                    bestQuantity = quantity;
                }
            }
        }
        if (best == null) {
            warning(suggestion, "利用可能な仕入先・数量条件がありません。仕入契約を確認してください。");
            return;
        }
        suggestion.setSupplier(best.getSupplier());
        suggestion.setSuggestedQuantity(bestQuantity);
        suggestion.setOrderPackSize(best.getOrderPackSize());
        suggestion.setLeadTimeDays(best.getLeadTimeDays());
        suggestion.setUnitCost(best.getUnitCost());
        suggestion.setSuggestedAmount(best.getUnitCost().multiply(BigDecimal.valueOf(bestQuantity)));
        suggestion.setExpectedDate(Dates.addDays(day, best.getLeadTimeDays()));
        if (suggestion.getSuggestedAmount().compareTo(best.getSupplier().getMinimumOrderAmount()) < 0) {
            warning(suggestion, "仕入先最低発注金額未満です。他の商品とまとめて発注してください。");
        }
    }

    private SupplierProduct effectiveOffer(List<SupplierProduct> offers, Long supplierId, int quantity) {
        SupplierProduct result = null;
        for (SupplierProduct offer : offers) {
            if (supplierId.equals(offer.getSupplier().getId()) && offer.getMinimumQuantity() <= quantity
                    && (result == null || offer.getMinimumQuantity() > result.getMinimumQuantity())) {
                result = offer;
            }
        }
        return result;
    }

    private boolean better(SupplierProduct candidate, int quantity, SupplierProduct existing, int existingQuantity) {
        if (candidate.isPreferred() != existing.isPreferred()) { return candidate.isPreferred(); }
        int amount = candidate.getUnitCost().multiply(BigDecimal.valueOf(quantity))
                .compareTo(existing.getUnitCost().multiply(BigDecimal.valueOf(existingQuantity)));
        if (amount != 0) { return amount < 0; }
        if (candidate.getLeadTimeDays() != existing.getLeadTimeDays()) {
            return candidate.getLeadTimeDays() < existing.getLeadTimeDays();
        }
        return candidate.getSupplier().getCode().compareTo(existing.getSupplier().getCode()) < 0;
    }

    private void snapshotTerms(PurchaseOrder order, Supplier supplier) {
        order.setSupplierName(supplier.getName());
        order.setClosingDay(supplier.getClosingDay());
        order.setPaymentTermDays(supplier.getPaymentTermDays());
        order.setTaxRounding(supplier.getTaxRounding());
        order.setOrderingInstructions(supplier.getOrderingInstructions());
    }

    private void orderable(Supplier supplier, Warehouse warehouse) {
        Checks.state(supplier.isActive() && !supplier.isOnHold(),
                "purchasing.supplierUnavailable", "無効または発注停止中の仕入先です。");
        Checks.state(warehouse.isActive(), "purchasing.inactiveWarehouse", "無効な倉庫には発注できません。");
    }

    private PurchaseOrder lockedOrder(Long id, int expectedVersion) {
        PurchaseOrder snapshot = initialize(dao.get(PurchaseOrder.class, id));
        lockOrderReferences(snapshot);
        PurchaseOrder order = dao.lock(PurchaseOrder.class, id);
        Checks.version(order.getVersion(), expectedVersion);
        return initialize(order);
    }

    private void lockOrderReferences(PurchaseOrder order) {
        Checks.state(order.getWarehouse() != null, "validation.warehouse", "入荷倉庫を指定してください。");
        List<Long> productIds = new ArrayList<Long>();
        for (PurchaseOrderLine line : order.getLines()) {
            Checks.state(line != null && line.getProduct() != null,
                    "validation.purchaseLine", "商品を指定してください。");
            productIds.add(line.getProduct().getId());
        }
        dao.lockReferences(Collections.singleton(order.getWarehouse().getId()), productIds);
    }

    private PurchaseOrder initialize(PurchaseOrder order) {
        org.hibernate.Hibernate.initialize(order.getLines());
        return order;
    }

    private PurchaseReceipt initialize(PurchaseReceipt receipt) {
        org.hibernate.Hibernate.initialize(receipt.getLines());
        initialize(receipt.getOrder());
        return receipt;
    }

    private void changed(PurchaseOrder order, Actor actor) {
        order.setLastChangedAt(new Date());
        order.setLastChangedBy(actor.getLogin());
    }

    private Map<Long, Object[]> index(List<Object[]> rows) {
        Map<Long, Object[]> result = new HashMap<Long, Object[]>();
        for (Object[] row : rows) { result.put((Long) row[0], row); }
        return result;
    }

    private long aggregate(Map<Long, Object[]> rows, Long productId) {
        Object[] row = rows.get(productId);
        return row == null || row[1] == null ? 0L : ((Number) row[1]).longValue();
    }

    private void warning(PurchasingReorderSuggestion suggestion, String warning) {
        suggestion.setWarning(suggestion.getWarning() + warning);
    }

    private boolean isStatus(String status) {
        return "DRAFT".equals(status) || "SUBMITTED".equals(status) || "APPROVED".equals(status)
                || "REJECTED".equals(status) || "CANCELLED".equals(status) || "PART_RECEIVED".equals(status)
                || "RECEIVED".equals(status) || "CLOSED".equals(status);
    }

    private void read(Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH");
    }
}
