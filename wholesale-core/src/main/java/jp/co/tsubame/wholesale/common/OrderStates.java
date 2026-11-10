package jp.co.tsubame.wholesale.common;

import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;

public final class OrderStates {
    private OrderStates() {
    }

    public static boolean isApproved(String state) {
        return "APPROVED".equals(state) || "PART_ALLOCATED".equals(state)
                || "ALLOCATED".equals(state) || "PART_SHIPPED".equals(state);
    }

    public static void updateFulfilment(SalesOrder order) {
        int open = 0;
        int allocated = 0;
        int shipped = 0;
        int cancelled = 0;
        for (SalesOrderLine line : order.getLines()) {
            open += line.getOpenQuantity();
            allocated += line.getAllocatedQuantity();
            shipped += line.getShippedQuantity();
            cancelled += line.getCancelledQuantity();
        }
        if (open == 0) {
            order.setStatus(cancelled > 0 ? (shipped > 0 ? "CLOSED_PARTIAL" : "CANCELLED") : "SHIPPED");
        } else if (shipped > 0) {
            order.setStatus("PART_SHIPPED");
        } else if (allocated == open) {
            order.setStatus("ALLOCATED");
        } else if (allocated > 0) {
            order.setStatus("PART_ALLOCATED");
        } else {
            order.setStatus("APPROVED");
        }
    }
}
