package jp.co.tsubame.wholesale.web.filter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.web.Web;
import org.apache.struts.chain.commands.InvalidPathException;

public final class SecurityFilter implements Filter {
    private static final Set<String> FIELDS = new HashSet<String>(Arrays.asList(
            "op", "id", "version", "text", "status", "customerId", "warehouseId", "from", "to",
            "pageNumber", "size", "reason", "csrfToken", "scoutTheme", "login", "password",
            "currentPassword", "newPassword", "confirmation", "code", "name", "active", "onHold",
            "creditLimit", "closingDay", "paymentTermDays", "taxRounding", "postalCode", "address",
            "telephone", "notes", "unit", "taxCategory", "listPrice", "standardCost", "packSize",
            "reorderPoint", "reorderQuantity", "productId", "validFrom", "validTo", "minimumQuantity",
            "unitPrice", "orderDate", "requestedDate", "deliveryAddress", "externalReference", "quantity",
            "priceOverride", "priceReason", "requestKey", "unitCost", "receiptDate", "reference", "note",
            "orderId", "plannedDate", "shippedDate", "carrier", "trackingNumber", "lineId", "shipmentId",
            "returnReason", "receivedDate", "restock", "periodEnd", "amount", "method", "invoiceId",
            "allocationId", "allocationVersion", "asOf", "sourceWarehouseId", "destinationWarehouseId",
            "quantityChange", "countedQuantity", "lineNote", "defaultLeadTimeDays", "minimumOrderAmount",
            "orderingInstructions", "supplierId", "supplierProductCode", "orderPackSize", "leadTimeDays",
            "preferred", "expectedDate", "lineExpectedDate", "supplierDeliveryNumber", "acceptedQuantity",
            "rejectedQuantity", "rejectionReason", "displayName", "selectedRole",
            "entityType", "entityId", "actorLogin", "operation", "supplierInvoiceNumber", "invoiceDate",
            "dueDate", "description", "taxRate", "invoiceLineId", "receiptLineId", "supplierCreditNumber",
            "creditDate", "netAmount", "paymentDate", "allocationAmount", "overdueOnly", "dimension", "exceptionsOnly",
            "plannedDispatchDate", "dispatchDate", "shipmentChoice", "stopNote", "trackingReference",
            "expectedLatestEventId", "targetEventId", "attemptAt", "outcome", "reportingCompany", "evidenceReference",
            "nextAttemptDate", "correctionReason", "asOfRecordedAt", "dueOnOrBefore",
            "quoteDate", "validUntil", "saveMode", "revisionNumber", "acceptedOn", "customerReference",
            "negotiatedUnitPrice", "negotiationReason", "expectedOrderVersion", "orderLineId", "targetQuantity"));
    public void init(FilterConfig config) { }
    public void destroy() { }

