package jp.co.tsubame.wholesale.web;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import org.springframework.web.context.support.WebApplicationContextUtils;

public final class Web {
    public static final String USER_ID = "authenticatedUserId";
    public static final String TOKEN = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Web() { }

    public static <T> T bean(ServletContext context, String name, Class<T> type) {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(context).getBean(name, type);
    }

    public static Actor actor(HttpServletRequest request) {
        Actor actor = (Actor) request.getAttribute("actor");
        if (actor == null) {
            throw new BusinessException("permission.denied", "ログインしてください。");
        }
        return actor;
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    public static String token(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(TOKEN);
            if (token == null) {
                token = randomToken();
                session.setAttribute(TOKEN, token);
            }
            return token;
        }
    }

    public static boolean sameToken(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != 64 || actual.length() != 64) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < 64; i++) {
            difference |= expected.charAt(i) ^ actual.charAt(i);
        }
        return difference == 0;
    }

    public static void roles(HttpServletRequest request, Actor actor) {
        String[] names = {"SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH", "ADMIN"};
        for (String role : names) {
            request.setAttribute("can" + role, Boolean.valueOf(actor.hasRole("ADMIN") || actor.hasRole(role)));
        }
    }

    public static void error(HttpServletRequest request, String field, String message) {
        @SuppressWarnings("unchecked")
        List<FieldError> errors = (List<FieldError>) request.getAttribute("fieldErrors");
        if (errors == null) {
            errors = new ArrayList<FieldError>();
            request.setAttribute("fieldErrors", errors);
        }
        errors.add(new FieldError(field, message));
    }

    public static final class FieldError {
        private final String field;
        private final String message;
        public FieldError(String field, String message) { this.field = field; this.message = message; }
        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}
