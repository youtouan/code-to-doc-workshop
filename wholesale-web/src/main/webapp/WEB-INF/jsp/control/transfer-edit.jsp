<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><html:form action="/transfers" method="post">
  <%@ include file="../fragments/identity.jspf" %>
  <div class="field-grid"><label>移送元倉庫<html:select property="sourceWarehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label><label>移送先倉庫<html:select property="destinationWarehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label><label class="wide">移送備考<html:textarea property="note" rows="3"/></label></div>
  <p class="help">移送元・移送先は異なる倉庫を指定します。未使用行は商品・数量とも空欄にしてください。</p>
  <%@ include file="../fragments/product-lines.jspf" %>
  <div class="actions"><button name="op" value="save" class="primary">下書きを保存</button><button name="op" value="addLine" formnovalidate>入力行を追加</button><a class="button" href="<c:out value='${ctx}'/>/transfers.do">一覧へ</a></div>
</html:form></section>
<%@ include file="../fragments/footer.jspf" %>
