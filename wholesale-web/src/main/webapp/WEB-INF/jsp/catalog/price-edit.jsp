<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <html:form action="/prices" method="post">
    <%@ include file="../fragments/identity.jspf" %>
    <div class="field-grid">
      <label>得意先<html:select property="customerId"><html:option value="">選択してください</html:option><html:options collection="customers" property="id" labelProperty="name"/></html:select></label>
      <label>商品<html:select property="productId"><html:option value="">選択してください</html:option><html:options collection="products" property="id" labelProperty="name"/></html:select></label>
      <label>適用開始日<input type="date" name="validFrom" value="<c:out value='${form.validFrom}'/>" required></label>
      <label>適用終了日（任意）<input type="date" name="validTo" value="<c:out value='${form.validTo}'/>"></label>
      <label>最低数量<html:text property="minimumQuantity" maxlength="9"/></label>
      <label>契約単価（円）<html:text property="unitPrice" maxlength="15"/></label>
      <label class="wide">備考<html:textarea property="notes" rows="3"/></label>
    </div>
    <div class="actions"><button type="submit" name="op" value="save" class="primary">保存</button><a class="button" href="<c:out value='${ctx}'/>/prices.do?customerId=<c:out value='${form.customerId}'/>">戻る</a>
    <c:if test="${not empty form.id}"><button type="submit" name="op" value="delete" class="danger" formnovalidate>この価格条件を削除</button></c:if></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
