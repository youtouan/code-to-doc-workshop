package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.OrderForm;
import jp.co.tsubame.wholesale.web.form.ReturnForm;
import jp.co.tsubame.wholesale.web.form.ShipmentForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActionContractTest {
    @Test public void everyMutationRejectsForgedGetBeforeServicesAreLookedUp() throws Exception {
        assertPost(new LoginAction(), "login");
        assertPost(new LogoutAction(), "logout");
        assertPost(new PasswordAction(), "change");
        assertPost(new CatalogAction(), "save");
        assertPost(new PriceAction(), "save", "delete");
        assertPost(new OrderAction(), "save", "addLine", "submit", "approve", "reject", "withdraw", "cancel", "allocate", "release", "reschedule", "copy");
        assertPost(new ReceiptAction(), "receive");
        assertPost(new ShipmentAction(), "instruct", "confirm", "cancel");
        assertPost(new ReturnAction(), "request", "approve", "reject", "cancel", "receive");
        assertPost(new InvoiceAction(), "prepare", "finalize", "cancel");
        assertPost(new PaymentAction(), "receive", "allocate", "reverse", "cancel");
        assertPost(new TransferAction(), "save", "addLine", "submit", "approve", "dispatch", "receive", "loss", "cancel");
        assertPost(new AdjustmentAction(), "propose", "approve", "reject", "cancel");
        assertPost(new CountAction(), "begin", "record", "review", "reopen", "approve", "cancel");
        assertPost(new SupplierAction(), "save");
        assertPost(new SupplierProductAction(), "save");
        assertPost(new PurchaseAction(), "save", "addLine", "submit", "approve", "reject", "cancel", "close");
        assertPost(new PurchaseReceiptAction(), "receive");
        assertPost(new UserAction(), "create", "update", "unlock", "resetPassword");
        assertPost(new APInvoiceAction(), "save", "addLine", "approveVariance", "post", "cancel");
        assertPost(new APMatchAction(), "save", "clear", "addLine");
        assertPost(new APCreditAction(), "propose", "approve", "cancel");
        assertPost(new APPaymentAction(), "pay", "addLine", "cancel");
        assertPost(new DispatchAction(), "save", "addLine", "release", "replan", "cancel", "confirm");
        assertPost(new DeliveryAction(), "record", "editCorrection", "correct", "reverse");
        assertPost(new QuotationAction(), "save", "revise", "addLine", "submit", "approve", "withdraw", "reject", "cancel", "accept", "expire", "convert");
        assertPost(new OrderAmendmentAction(), "request", "approve", "reject", "cancel");
    }
    @Test public void unknownOperationIs400AndDoesNotDispatchReflectively() throws Exception {
        HttpFixture http = new HttpFixture(); BaseForm form = new BaseForm(); form.setOp("getClass");
        ActionForward forward = new OrderAction().execute(mapping(), form, http.request, http.response);
        assertEquals(400, http.status); assertEquals("/WEB-INF/jsp/error.jsp", forward.getPath());
    }
    @Test public void postCannotInvokeSafeDisplayBranch() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        BaseForm form = new BaseForm();
        new StockAction().execute(mapping(), form, http.request, http.response);
        assertEquals(405, http.status); assertEquals("GET", http.headers.get("Allow"));
    }
    @Test public void businessFailuresKeepUserInputAndUse422() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        BaseForm form = new BaseForm(); form.setOp("save"); form.setReason("<b>user input</b>");
        BaseAction action = new ProbeAction("validation.bad");
        ActionForward result = action.execute(mapping(), form, http.request, http.response);
        assertEquals(422, http.status); assertSame(form, http.attributes.get("form"));
        assertEquals("<b>user input</b>", form.getReason());
        assertEquals("/WEB-INF/jsp/edit.jsp", result.getPath());
        assertEquals(Boolean.TRUE, http.attributes.get("failed"));
    }
    @Test public void deniedPermissionsAndVersionConflictsHaveProperStatus() throws Exception {
        HttpFixture denied = new HttpFixture(); denied.method = "POST";
        BaseForm form = new BaseForm(); form.setOp("save");
        new ProbeAction("permission.denied").execute(mapping(), form, denied.request, denied.response);
        assertEquals(403, denied.status);
        HttpFixture stale = new HttpFixture(); stale.method = "POST";
        new ProbeAction("concurrency.version").execute(mapping(), form, stale.request, stale.response);
        assertEquals(409, stale.status);
    }
    @Test public void actualCoreVersionCodeAndAmendmentSnapshotConflictAre409() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        BaseForm form = new BaseForm(); form.setOp("save");
        BaseAction action = new ProbeAction("unused") {
            protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request, HttpServletResponse response) {
                jp.co.tsubame.wholesale.common.Checks.version(2, 1); return null;
            }
        };
        action.execute(mapping(), form, http.request, http.response);
        assertEquals(409, http.status);
        HttpFixture stale = new HttpFixture(); stale.method = "POST";
        new ProbeAction("amendment.stale").execute(mapping(), form, stale.request, stale.response);
        assertEquals(409, stale.status);
    }
    @Test public void invalidIdentityDuringRedisplayDoesNotBecome500() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        BaseForm form = new BaseForm(); form.setOp("save"); form.setId("not-an-id");
        BaseAction action = new ProbeAction("validation.id") {
            protected ActionForward failure(BaseForm form, HttpServletRequest request) {
                throw new BusinessException("validation.id", "IDが不正です。");
            }
        };
        assertEquals("/WEB-INF/jsp/error.jsp", action.execute(mapping(), form, http.request, http.response).getPath());
        assertEquals(422, http.status);
    }
    @Test public void salesDraftDoesNotSubmitCatalogPricesAsOverrides() {
        HttpFixture http = sales();
        OrderForm form = order();
        OrderInput input = new OrderAction().input(form, http.request);
        assertEquals(1, input.getLines().size()); assertNull(input.getLines().get(0).getPriceOverride());
        assertEquals(12, input.getLines().get(0).getQuantity());
        form.setPriceOverride(new String[] {"100", ""});
        try { new OrderAction().input(form, http.request); fail("Sales cannot override price"); }
        catch (BusinessException expected) { assertEquals("permission.denied", expected.getCode()); }
    }
    @Test public void managerCanExplicitlyNegotiateAPrice() {
        HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(2L, "manager", "管理", "MANAGER"));
        OrderForm form = order(); form.setPriceOverride(new String[] {"100.00", ""}); form.setPriceReason(new String[] {"承認済の個別価格", ""});
        assertEquals("100.00", new OrderAction().input(form, http.request).getLines().get(0).getPriceOverride().toPlainString());
    }
    @Test public void shippingZeroQuantitiesAreExcludedBeforeDtoConstruction() {
        ShipmentForm form = new ShipmentForm(); form.setLineId(new String[] {"1", "2", "3"});
        form.setQuantity(new String[] {"0", "", "12"});
        assertEquals(1, ShipmentAction.lines(form).size());
        assertEquals(Long.valueOf(3), ShipmentAction.lines(form).get(0).getOrderLineId());
        form.setQuantity(new String[] {"0", "", "0"});
        try { ShipmentAction.lines(form); fail("Empty shipment must fail"); }
        catch (BusinessException expected) { assertEquals("明細", expected.getField()); }
    }
    @Test public void returnDamagedGoodsCannotBeRestocked() {
        ReturnForm form = new ReturnForm(); form.setLineId(new String[] {"1"}); form.setQuantity(new String[] {"1"});
        form.setRestock(new String[] {"true"}); form.setReturnReason("DAMAGED");
        try { ReturnAction.lines(form); fail("Damaged stock must not be restocked"); }
        catch (BusinessException expected) { assertEquals("在庫復帰", expected.getField()); }
        form.setRestock(new String[] {"false"});
        assertEquals(1, ReturnAction.lines(form).size());
        assertFalse(ReturnAction.lines(form).get(0).isRestock());
    }
    @Test public void logoutInvalidatesSessionAndUses303() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        Web.token(http.createSession()); BaseForm form = new BaseForm(); form.setOp("logout");
        assertNull(new LogoutAction().execute(mapping(), form, http.request, http.response));
        assertTrue(http.invalidated); assertEquals(303, http.status);
        assertEquals("/wholesale/login.do", http.headers.get("Location"));
    }
    private void assertPost(BaseAction action, String... operations) throws Exception {
        for (String operation : operations) {
            HttpFixture http = new HttpFixture(); BaseForm form = new BaseForm(); form.setOp(operation);
            ActionForward result = action.execute(mapping(), form, http.request, http.response);
            assertEquals(action.getClass().getSimpleName() + ":" + operation, 405, http.status);
            assertEquals("POST", http.headers.get("Allow"));
            assertEquals("/WEB-INF/jsp/error.jsp", result.getPath());
        }
    }
    private static ActionMapping mapping() { ActionMapping mapping = new ActionMapping(); mapping.setPath("/test"); return mapping; }
    private HttpFixture sales() {
        HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(1L, "sales", "営業", "SALES")); return http;
    }
    private OrderForm order() {
        OrderForm form = new OrderForm(); form.setCustomerId("1"); form.setWarehouseId("2");
        form.setOrderDate("2026-09-01"); form.setRequestedDate("2026-09-10");
        form.setProductId(new String[] {"10", ""}); form.setQuantity(new String[] {"12", ""});
        form.setPriceOverride(new String[] {"", ""}); form.setPriceReason(new String[] {"", ""});
        return form;
    }
    private static class ProbeAction extends BaseAction {
        private final String code;
        ProbeAction(String code) { this.code = code; }
        protected boolean isMutation(String op) { return "save".equals(op); }
        protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request, HttpServletResponse response) {
            throw new BusinessException(code, "入力", "業務上の確認が必要です。");
        }
        protected ActionForward failure(BaseForm form, HttpServletRequest request) { return view(request, "edit", "編集"); }
    }
}
