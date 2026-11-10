<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">増加は正の整数、減少は負の整数で指定します（例：10、-3）。0の調整は登録できません。引当済数量を下回る減少は承認できません。</p>
  <html:form action="/adjustments" method="post"><%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="propose">
    <div class="field-grid"><label>倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label><label>商品<html:select property="productId"><html:option value="">選択してください</html:option><html:options collection="products" property="id" labelProperty="name"/></html:select></label><label>増減数量<html:text property="quantityChange" maxlength="10"/></label><label class="wide">調整理由<html:textarea property="reason" rows="4"/></label></div>
    <div class="actions"><button class="primary">調整を申請</button><a class="button" href="<c:out value='${ctx}'/>/adjustments.do">一覧へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
