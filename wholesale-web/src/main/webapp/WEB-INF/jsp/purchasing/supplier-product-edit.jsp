<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${supplier.name}"/> · 調達条件</h2><p class="help">仕入入数は商品入数の倍数、最低発注数量は仕入入数の倍数を指定してください（例：商品入数1・仕入入数12・最低数量24）。同じ最低数量段階で有効期間が重なる契約は登録できません。</p>
  <html:form action="/supplierProducts" method="post"><%@ include file="../fragments/identity.jspf" %><html:hidden property="supplierId"/><input type="hidden" name="op" value="save">
    <div class="field-grid"><label>商品<html:select property="productId"><html:option value="">選択してください</html:option><html:options collection="products" property="id" labelProperty="name"/></html:select></label><label>仕入先商品コード<html:text property="supplierProductCode" maxlength="60"/></label>
      <label>適用開始日<input type="date" name="validFrom" value="<c:out value='${form.validFrom}'/>" required></label><label>適用終了日（任意）<input type="date" name="validTo" value="<c:out value='${form.validTo}'/>"></label><label>最低発注数量<html:text property="minimumQuantity" maxlength="9"/></label><label>仕入入数<html:text property="orderPackSize" maxlength="9"/></label><label>調達日数<html:text property="leadTimeDays" maxlength="3"/></label><label>仕入単価（円）<html:text property="unitCost" maxlength="15"/></label>
      <label>利用状態<html:select property="active"><html:option value="true">有効</html:option><html:option value="false">無効</html:option></html:select></label><label>補充時の優先<html:select property="preferred"><html:option value="false">通常</html:option><html:option value="true">優先仕入先</html:option></html:select></label><label class="wide">備考<html:textarea property="notes" rows="3"/></label>
    </div><div class="actions"><button class="primary">保存</button><a class="button" href="<c:out value='${ctx}'/>/suppliers.do?op=detail&amp;id=<c:out value='${form.supplierId}'/>">仕入先へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
