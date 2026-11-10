<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">仕入発注のない直接入庫を登録します。二重送信は同じ受付キーで検出します。登録後の数量訂正は在庫調整で記録してください。</p>
  <html:form action="/receipts" method="post">
    <%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="receive"><html:hidden property="requestKey"/>
    <div class="field-grid">
      <label>入庫倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label>
      <label>商品<html:select property="productId"><html:option value="">選択してください</html:option><html:options collection="products" property="id" labelProperty="name"/></html:select></label>
      <label>数量<html:text property="quantity" maxlength="9"/></label><label>入庫単価（円）<html:text property="unitCost" maxlength="15"/></label>
      <label>入庫日<input type="date" name="receiptDate" value="<c:out value='${form.receiptDate}'/>" required></label><label>参照番号<html:text property="reference" maxlength="100"/></label>
      <label class="wide">備考<html:textarea property="note" rows="3"/></label>
    </div>
    <div class="actions"><button class="primary" type="submit">入庫を確定</button><a class="button" href="<c:out value='${ctx}'/>/receipts.do">一覧へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
