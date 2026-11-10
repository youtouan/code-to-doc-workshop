package jp.co.tsubame.wholesale.web.action;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.entity.BaseEntity;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public abstract class BaseAction extends Action {
    public final ActionForward execute(ActionMapping mapping, ActionForm rawForm, HttpServletRequest request,
                                       HttpServletResponse response) throws Exception {
        BaseForm form = (BaseForm) rawForm;
        request.setAttribute("form", form);
        request.setAttribute("route", mapping.getPath() + ".do");
        String op = form.getOp();
        if (!isRead(op) && !isMutation(op)) {
            response.setStatus(400);
            Web.error(request, null, "指定した操作は利用できません。");
            return view(request, "error", "操作の確認");
        }
        if ((isMutation(op) && !"POST".equals(request.getMethod()))
                || (isRead(op) && !"GET".equals(request.getMethod()))) {
            response.setStatus(405);
            response.setHeader("Allow", isMutation(op) ? "POST" : "GET");
            Web.error(request, null, "この操作は画面上のボタンから実行してください。");
            return view(request, "error", "操作の確認");
        }
        try {
            return perform(mapping, form, request, response);
        } catch (BusinessException ex) {
            String code = ex.getCode() == null ? "" : ex.getCode();
            int status = code.startsWith("permission") || code.startsWith("auth.") ? 403
                    : code.contains("notFound") ? 404
                    : code.contains("version") || code.contains("conflict") || code.startsWith("concurrent.")
                            || code.endsWith(".concurrent") || "amendment.stale".equals(code) ? 409 : 422;
            response.setStatus(status);
            Web.error(request, ex.getField(), ex.getMessage());
            request.setAttribute("failed", Boolean.TRUE);
            if (status == 403 || status == 404) {
                return view(request, "error", "操作の確認");
            }
            try {
                return failure(form, request);
            } catch (BusinessException redisplayError) {
                // Invalid identities/search values cannot be used to reload a detail form.
                return view(request, "error", "入力内容の確認");
            }
        }
    }

    protected boolean isRead(String op) {
        return "list".equals(op) || "detail".equals(op) || "edit".equals(op) || "new".equals(op);
    }
    protected abstract boolean isMutation(String op);
    protected abstract ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request,
                                              HttpServletResponse response) throws Exception;
    protected ActionForward failure(BaseForm form, HttpServletRequest request) throws Exception {
        return view(request, "error", "処理の確認");
    }
    protected ActionForward view(HttpServletRequest request, String path, String title) {
        request.setAttribute("pageTitle", title);
        return new ActionForward("/WEB-INF/jsp/" + path + ".jsp");
    }
    protected Actor actor(HttpServletRequest request) { return Web.actor(request); }
    protected <T> T service(HttpServletRequest request, String name, Class<T> type) {
        return Web.bean(request.getServletContext(), name, type);
    }
    protected CatalogService catalog(HttpServletRequest request) {
        return service(request, "catalogService", CatalogService.class);
    }
    protected void references(HttpServletRequest request) {
        CatalogService service = catalog(request);
        Actor actor = actor(request);
        request.setAttribute("customers", service.listActiveCustomers(actor));
        request.setAttribute("products", service.listActiveProducts(actor));
        request.setAttribute("warehouses", service.listActiveWarehouses(actor));
    }
    protected Long id(BaseForm form) { return Inputs.id(form.getId(), "伝票ID"); }
    protected int version(BaseForm form) { return Inputs.integer(form.getVersion(), "更新番号", 0, Integer.MAX_VALUE); }
    protected String reason(BaseForm form) { return Inputs.text(form.getReason(), "理由", 1000, true); }
    protected void identity(BaseForm form, BaseEntity entity) {
        form.setId(String.valueOf(entity.getId()));
        form.setVersion(String.valueOf(entity.getVersion()));
    }
    protected ActionForward redirect(HttpServletRequest request, HttpServletResponse response, String route,
                                     Long id, String message) throws IOException {
        request.getSession().setAttribute("flash", message);
        response.setStatus(303);
        response.setHeader("Location", request.getContextPath() + route + (id == null ? "" : "?op=detail&id=" + id));
        return null;
    }
    protected boolean validate(ActionMapping mapping, BaseForm form, HttpServletRequest request) {
        form.setPage(0);
        ActionErrors errors = form.validate(mapping, request);
        if (errors != null && !errors.isEmpty()) {
            saveErrors(request, errors);
            return false;
        }
        return true;
    }
}
