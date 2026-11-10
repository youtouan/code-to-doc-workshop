package jp.co.tsubame.wholesale.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/** Small Servlet API test double; production code always uses container objects. */
public final class HttpFixture {
    public final Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    public final Map<String, Object> contextAttributes = new LinkedHashMap<String, Object>();
    public final Map<String, String[]> parameters = new LinkedHashMap<String, String[]>();
    public final Map<String, String> headers = new LinkedHashMap<String, String>();
    public String method = "GET";
    public String path = "/orders.do";
    public String requestEncoding;
    public String responseEncoding;
    public String forwarded;
    public String redirect;
    public int status = 200;
    public int technicalLogs;
    public long contentLength;
    public boolean invalidated;
    public Map<String, Object> sessionAttributes;
    public final ServletContext context;
    public final HttpServletRequest request;
    public final HttpServletResponse response;
    private HttpSession session;
    private final StringWriter body = new StringWriter();

    public HttpFixture() {
        context = proxy(ServletContext.class, new InvocationHandler() {
            public Object invoke(Object target, Method call, Object[] args) {
                String name = call.getName();
                if ("getAttribute".equals(name)) { return contextAttributes.get(args[0]); }
                if ("setAttribute".equals(name)) { contextAttributes.put((String) args[0], args[1]); return null; }
                if ("removeAttribute".equals(name)) { contextAttributes.remove(args[0]); return null; }
                if ("getAttributeNames".equals(name)) { return Collections.enumeration(contextAttributes.keySet()); }
                if ("getInitParameterNames".equals(name)) { return Collections.enumeration(Collections.<String>emptyList()); }
                if ("getContextPath".equals(name)) { return "/wholesale"; }
                if ("getClassLoader".equals(name)) { return HttpFixture.class.getClassLoader(); }
                if ("log".equals(name)) { technicalLogs++; return null; }
                return defaultValue(call.getReturnType());
            }
        });
        request = proxy(HttpServletRequest.class, new InvocationHandler() {
            public Object invoke(Object target, Method call, Object[] args) {
                String name = call.getName();
                if ("getMethod".equals(name)) { return method; }
                if ("getServletPath".equals(name)) { return path; }
                if ("getContextPath".equals(name)) { return "/wholesale"; }
                if ("getRequestURI".equals(name)) { return "/wholesale" + path; }
                if ("getServletContext".equals(name)) { return context; }
                if ("getContentLengthLong".equals(name)) { return Long.valueOf(contentLength); }
                if ("getContentLength".equals(name)) { return Integer.valueOf((int) contentLength); }
                if ("setCharacterEncoding".equals(name)) { requestEncoding = (String) args[0]; return null; }
                if ("getCharacterEncoding".equals(name)) { return requestEncoding; }
                if ("getParameterNames".equals(name)) { return Collections.enumeration(parameters.keySet()); }
                if ("getParameterMap".equals(name)) { return parameters; }
                if ("getParameterValues".equals(name)) { return parameters.get(args[0]); }
                if ("getParameter".equals(name)) {
                    String[] values = parameters.get(args[0]); return values == null ? null : values[0];
                }
                if ("getAttribute".equals(name)) { return attributes.get(args[0]); }
                if ("setAttribute".equals(name)) { attributes.put((String) args[0], args[1]); return null; }
                if ("removeAttribute".equals(name)) { attributes.remove(args[0]); return null; }
                if ("getAttributeNames".equals(name)) { return Collections.enumeration(attributes.keySet()); }
                if ("getSession".equals(name)) {
                    boolean create = args == null || args.length == 0 || Boolean.TRUE.equals(args[0]);
                    if (session == null && create) { createSession(); }
                    return session;
                }
                if ("getRequestDispatcher".equals(name)) {
                    final String location = (String) args[0];
                    return proxy(RequestDispatcher.class, new InvocationHandler() {
                        public Object invoke(Object target, Method call, Object[] args) {
                            forwarded = location;
                            return null;
                        }
                    });
                }
                return defaultValue(call.getReturnType());
            }
        });
        response = proxy(HttpServletResponse.class, new InvocationHandler() {
            public Object invoke(Object target, Method call, Object[] args) {
                String name = call.getName();
                if ("setStatus".equals(name)) { status = ((Integer) args[0]).intValue(); return null; }
                if ("getStatus".equals(name)) { return Integer.valueOf(status); }
                if ("setHeader".equals(name) || "addHeader".equals(name)) { headers.put((String) args[0], (String) args[1]); return null; }
                if ("getHeader".equals(name)) { return headers.get(args[0]); }
                if ("setCharacterEncoding".equals(name)) { responseEncoding = (String) args[0]; return null; }
                if ("sendRedirect".equals(name)) { redirect = (String) args[0]; status = 302; return null; }
                if ("getWriter".equals(name)) { return new PrintWriter(body); }
                return defaultValue(call.getReturnType());
            }
        });
    }
    public HttpSession createSession() {
        final Map<String, Object> data = new LinkedHashMap<String, Object>();
        sessionAttributes = data;
        session = proxy(HttpSession.class, new InvocationHandler() {
            public Object invoke(Object target, Method call, Object[] args) {
                String name = call.getName();
                if ("getAttribute".equals(name)) { return data.get(args[0]); }
                if ("setAttribute".equals(name)) { data.put((String) args[0], args[1]); return null; }
                if ("removeAttribute".equals(name)) { data.remove(args[0]); return null; }
                if ("invalidate".equals(name)) { invalidated = true; data.clear(); session = null; return null; }
                if ("getId".equals(name)) { return Integer.toHexString(System.identityHashCode(data)); }
                if ("getServletContext".equals(name)) { return context; }
                return defaultValue(call.getReturnType());
            }
        });
        return session;
    }
    public void parameter(String name, String... values) { parameters.put(name, values); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
    private static Object defaultValue(Class<?> type) {
        if (Boolean.TYPE.equals(type)) { return Boolean.FALSE; }
        if (Integer.TYPE.equals(type)) { return Integer.valueOf(0); }
        if (Long.TYPE.equals(type)) { return Long.valueOf(0); }
        return null;
    }
}
