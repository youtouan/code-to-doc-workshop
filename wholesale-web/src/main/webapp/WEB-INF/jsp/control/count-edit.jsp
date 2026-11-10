<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">商品を選択しなければ、その倉庫の全在庫を対象にします。選択する場合は在庫残高が存在する商品を指定してください。開始後は対象在庫の入出庫が制限されます。</p>
  <html:form action="/counts" method="post"><%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="begin">
    <div class="field-grid"><label>棚卸倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label><label>対象商品（任意・複数選択）<html:select property="productId" multiple="true" size="8"><html:options collection="products" property="id" labelProperty="name"/></html:select></label><label class="wide">棚卸備考<html:textarea property="note" rows="3"/></label></div>
    <div class="actions"><button class="primary">対象在庫を凍結して棚卸開始</button><a class="button" href="<c:out value='${ctx}'/>/counts.do">一覧へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
