package jp.co.tsubame.wholesale.web.action;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.nio.file.Paths;
import javax.servlet.ServletContext;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.CatalogForm;
import jp.co.tsubame.wholesale.web.form.LoginForm;
import jp.co.tsubame.wholesale.web.form.PasswordForm;
import org.apache.commons.validator.ValidatorResources;
import org.apache.struts.Globals;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionServlet;
import org.apache.struts.config.impl.ModuleConfigImpl;
import org.apache.struts.util.MessageResources;
import org.apache.struts.validator.ValidatorPlugIn;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class ValidatorAndLoginTest {
    @Test public void realValidationXmlRequiresLoginAndPassword() throws Exception {
        HttpFixture http = fixture();
        LoginForm form = new LoginForm(); form.setServlet(servlet(http));
        ActionErrors errors = form.validate(mapping("loginForm"), http.request);
        assertEquals(2, errors.size());
        form.setLogin("synthetic"); form.setPassword("not-a-real-credential");
        assertTrue(form.validate(mapping("loginForm"), http.request).isEmpty());
    }
    @Test public void selectedMasterAndPasswordFormsUseRealValidatorRules() throws Exception {
        HttpFixture http = fixture();
        CatalogForm catalog = new CatalogForm(); catalog.setServlet(servlet(http));
        assertEquals(2, catalog.validate(mapping("catalogForm"), http.request).size());
        catalog.setCode("C001"); catalog.setName("テスト得意先");
        assertTrue(catalog.validate(mapping("catalogForm"), http.request).isEmpty());
        PasswordForm password = new PasswordForm(); password.setServlet(servlet(http));
        assertEquals(3, password.validate(mapping("passwordForm"), http.request).size());
    }
    @Test public void validatorPasswordLimitMatchesCoreMaximum() throws Exception {
        HttpFixture http = fixture();
        char[] characters = new char[129]; Arrays.fill(characters, 'x');
        LoginForm login = new LoginForm(); login.setServlet(servlet(http)); login.setLogin("synthetic");
        login.setPassword(new String(characters));
        assertEquals(1, login.validate(mapping("loginForm"), http.request).size());
        login.setPassword(new String(characters, 0, 128));
        assertTrue(login.validate(mapping("loginForm"), http.request).isEmpty());
        PasswordForm change = new PasswordForm(); change.setServlet(servlet(http));
        change.setCurrentPassword(new String(characters)); change.setNewPassword(new String(characters));
        change.setConfirmation(new String(characters));
        assertEquals(3, change.validate(mapping("passwordForm"), http.request).size());
    }
    @Test public void successfulLoginInvalidatesOldSessionAndRotatesToken() throws Exception {
        HttpFixture http = fixture(); http.path = "/login.do"; http.method = "POST";
        String oldToken = Web.token(http.createSession());
        auth(http, new AuthService() {
            public AuthenticationResult authenticate(String login, char[] password) {
                Arrays.fill(password, '\0');
                return new AuthenticationResult(new Actor(42L, login, "検証用利用者", "SALES"), "");
            }
        });
        LoginForm form = new LoginForm(); form.setServlet(servlet(http));
        form.setOp("login"); form.setLogin("synthetic"); form.setPassword("validation-only");
        assertNull(new LoginAction().execute(mapping("loginForm"), form, http.request, http.response));
        assertTrue(http.invalidated); assertEquals(Long.valueOf(42), http.sessionAttributes.get(Web.USER_ID));
        assertFalse(Web.sameToken(oldToken, (String) http.sessionAttributes.get(Web.TOKEN)));
        assertEquals(303, http.status); assertEquals("", form.getPassword());
        assertEquals("/wholesale/dashboard.do", http.headers.get("Location"));
    }
    @Test public void authenticationFailureDoesNotBecomeSuccessOrRetainPassword() throws Exception {
        HttpFixture http = fixture(); http.method = "POST";
        auth(http, new AuthService() {
            public AuthenticationResult authenticate(String login, char[] password) {
                Arrays.fill(password, '\0'); return new AuthenticationResult(null, "認証できませんでした。");
            }
        });
        LoginForm form = new LoginForm(); form.setServlet(servlet(http)); form.setOp("login");
        form.setLogin("synthetic"); form.setPassword("invalid");
        ActionForward result = new LoginAction().execute(mapping("loginForm"), form, http.request, http.response);
        assertEquals(401, http.status); assertEquals("/WEB-INF/jsp/login.jsp", result.getPath());
        assertEquals("synthetic", form.getLogin()); assertEquals("", form.getPassword());
        assertNull(http.headers.get("Location"));
    }
    @Test public void loginGetDoesNotValidateEmptyForm() throws Exception {
        HttpFixture http = new HttpFixture();
        LoginForm form = new LoginForm();
        ActionForward result = new LoginAction().execute(mapping("loginForm"), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals("/WEB-INF/jsp/login.jsp", result.getPath());
    }
    private HttpFixture fixture() throws Exception {
        HttpFixture http = new HttpFixture();
        InputStream rules = new FileInputStream(Paths.get("src", "main", "webapp", "WEB-INF", "validator-rules.xml").toFile());
        InputStream validation = new FileInputStream(Paths.get("src", "main", "webapp", "WEB-INF", "validation.xml").toFile());
        try {
            http.contextAttributes.put(ValidatorPlugIn.VALIDATOR_KEY, new ValidatorResources(new InputStream[] {rules, validation}));
        } finally { rules.close(); validation.close(); }
        http.attributes.put(Globals.MODULE_KEY, new ModuleConfigImpl(""));
        http.contextAttributes.put(Globals.MODULE_KEY, new ModuleConfigImpl(""));
        MessageResources messages = MessageResources.getMessageResources("jp.co.tsubame.wholesale.web.Messages");
        http.attributes.put(Globals.MESSAGES_KEY, messages); http.contextAttributes.put(Globals.MESSAGES_KEY, messages);
        return http;
    }
    private ActionServlet servlet(final HttpFixture http) {
        return new ActionServlet() {
            private static final long serialVersionUID = 1L;
            public ServletContext getServletContext() { return http.context; }
        };
    }
    private ActionMapping mapping(String name) {
        ActionMapping mapping = new ActionMapping(); mapping.setPath("/login"); mapping.setName(name); return mapping;
    }
    private void auth(HttpFixture http, AuthService auth) {
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("authService", auth); context.refresh();
        http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    }
}