    public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
                + "img-src 'self'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; "
                + "base-uri 'self'; form-action 'self'; object-src 'none'");
        String correlation = UUID.randomUUID().toString();
        request.setAttribute("correlationId", correlation);
        response.setHeader("X-Request-ID", correlation);
        String path = request.getServletPath();
        if (path.startsWith("/assets/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "GET, POST");
            fail(request, response, 405, "このHTTPメソッドは使用できません。");
            return;
        }
        long maximumBody = "/quotations.do".equals(path) || "/apInvoices.do".equals(path) ? 1048576L : 262144L;
        if (request.getContentLengthLong() > maximumBody) {
            fail(request, response, 413, "送信内容が大きすぎます。明細数または入力文字数を減らしてください。");
            return;
        }
        Enumeration<String> names = request.getParameterNames();
        int totalCharacters = 0;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            // Reject nested/indexed BeanUtils paths before Struts population.
            String[] values = request.getParameterValues(name);
            int maximumValues = "/apMatches.do".equals(path) && arrayField(path, name) ? 500 : 100;
            if (("/quotations.do".equals(path) || "/orderAmendments.do".equals(path)
                    || "/apInvoices.do".equals(path) || "/apCredits.do".equals(path) || "/apPayments.do".equals(path))
                    && arrayField(path, name)) {
                maximumValues = 200;
            }
            if (!FIELDS.contains(name) || values == null || values.length > maximumValues
                    || (!arrayField(path, name) && values.length != 1)) {
                fail(request, response, 400, "入力項目の形式が不正です。");
                return;
            }
            for (String value : values) {
                totalCharacters += value.length();
                if (value.length() > 4000 || totalCharacters > 131072) {
                    fail(request, response, 413, "入力内容が大きすぎます。");
                    return;
                }
            }
        }
        HttpSession session = request.getSession(false);
        boolean publicLogin = "/login.do".equals(path) || "/".equals(path) || "/index.jsp".equals(path);
        if (publicLogin && session == null && "GET".equals(request.getMethod())) {
            session = request.getSession(true);
        }
        if ("POST".equals(request.getMethod())) {
            String[] supplied = request.getParameterValues(Web.TOKEN);
            if (session == null || supplied == null || supplied.length != 1
                    || !Web.sameToken((String) session.getAttribute(Web.TOKEN), supplied[0])) {
                fail(request, response, 403, "操作の有効期限が切れています。画面を開き直して再入力してください。");
                return;
            }
        }
        try {
            if (session != null && session.getAttribute(Web.USER_ID) != null) {
                try {
                    Actor actor = Web.bean(request.getServletContext(), "authService", AuthService.class)
                            .getCurrentActor((Long) session.getAttribute(Web.USER_ID));
                    request.setAttribute("actor", actor);
                    Web.roles(request, actor);
                } catch (BusinessException ex) {
                    session.invalidate();
                    fail(request, response, 403, "このアカウントは現在利用できません。管理者へお問い合わせください。");
                    return;
                }
            }
            if (!publicLogin && request.getAttribute("actor") == null) {
                if ("GET".equals(request.getMethod())) {
                    response.sendRedirect(request.getContextPath() + "/login.do");
                } else {
                    fail(request, response, 401, "ログインの有効期限が切れています。再度ログインしてください。");
                }
                return;
            }
            if (session != null) {
                request.setAttribute(Web.TOKEN, Web.token(session));
                Object flash = session.getAttribute("flash");
                if (flash != null && "GET".equals(request.getMethod())) {
                    request.setAttribute("flash", flash);
                    session.removeAttribute("flash");
                }
            }
            chain.doFilter(request, response);
        } catch (Exception ex) {
            if (path.endsWith(".do") && isUnmappedAction(ex) && !response.isCommitted()) {
                request.getServletContext().log("Unmapped Struts action [" + correlation + "] " + path);
                response.resetBuffer();
                fail(request, response, 404, "指定されたページが見つかりません。URLまたはメニューをご確認ください。");
                return;
            }
            request.getServletContext().log("Request failed [" + correlation + "] " + path, ex);
            if (response.isCommitted()) {
                throw new ServletException("Request failed [" + correlation + "]", ex);
            }
            response.resetBuffer();
            fail(request, response, 500, "処理を完了できませんでした。再実行の前に一覧で登録状況を確認し、"
                    + "解消しない場合は問い合わせ番号を管理者にお知らせください。");
        }
    }

    private static boolean isUnmappedAction(Exception error) {
        if (!(error instanceof ServletException)) { return false; }
        Throwable cause = error;
        IdentityHashMap<Throwable, Boolean> visited = new IdentityHashMap<Throwable, Boolean>();
        // Only unwrap servlet wrappers; unrelated application/DB exception chains stay 500.
        while (cause instanceof ServletException) {
            if (visited.put(cause, Boolean.TRUE) != null) { return false; }
            ServletException wrapper = (ServletException) cause;
            cause = wrapper.getRootCause();
            if (cause == null) { cause = wrapper.getCause(); }
        }
        return cause instanceof InvalidPathException;
    }

    static boolean arrayField(String path, String name) {
        if ("/quotations.do".equals(path)) {
            return "productId".equals(name) || "quantity".equals(name)
                    || "negotiatedUnitPrice".equals(name) || "negotiationReason".equals(name);
        }
        if ("/orderAmendments.do".equals(path)) { return "orderLineId".equals(name) || "targetQuantity".equals(name); }
        if ("/dispatchManifests.do".equals(path)) {
            return "shipmentChoice".equals(name) || "stopNote".equals(name)
                    || "shipmentId".equals(name) || "trackingReference".equals(name);
        }
        if ("/apInvoices.do".equals(path)) {
            return "productId".equals(name) || "quantity".equals(name) || "description".equals(name)
                    || "unitPrice".equals(name) || "taxRate".equals(name);
        }
        if ("/apMatches.do".equals(path)) { return "invoiceLineId".equals(name) || "receiptLineId".equals(name) || "quantity".equals(name); }
        if ("/apCredits.do".equals(path)) { return "invoiceLineId".equals(name) || "netAmount".equals(name); }
        if ("/apPayments.do".equals(path)) { return "invoiceId".equals(name) || "allocationAmount".equals(name); }
        if ("/users.do".equals(path)) { return "selectedRole".equals(name); }
        if ("/orders.do".equals(path)) {
            return "productId".equals(name) || "quantity".equals(name)
                    || "priceOverride".equals(name) || "priceReason".equals(name);
        }
        if ("/shipments.do".equals(path) || "/returns.do".equals(path)) {
            return "lineId".equals(name) || "quantity".equals(name)
                    || ("/returns.do".equals(path) && "restock".equals(name));
        }
        if ("/transfers.do".equals(path) || "/purchases.do".equals(path)) {
            return "productId".equals(name) || "quantity".equals(name)
                    || ("/purchases.do".equals(path) && ("lineExpectedDate".equals(name) || "lineNote".equals(name)));
        }
        if ("/counts.do".equals(path)) {
            return "productId".equals(name) || "lineId".equals(name)
                    || "countedQuantity".equals(name) || "lineNote".equals(name);
        }
        if ("/purchaseReceipts.do".equals(path)) {
            return "lineId".equals(name) || "acceptedQuantity".equals(name) || "rejectedQuantity".equals(name)
                    || "rejectionReason".equals(name) || "lineNote".equals(name);
        }
        return false;
    }

    private static void fail(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("pageTitle", "処理を確認してください");
        Web.error(request, null, message);
        request.getRequestDispatcher("/WEB-INF/jsp/error.jsp").forward(request, response);
    }
}
