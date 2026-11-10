package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.dao.BillingAmounts;
import jp.co.tsubame.wholesale.dao.BillingLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;

public class CreditService extends BaseService {
    public void checkApproval(SalesOrder order, Actor actor) {
        require(actor, "MANAGER", "BATCH");
        Checks.state(order != null && order.getCustomer() != null, "credit.order", "受注・得意先を指定してください。");
        Customer customer = dao.lock(Customer.class, order.getCustomer().getId());
        Checks.state("SUBMITTED".equals(order.getStatus()), "credit.orderState", "承認申請中の受注だけを確認できます。");
        Checks.state(customer.isActive(), "credit.inactive", "無効な得意先の受注は承認できません。");
        Checks.state(!customer.isOnHold(), "credit.onHold", "取引保留中の得意先の受注は承認できません。");
        Checks.state(customer.getCreditLimit() != null && customer.getCreditLimit().signum() > 0,
                "credit.noLimit", "与信限度額が設定されていません。ゼロ限度額では掛売できません。");
        BillingAmounts candidate = new BillingAmounts(order.getTaxRounding());
        for (SalesOrderLine line : order.getLines()) {
            candidate.add(Money.amount(line.getUnitPrice(), line.getOpenQuantity()), line.getTaxRate());
        }
        BigDecimal exposure = exposure(customer).add(candidate.gross());
        Checks.state(exposure.compareTo(customer.getCreditLimit()) <= 0, "credit.limit",
                "与信限度額超過: 見込残高=" + exposure.toPlainString()
                + " / 限度額=" + customer.getCreditLimit().toPlainString());
    }

    public BigDecimal previewExposure(Long customerId, Actor actor) {
        require(actor, "SALES", "BILLING", "MANAGER", "BATCH");
        return exposure(dao.get(Customer.class, customerId));
    }

    private BigDecimal exposure(Customer customer) {
        BillingLedger ledger = new BillingLedger(dao);
        BigDecimal result = ledger.sum("select sum(i.totalAmount-i.paidAmount-i.creditedAmount) from Invoice i "
                + "where i.customer=:customer and i.status<>'VOID'", WholesaleDao.params("customer", customer));
        List<Shipment> unbilled = dao.list("from Shipment s where s.order.customer=:customer "
                + "and s.status='CONFIRMED' and s.invoice is null", WholesaleDao.params("customer", customer));
        for (Shipment shipment : unbilled) {
            BillingAmounts original = new BillingAmounts(shipment.getOrder().getTaxRounding());
            BillingAmounts returned = new BillingAmounts(shipment.getOrder().getTaxRounding());
            for (ShipmentLine line : shipment.getLines()) {
                original.add(line.getNetAmount(), line.getTaxRate());
                // Receipt updates this count and issues its credit in the same transaction.
                returned.add(Money.amount(line.getUnitPrice(), line.getReturnedQuantity()), line.getTaxRate());
            }
            result = result.add(original.gross().subtract(returned.net())
                    .subtract(original.cumulativeCreditTax(returned)));
        }
        List<SalesOrder> approved = dao.list("from SalesOrder o where o.customer=:customer and "
                + "o.status in ('APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED')",
                WholesaleDao.params("customer", customer));
        for (SalesOrder order : approved) {
            BillingAmounts remaining = new BillingAmounts(order.getTaxRounding());
            for (SalesOrderLine line : order.getLines()) {
                Checks.state(line.getOpenQuantity() >= 0, "credit.quantity", "受注残数量が不正です。");
                remaining.add(Money.amount(line.getUnitPrice(), line.getOpenQuantity()), line.getTaxRate());
            }
            result = result.add(remaining.gross());
        }
        // Unallocated cash and excess credits are not a license to approve new credit sales.
        return result.max(Money.ZERO);
    }
}
