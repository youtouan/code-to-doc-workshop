package jp.co.tsubame.wholesale.web.filter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.Web;
import org.apache.struts.chain.commands.InvalidPathException;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class SecurityFilterTest {
    @Test public void loginGetCreatesPreloginTokenAndSecurityHeaders() throws Exception {
        HttpFixture http = new HttpFixture(); http.path = "/login.do";
        assertEquals(1, run(http));
        assertNotNull(http.sessionAttributes.get(Web.TOKEN));
        assertEquals(http.sessionAttributes.get(Web.TOKEN), http.attributes.get(Web.TOKEN));
        assertEquals("UTF-8", http.requestEncoding);
        assertEquals("UTF-8", http.responseEncoding);
        assertEquals("DENY", http.headers.get("X-Frame-Options"));
        assertEquals("nosniff", http.headers.get("X-Content-Type-Options"));
        assertTrue(http.headers.get("Content-Security-Policy").contains("form-action 'self'"));
        assertTrue(http.headers.get("Cache-Control").contains("no-store"));
    }
    @Test public void loginPostRequiresExistingSessionAndValidToken() throws Exception {
        HttpFixture absent = new HttpFixture(); absent.path = "/login.do"; absent.method = "POST";
        assertEquals(0, run(absent)); assertEquals(403, absent.status);
        HttpFixture valid = new HttpFixture(); valid.path = "/login.do"; valid.method = "POST";
        valid.parameter("csrfToken", Web.token(valid.createSession()));
        assertEquals(1, run(valid)); assertEquals(200, valid.status);
    }
    @Test public void duplicateTokensAndScalarParametersAreRejected() throws Exception {
        HttpFixture http = new HttpFixture(); http.path = "/login.do"; http.method = "POST";
        String token = Web.token(http.createSession());
        http.parameter("csrfToken", token, token);
        assertEquals(0, run(http)); assertEquals(400, http.status);
        HttpFixture duplicatedOperation = new HttpFixture(); duplicatedOperation.path = "/login.do";
        duplicatedOperation.parameter("op", "list", "login");
        assertEquals(0, run(duplicatedOperation)); assertEquals(400, duplicatedOperation.status);
    }
    @Test public void unauthenticatedProtectedReadsRedirectAndWritesFail() throws Exception {
        HttpFixture get = new HttpFixture();
        assertEquals(0, run(get)); assertEquals("/wholesale/login.do", get.redirect);
        HttpFixture post = new HttpFixture(); post.method = "POST";
        post.parameter("csrfToken", Web.token(post.createSession()));
        assertEquals(0, run(post)); assertEquals(401, post.status);
    }
    @Test public void forgedNestedAndFrameworkPropertiesNeverReachStruts() throws Exception {
        for (String name : new String[] {"class", "class.classLoader", "product[0]", "servlet", "page", "validatorResults"}) {
            HttpFixture http = new HttpFixture(); http.path = "/login.do"; http.parameter(name, "bad");
            assertEquals(name, 0, run(http)); assertEquals(400, http.status);
        }
    }
    @Test public void oversizedPayloadAndUnsupportedMethodAreRejected() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "DELETE";
        assertEquals(0, run(http)); assertEquals(405, http.status);
        assertEquals("GET, POST", http.headers.get("Allow"));
        HttpFixture huge = new HttpFixture(); huge.contentLength = 262145;
        assertEquals(0, run(huge)); assertEquals(413, huge.status);
    }
    @Test public void actorIsRefreshedFromCoreOnEveryRequest() throws Exception {
        final AtomicInteger refreshed = new AtomicInteger();
        HttpFixture http = authenticated(new AuthService() {
            public Actor getCurrentActor(Long id) {
                refreshed.incrementAndGet(); return new Actor(id, "synthetic", "現在の権限", "WAREHOUSE");
            }
        });
        assertEquals(1, run(http));
        Actor actor = (Actor) http.attributes.get("actor");
        assertTrue(actor.hasRole("WAREHOUSE")); assertFalse(actor.hasRole("MANAGER"));
        assertEquals(Boolean.FALSE, http.attributes.get("canMANAGER"));
        assertEquals(1, run(http)); assertEquals(2, refreshed.get());
    }
    @Test public void disabledAccountInvalidatesSession() throws Exception {
        HttpFixture http = authenticated(new AuthService() {
            public Actor getCurrentActor(Long id) { throw new BusinessException("auth.disabled", "停止"); }
        });
        assertEquals(0, run(http)); assertEquals(403, http.status); assertTrue(http.invalidated);
    }
    @Test public void infrastructureFailuresAre500AndLoggedOnce() throws Exception {
        HttpFixture http = authenticated(new AuthService() {
            public Actor getCurrentActor(Long id) { throw new IllegalStateException("database unavailable"); }
        });
        assertEquals(0, run(http)); assertEquals(500, http.status); assertEquals(1, http.technicalLogs);
        assertEquals("/WEB-INF/jsp/error.jsp", http.forwarded);
        assertNotNull(http.attributes.get("correlationId"));
        assertFalse(http.attributes.toString().contains("database unavailable"));
    }
    @Test public void composableProcessorWrappedInvalidPathReturns404() throws Exception {
        HttpFixture http = routingFailure(new ServletException(
                new InvalidPathException("Internal unmapped action detail", "/missing-route-for-smoke")));
        assertEquals(404, http.status); assertEquals("/WEB-INF/jsp/error.jsp", http.forwarded);
        assertEquals(1, http.technicalLogs);
        assertNotNull(http.attributes.get("correlationId"));
        assertEquals("nosniff", http.headers.get("X-Content-Type-Options"));
        assertTrue(http.headers.get("Cache-Control").contains("no-store"));
    }
    @Test public void nestedServletWrappersAroundInvalidPathAlsoReturn404() throws Exception {
        HttpFixture http = routingFailure(new ServletException("outer", new ServletException(
                new InvalidPathException("Internal detail", "/missing-route-for-smoke"))));
        assertEquals(404, http.status);
    }
    @Test public void unrelatedServletAndDatabaseFailuresRemain500() throws Exception {
        ServletException[] failures = {
            new ServletException("Ordinary servlet failure"),
            new ServletException("InvalidPathException mentioned in a message"),
            new ServletException(new java.sql.SQLException("Database failure")),
            new ServletException(new IllegalStateException("Application wrapper",
                    new InvalidPathException("Not a direct servlet routing failure", "/missing-route-for-smoke")))
        };
        for (ServletException failure : failures) {
            HttpFixture http = routingFailure(failure);
            assertEquals(500, http.status); assertEquals(1, http.technicalLogs);
            assertEquals("/WEB-INF/jsp/error.jsp", http.forwarded);
        }
    }
    @Test public void cyclicServletCauseDoesNotLoopOrBecome404() throws Exception {
        HttpFixture http = routingFailure(new ServletException("cyclic") {
            private static final long serialVersionUID = 1L;
            public Throwable getRootCause() { return this; }
        });
        assertEquals(500, http.status); assertEquals(1, http.technicalLogs);
    }
    @Test public void logoutPostRequiresCsrfAndRefreshedIdentity() throws Exception {
        HttpFixture http = authenticated(new AuthService() {
            public Actor getCurrentActor(Long id) { return new Actor(id, "user", "User", "SALES"); }
        });
        http.method = "POST"; http.path = "/logout.do";
        http.parameter("op", "logout"); http.parameter("csrfToken", Web.token(http.request.getSession()));
        assertEquals(1, run(http));
        http.parameter("csrfToken", Web.randomToken());
        assertEquals(0, run(http)); assertEquals(403, http.status);
    }
    @Test public void arraysAreOnlyAllowedOnCorrespondingRoutes() {
        assertTrue(SecurityFilter.arrayField("/orders.do", "productId"));
        assertTrue(SecurityFilter.arrayField("/returns.do", "restock"));
        assertFalse(SecurityFilter.arrayField("/products.do", "productId"));
        assertFalse(SecurityFilter.arrayField("/orders.do", "id"));
        assertFalse(SecurityFilter.arrayField("/shipments.do", "restock"));
        assertTrue(SecurityFilter.arrayField("/users.do", "selectedRole"));
        assertFalse(SecurityFilter.arrayField("/login.do", "selectedRole"));
        assertFalse(SecurityFilter.arrayField("/audit.do", "entityId"));
        assertTrue(SecurityFilter.arrayField("/apInvoices.do", "unitPrice"));
        assertTrue(SecurityFilter.arrayField("/apMatches.do", "receiptLineId"));
        assertTrue(SecurityFilter.arrayField("/apCredits.do", "netAmount"));
        assertTrue(SecurityFilter.arrayField("/apPayments.do", "invoiceId"));
        assertFalse(SecurityFilter.arrayField("/apPayments.do", "amount"));
        assertFalse(SecurityFilter.arrayField("/operationsReports.do", "productId"));
        assertFalse(SecurityFilter.arrayField("/operationsReports.do", "dimension"));
        assertTrue(SecurityFilter.arrayField("/dispatchManifests.do", "shipmentChoice"));
        assertTrue(SecurityFilter.arrayField("/dispatchManifests.do", "trackingReference"));
        assertFalse(SecurityFilter.arrayField("/deliveryAttempts.do", "shipmentId"));
        assertFalse(SecurityFilter.arrayField("/deliveryAttempts.do", "expectedLatestEventId"));
        assertTrue(SecurityFilter.arrayField("/quotations.do", "negotiatedUnitPrice"));
        assertTrue(SecurityFilter.arrayField("/orderAmendments.do", "targetQuantity"));
        assertFalse(SecurityFilter.arrayField("/orders.do", "negotiatedUnitPrice"));
        assertFalse(SecurityFilter.arrayField("/orderAmendments.do", "expectedOrderVersion"));
    }
    @Test public void fiveHundredRowsAreAllowedOnlyForApMatchingArrays() throws Exception {
        String[] values = new String[500]; java.util.Arrays.fill(values, "1");
        HttpFixture matches = new HttpFixture(); matches.path = "/apMatches.do"; matches.parameter("receiptLineId", values);
        assertEquals(0, run(matches)); assertEquals(302, matches.status);
        assertEquals("/wholesale/login.do", matches.redirect);
        HttpFixture tooMany = new HttpFixture(); tooMany.path = "/apMatches.do";
        String[] overflow = new String[501]; java.util.Arrays.fill(overflow, "1"); tooMany.parameter("receiptLineId", overflow);
        assertEquals(0, run(tooMany)); assertEquals(400, tooMany.status);
        HttpFixture payments = new HttpFixture(); payments.path = "/apPayments.do"; payments.parameter("invoiceId", values);
        assertEquals(0, run(payments)); assertEquals(400, payments.status);
    }
    @Test public void quoteRowsHaveBoundedRoomForUtf8NegotiationReasons() throws Exception {
        String[] values = new String[200]; java.util.Arrays.fill(values, "1");
        HttpFixture quote = new HttpFixture(); quote.path = "/quotations.do"; quote.contentLength = 600000;
        quote.parameter("productId", values);
        assertEquals(0, run(quote)); assertEquals(302, quote.status);
        HttpFixture tooMany = new HttpFixture(); tooMany.path = "/quotations.do";
        String[] overflow = new String[201]; java.util.Arrays.fill(overflow, "1"); tooMany.parameter("productId", overflow);
        assertEquals(0, run(tooMany)); assertEquals(400, tooMany.status);
        HttpFixture oversized = new HttpFixture(); oversized.path = "/quotations.do"; oversized.contentLength = 1048577;
        assertEquals(0, run(oversized)); assertEquals(413, oversized.status);
    }
    @Test public void apInvoiceCreditAndPaymentArraysAllow200ButReject201() throws Exception {
        String[][] cases = {{"/apInvoices.do", "productId"}, {"/apCredits.do", "invoiceLineId"}, {"/apPayments.do", "invoiceId"}};
        for (String[] entry : cases) {
            String[] values = new String[200]; java.util.Arrays.fill(values, "1");
            HttpFixture allowed = new HttpFixture(); allowed.path = entry[0]; allowed.parameter(entry[1], values);
            if ("/apInvoices.do".equals(entry[0])) { allowed.contentLength = 600000; }
            assertEquals(0, run(allowed)); assertEquals(entry[0], 302, allowed.status);
            String[] overflow = new String[201]; java.util.Arrays.fill(overflow, "1");
            HttpFixture denied = new HttpFixture(); denied.path = entry[0]; denied.parameter(entry[1], overflow);
            assertEquals(0, run(denied)); assertEquals(entry[0], 400, denied.status);
        }
    }
    private HttpFixture authenticated(AuthService auth) {
        HttpFixture http = new HttpFixture();
        StaticWebApplicationContext context = new StaticWebApplicationContext();
        context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("authService", auth);
        context.refresh();
        http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        http.createSession().setAttribute(Web.USER_ID, Long.valueOf(9));
        return http;
    }
    private HttpFixture routingFailure(final ServletException failure) throws Exception {
        HttpFixture http = authenticated(new AuthService() {
            public Actor getCurrentActor(Long id) { return new Actor(id, "routing-test", "検証", "SALES"); }
        });
        http.path = "/missing-route-for-smoke.do";
        new SecurityFilter().doFilter(http.request, http.response, new FilterChain() {
            public void doFilter(ServletRequest request, ServletResponse response) throws ServletException { throw failure; }
        });
        return http;
    }
    private int run(HttpFixture http) throws IOException, ServletException {
        final AtomicInteger called = new AtomicInteger();
        new SecurityFilter().doFilter(http.request, http.response, new FilterChain() {
            public void doFilter(ServletRequest request, ServletResponse response) { called.incrementAndGet(); }
        });
        return called.get();
    }
}
