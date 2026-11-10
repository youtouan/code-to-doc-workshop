package jp.co.tsubame.wholesale.web.action;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockCountCommand;
import jp.co.tsubame.wholesale.common.StockCountEntry;
import jp.co.tsubame.wholesale.entity.StockCount;
import jp.co.tsubame.wholesale.entity.StockCountLine;
import jp.co.tsubame.wholesale.service.StockCountService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.CountForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class CountAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) {
        return "begin".equals(op) || "record".equals(op) || "review".equals(op) || "reopen".equals(op)
                || "approve".equals(op) || "cancel".equals(op);
    }
    private StockCountService counts(HttpServletRequest request) { return service(request, "stockCountService", StockCountService.class); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        CountForm form = (CountForm) base;
        if ("approve".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if ("record".equals(form.getOp())) { actor(request).require("WAREHOUSE", "BATCH"); }
        else if (isMutation(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER"); }
        StockCountService service = counts(request);
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchWarehouse", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"COUNTING", "REVIEWED", "APPROVED", "CANCELLED"});
            request.setAttribute("results", service.search(actor(request), Inputs.search(form)));
            return view(request, "control/counts", "棚卸一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER"); references(request);
            return view(request, "control/count-edit", "棚卸の開始");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        StockCount result;
        if ("begin".equals(form.getOp())) {
            StockCountCommand command = new StockCountCommand();
            command.setWarehouseId(Inputs.id(form.getWarehouseId(), "棚卸倉庫"));
            command.setNote(Inputs.text(form.getNote(), "棚卸備考", 500, false));
            if (form.getProductId() != null && form.getProductId().length > 0) {
                if (form.getProductId().length > 100) { throw Inputs.invalid("商品", "一度に選択できる商品は100件です。"); }
                command.setProductIds(new ArrayList<Long>(Inputs.uniqueIds(form.getProductId(), "棚卸商品")));
            }
            result = service.begin(actor(request), command);
        } else if ("record".equals(form.getOp())) {
            Inputs.arrays(form.getLineId(), form.getCountedQuantity(), form.getLineNote());
            Inputs.uniqueIds(form.getLineId(), "棚卸明細");
            List<StockCountEntry> entries = new ArrayList<StockCountEntry>();
            for (int i = 0; i < form.getLineId().length; i++) {
                if (form.getCountedQuantity()[i] == null || form.getCountedQuantity()[i].length() == 0) { continue; }
                StockCountEntry entry = new StockCountEntry();
                entry.setLineId(Inputs.id(form.getLineId()[i], "棚卸明細"));
                entry.setCountedQuantity(Integer.valueOf(Inputs.quantity(form.getCountedQuantity()[i], "実棚数量", true)));
                entry.setNote(Inputs.text(form.getLineNote()[i], "差異理由", 500, false)); entries.add(entry);
            }
            if (entries.isEmpty()) { throw Inputs.invalid("実棚数量", "1行以上の実棚数量を入力してください。"); }
            result = service.record(actor(request), id(form), version(form), entries);
        } else if ("review".equals(form.getOp())) { result = service.review(actor(request), id(form), version(form)); }
        else if ("reopen".equals(form.getOp())) { result = service.reopen(actor(request), id(form), version(form), reason(form)); }
        else if ("approve".equals(form.getOp())) { result = service.approve(actor(request), id(form), version(form)); }
        else { result = service.cancel(actor(request), id(form), version(form), reason(form)); }
        return redirect(request, response, "/counts.do", result.getId(), "棚卸処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        CountForm form = (CountForm) base;
        if ("list".equals(form.getOp())) { return view(request, "control/counts", "棚卸検索条件の確認"); }
        if ("begin".equals(form.getOp())) { references(request); return view(request, "control/count-edit", "棚卸範囲の確認"); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "棚卸条件の確認");
    }
    private ActionForward detail(CountForm form, HttpServletRequest request, boolean refresh) {
        StockCount count = counts(request).get(actor(request), id(form));
        request.setAttribute("count", count);
        Search search = Inputs.search(form);
        int start = Math.min(count.getLines().size(), search.getOffset());
        int end = Math.min(count.getLines().size(), start + search.getSize());
        List<StockCountLine> lines = count.getLines().subList(start, end);
        request.setAttribute("results", new Page<StockCountLine>(lines, count.getLines().size(), search.getPage(), search.getSize()));
        if (refresh) {
            identity(form, count);
            String[] ids = new String[lines.size()], quantities = new String[lines.size()], notes = new String[lines.size()];
            for (int i = 0; i < lines.size(); i++) {
                StockCountLine line = lines.get(i); ids[i] = String.valueOf(line.getId());
                quantities[i] = line.getCountedQuantity() == null ? "" : String.valueOf(line.getCountedQuantity());
                notes[i] = line.getNote();
            }
            form.setLineId(ids); form.setCountedQuantity(quantities); form.setLineNote(notes);
        }
        return view(request, "control/count-detail", "棚卸詳細 · " + count.getNumber());
    }
}
