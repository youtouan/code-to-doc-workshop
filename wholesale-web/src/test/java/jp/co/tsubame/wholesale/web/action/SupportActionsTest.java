package jp.co.tsubame.wholesale.web.action;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.UserSummary;
import jp.co.tsubame.wholesale.entity.AppUser;
import jp.co.tsubame.wholesale.entity.AuditEvent;
import jp.co.tsubame.wholesale.service.AuditService;
import jp.co.tsubame.wholesale.service.UserAdminService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.AuditForm;
import jp.co.tsubame.wholesale.web.form.UserForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class SupportActionsTest {
    private static final List<String> ROLES = Arrays.asList("SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH", "ADMIN");

    @Test public void nonAdminCannotReadOrMutateUsers() throws Exception {
        for (String op : new String[] {"list", "new", "edit", "detail", "create", "update", "unlock", "resetPassword"}) {
            HttpFixture http = actor("MANAGER");
            http.method = new UserAction().isMutation(op) ? "POST" : "GET";
            UserForm form = new UserForm(); form.setOp(op); form.setNewPassword("must-be-cleared");
            ActionForward forward = new UserAction().execute(mapping("/users"), form, http.request, http.response);
            assertEquals(op, 403, http.status);
            assertEquals("/WEB-INF/jsp/error.jsp", forward.getPath());
            assertEquals("", form.getNewPassword());
        }
    }
    @Test public void auditRequiresManagerEvenForDirectDetailRequests() throws Exception {
        HttpFixture http = actor("SALES");
        AuditForm form = new AuditForm(); form.setOp("detail"); form.setId("12");
        new AuditAction().execute(mapping("/audit"), form, http.request, http.response);
        assertEquals(403, http.status);
    }
    @Test public void auditFiltersAndPaginationUseTypedServiceContract() throws Exception {
        HttpFixture http = actor("MANAGER");
        AuditService service = new AuditService() {
            public Page<AuditEvent> searchEvents(String type, Long entityId, String login, Search search, Actor actor) {
                assertEquals("AppUser", type); assertEquals(Long.valueOf(12), entityId); assertEquals("admin", login);
                assertEquals("USER_UPDATE", search.getStatus()); assertEquals(2, search.getPage());
                assertEquals("権限", search.getText());
                return new Page<AuditEvent>(Collections.<AuditEvent>emptyList(), 60, search.getPage(), search.getSize());
            }
        };
        service(http, "auditService", service);
        AuditForm form = new AuditForm(); form.setEntityType("AppUser"); form.setEntityId("12");
        form.setActorLogin("admin"); form.setOperation("USER_UPDATE"); form.setPageNumber("2"); form.setText("権限");
        ActionForward forward = new AuditAction().execute(mapping("/audit"), form, http.request, http.response);
        assertEquals("/WEB-INF/jsp/support/audit.jsp", forward.getPath());
        @SuppressWarnings("unchecked")
        Map<String, String> extras = (Map<String, String>) http.attributes.get("extraPageParameters");
        assertEquals("12", extras.get("entityId")); assertEquals("USER_UPDATE", extras.get("operation"));
    }
    @Test public void invalidAuditIdRedisplaysFiltersWithoutQuery() throws Exception {
        HttpFixture http = actor("MANAGER");
        service(http, "auditService", new AuditService() {
            public Page<AuditEvent> searchEvents(String type, Long id, String login, Search search, Actor actor) {
                fail("Invalid ID must not reach core"); return null;
            }
        });
        AuditForm form = new AuditForm(); form.setEntityId("<script>");
        ActionForward result = new AuditAction().execute(mapping("/audit"), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/support/audit.jsp", result.getPath());
        assertEquals("<script>", form.getEntityId());
    }
    @Test public void roleSelectionRejectsCsvInjectionDuplicatesAndUnknownValues() {
        assertEquals("SALES,ADMIN", UserAction.roleCsv(new String[] {"ADMIN", "SALES"}, ROLES));
        for (String[] values : new String[][] {{}, {"SALES,ADMIN"}, {"SALES", "SALES"}, {"ROOT"}, {"<script>"}}) {
            try { UserAction.roleCsv(values, ROLES); fail("Invalid role selection accepted"); }
            catch (BusinessException expected) { assertEquals("権限", expected.getField()); }
        }
    }
    @Test public void userCreationCallsCoreAndWipesPasswordBufferAndForm() throws Exception {
        HttpFixture http = actor("ADMIN"); http.method = "POST";
        final AtomicReference<char[]> sent = new AtomicReference<char[]>();
        service(http, "userAdminService", new UserAdminService() {
            public List<String> listRoles(Actor actor) { return ROLES; }
            public UserSummary createUser(String login, String displayName, String roles, char[] password, Actor actor) {
                assertEquals("synthetic-user", login); assertEquals("登録テスト", displayName);
                assertEquals("SALES,WAREHOUSE", roles); sent.set(password); return summary(12);
            }
        });
        UserForm form = createForm();
        assertNull(new UserAction().execute(mapping("/users"), form, http.request, http.response));
        assertEquals(303, http.status); assertEquals("/wholesale/users.do?op=detail&id=12", http.headers.get("Location"));
        assertEquals("", form.getNewPassword()); assertEquals("", form.getConfirmation());
        for (char value : sent.get()) { assertEquals('\0', value); }
    }
    @Test public void duplicateUserKeepsNonSecretInputAndReferences() throws Exception {
        HttpFixture http = actor("ADMIN"); http.method = "POST";
        service(http, "userAdminService", new UserAdminService() {
            public List<String> listRoles(Actor actor) { return ROLES; }
            public UserSummary createUser(String login, String displayName, String roles, char[] password, Actor actor) {
                throw new BusinessException("user.duplicate", "同じ利用者IDがあります。");
            }
        });
        UserForm form = createForm();
        ActionForward forward = new UserAction().execute(mapping("/users"), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/support/user-edit.jsp", forward.getPath());
        assertEquals("登録テスト", form.getDisplayName()); assertEquals(2, form.getSelectedRole().length);
        assertEquals(ROLES, http.attributes.get("availableRoles")); assertEquals("", form.getNewPassword());
        assertNull(http.headers.get("Location"));
    }
    @Test public void mismatchedPasswordNeverCallsMutation() throws Exception {
        HttpFixture http = actor("ADMIN"); http.method = "POST";
        final AtomicInteger writes = new AtomicInteger();
        service(http, "userAdminService", new UserAdminService() {
            public List<String> listRoles(Actor actor) { return ROLES; }
            public UserSummary createUser(String login, String name, String roles, char[] password, Actor actor) {
                writes.incrementAndGet(); return summary(12);
            }
        });
        UserForm form = createForm(); form.setConfirmation("different");
        new UserAction().execute(mapping("/users"), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals(0, writes.get()); assertEquals("", form.getConfirmation());
    }
    @Test public void adminPasswordLongerThan128NeverCallsMutation() throws Exception {
        HttpFixture http = actor("ADMIN"); http.method = "POST";
        service(http, "userAdminService", new UserAdminService() {
            public List<String> listRoles(Actor actor) { return ROLES; }
            public UserSummary createUser(String login, String name, String roles, char[] password, Actor actor) {
                fail("Oversized password must be rejected before service"); return null;
            }
        });
        char[] characters = new char[129]; Arrays.fill(characters, 'x');
        UserForm form = createForm(); form.setNewPassword(new String(characters)); form.setConfirmation(new String(characters));
        new UserAction().execute(mapping("/users"), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("", form.getNewPassword()); assertEquals("", form.getConfirmation());
    }
    @Test public void userUpdateDoesNotBindPostedLoginOrPassword() throws Exception {
        HttpFixture http = actor("ADMIN"); http.method = "POST";
        service(http, "userAdminService", new UserAdminService() {
            public List<String> listRoles(Actor actor) { return ROLES; }
            public UserSummary updateUser(Long id, int version, String displayName, String roles, boolean active, Actor actor) {
                assertEquals(Long.valueOf(12), id); assertEquals(3, version); assertFalse(active);
                assertEquals("WAREHOUSE", roles); assertEquals("変更テスト", displayName); return summary(12);
            }
        });
        UserForm form = new UserForm(); form.setOp("update"); form.setId("12"); form.setVersion("3");
        form.setLogin("cannot-change-login"); form.setDisplayName("変更テスト");
        form.setSelectedRole(new String[] {"WAREHOUSE"}); form.setActive("false"); form.setNewPassword("ignored-secret");
        new UserAction().execute(mapping("/users"), form, http.request, http.response);
        assertEquals(303, http.status); assertEquals("", form.getNewPassword());
    }
    @Test public void resetPasswordUsesSelectedUserVersionAndWipesBuffer() throws Exception {
        HttpFixture http = actor("ADMIN"); http.method = "POST";
        final AtomicReference<char[]> sent = new AtomicReference<char[]>();
        service(http, "userAdminService", new UserAdminService() {
            public UserSummary resetPassword(Long id, int version, char[] password, Actor actor) {
                assertEquals(Long.valueOf(12), id); assertEquals(4, version); sent.set(password); return summary(12);
            }
        });
        UserForm form = createForm(); form.setOp("resetPassword"); form.setId("12"); form.setVersion("4");
        new UserAction().execute(mapping("/users"), form, http.request, http.response);
        assertEquals(303, http.status);
        for (char value : sent.get()) { assertEquals('\0', value); }
        assertEquals("", form.getNewPassword());
    }
    private UserForm createForm() {
        UserForm form = new UserForm(); form.setOp("create"); form.setLogin("synthetic-user");
        form.setDisplayName("登録テスト"); form.setSelectedRole(new String[] {"WAREHOUSE", "SALES"});
        form.setNewPassword("test-only-value"); form.setConfirmation("test-only-value"); return form;
    }
    private static UserSummary summary(long id) {
        AppUser user = new AppUser(); user.setId(Long.valueOf(id)); user.setLogin("synthetic-user");
        user.setDisplayName("登録テスト"); user.setRoles("SALES"); user.setActive(true); return new UserSummary(user);
    }
    private HttpFixture actor(String role) {
        HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(99L, "test-actor", "検証", role)); return http;
    }
    private ActionMapping mapping(String path) { ActionMapping mapping = new ActionMapping(); mapping.setPath(path); return mapping; }
    private void service(HttpFixture http, String name, Object bean) {
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton(name, bean); context.refresh();
        http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    }
}
