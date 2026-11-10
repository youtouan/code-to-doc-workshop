package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockCountLine;
import jp.co.tsubame.wholesale.entity.StockTransferLine;
import org.junit.Test;
import static org.junit.Assert.*;

public class StockControlRulesTest {
    @Test
    public void quantitiesAreSortedForConsistentLockOrder() {
        Map<Long, Integer> lines = StockControlRules.quantities(Arrays.asList(
                new StockControlLineCommand(22L, 3), new StockControlLineCommand(11L, 8)));
        assertEquals(Arrays.asList(11L, 22L), Arrays.asList(lines.keySet().toArray(new Long[0])));
        assertEquals(Integer.valueOf(8), lines.get(11L));
    }

    @Test
    public void duplicateProductCannotBypassPerLineBounds() {
        expect("stockControl.duplicateProduct", new Runnable() {
            @Override public void run() {
                StockControlRules.quantities(Arrays.asList(
                        new StockControlLineCommand(11L, 1000000), new StockControlLineCommand(11L, 1)));
            }
        });
    }

    @Test
    public void zeroNegativeAndExcessiveTransferQuantitiesAreRejected() {
        for (final int quantity : new int[] {0, -1, 1000001, Integer.MAX_VALUE}) {
            expect("validation.quantity", new Runnable() {
                @Override public void run() {
                    StockControlRules.quantities(Arrays.asList(new StockControlLineCommand(11L, quantity)));
                }
            });
        }
    }

    @Test
    public void adjustmentBoundsDoNotOverflowOnMinimumInteger() {
        assertEquals(-1000000, StockControlRules.adjustment(-1000000));
        assertEquals(1000000, StockControlRules.adjustment(1000000));
        for (final int quantity : new int[] {0, -1000001, 1000001, Integer.MIN_VALUE}) {
            expect("stockControl.adjustmentQuantity", new Runnable() {
                @Override public void run() { StockControlRules.adjustment(quantity); }
            });
        }
    }

    @Test
    public void uncountedAndExplicitZeroAreDifferentStates() {
        StockBalance balance = new StockBalance();
        balance.setProduct(new Product());
        StockCountLine line = new StockCountLine();
        line.setBalance(balance);
        line.setSnapshotOnHand(15);
        line.setUnitCost(new BigDecimal("12.50"));
        assertNull(line.getCountedQuantity());
        assertNull(line.getDifference());
        assertNull(line.getDifferenceValue());
        line.setCountedQuantity(0);
        assertEquals(Integer.valueOf(-15), line.getDifference());
        assertEquals(new BigDecimal("-187.50"), line.getDifferenceValue());
        expect("stockControl.countQuantity", new Runnable() {
            @Override public void run() { StockControlRules.counted(null); }
        });
        assertEquals(0, StockControlRules.counted(0));
    }

    @Test
    public void countsAcceptThePhysicalLedgerRangeWithoutIntegerOverflow() {
        assertEquals(2000000000, StockControlRules.counted(2000000000));
        for (final int quantity : new int[] {-1, Integer.MAX_VALUE}) {
            expect("stockControl.countQuantity", new Runnable() {
                @Override public void run() { StockControlRules.counted(quantity); }
            });
        }
    }

    @Test
    public void approvalSeparationChecksIdentityAndLoginEvenForAdmin() {
        expect("approval.self", new Runnable() {
            @Override public void run() {
                StockControlRules.independent(new Actor(7L, "renamed", "", "ADMIN"), 7L, "original");
            }
        });
        expect("approval.self", new Runnable() {
            @Override public void run() {
                StockControlRules.independent(new Actor(8L, "original", "", "MANAGER"), 7L, "original");
            }
        });
        StockControlRules.independent(new Actor(8L, "reviewer", "", "MANAGER"), 7L, "original");
    }

    @Test
    public void receiptFingerprintIsOrderIndependentButNotDocumentOrKindIndependent() {
        Map<Long, Integer> first = StockControlRules.quantities(Arrays.asList(
                new StockControlLineCommand(11L, 1), new StockControlLineCommand(12L, 2)));
        Map<Long, Integer> second = StockControlRules.quantities(Arrays.asList(
                new StockControlLineCommand(12L, 2), new StockControlLineCommand(11L, 1)));
        String fingerprint = StockControlRules.fingerprint(1L, "RECEIPT", "note", first);
        assertEquals(fingerprint, StockControlRules.fingerprint(1L, "RECEIPT", "note", second));
        assertFalse(fingerprint.equals(StockControlRules.fingerprint(2L, "RECEIPT", "note", first)));
        assertFalse(fingerprint.equals(StockControlRules.fingerprint(1L, "LOSS", "note", first)));
        assertFalse(fingerprint.equals(StockControlRules.fingerprint(1L, "RECEIPT", "other", first)));
    }

    @Test
    public void transitValueExcludesAcknowledgedReceiptsAndLosses() {
        StockTransferLine line = new StockTransferLine();
        line.setQuantity(20);
        line.setDispatchedQuantity(20);
        line.setReceivedQuantity(5);
        line.setLostQuantity(2);
        line.setUnitCost(new BigDecimal("100.25"));
        assertEquals(13, line.getInTransitQuantity());
        assertEquals(new BigDecimal("1303.25"), line.getInTransitValue());
        assertEquals(new BigDecimal("200.50"), line.getLostValue());
        assertEquals("STANDARD_COST_AT_DISPATCH", line.getCostBasis());
    }

    private void expect(String code, Runnable action) {
        try {
            action.run();
            fail("Expected " + code);
        } catch (BusinessException failure) {
            assertEquals(code, failure.getCode());
        }
    }
}
