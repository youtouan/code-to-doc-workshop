<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<html:form action="/orders" method="post">
  <%@ include file="../fragments/identity.jspf" %>
  <section class="card"><h2>受注ヘッダー</h2>
    <div class="field-grid">
      <label>得意先<html:select property="customerId"><html:option value="">選択してください</html:option><html:options collection="customers" property="id" labelProperty="name"/></html:select></label>
      <label>出荷倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label>
      <label>受注日<input type="date" name="orderDate" value="<c:out value='${form.orderDate}'/>" required></label>
      <label>希望納期<input type="date" name="requestedDate" value="<c:out value='${form.requestedDate}'/>" required></label>
      <label>先方注文番号<html:text property="externalReference" maxlength="100"/></label>
      <label>納品先住所（空欄で得意先住所）<html:text property="deliveryAddress" maxlength="250"/></label>
      <label class="wide">備考<html:textarea property="notes" rows="3"/></label>
    </div>
    <c:if test="${not empty form.id}"><p class="help">保存済み受注の得意先は変更できません。編集保存時に現行の価格条件で再計算します。</p></c:if>
  </section>
  <section class="card"><h2>商品明細</h2>
    <p class="help">数量は商品の入数の倍数で入力してください。未使用の行は商品・数量とも空欄にします。削除する行も空欄にしてください。</p>
    <c:if test="${canMANAGER}"><p class="help">交渉単価は明示的な価格変更時のみ入力し、理由を記録してください。空欄なら価格条件が適用されます。</p></c:if>
    <div class="table-wrap"><table><thead><tr><th>行</th><th>商品（コード / 名称 / 入数）</th><th>数量</th><c:if test="${canMANAGER}"><th>交渉単価（任意）</th><th>価格変更理由</th></c:if></tr></thead><tbody>
      <c:forEach items="${form.productId}" var="selectedProduct" varStatus="row"><tr>
        <td><c:out value="${row.count}"/></td>
        <td><select name="productId" aria-label="商品"><option value="">未使用行</option><c:forEach items="${products}" var="p"><option value="<c:out value='${p.id}'/>" <c:if test="${selectedProduct eq fn:escapeXml(p.id)}">selected</c:if>><c:out value="${p.code}"/> / <c:out value="${p.name}"/> / 入数<c:out value="${p.packSize}"/></option></c:forEach></select></td>
        <td><input type="text" inputmode="numeric" name="quantity" maxlength="9" aria-label="数量" value="<c:out value='${form.quantity[row.index]}'/>"></td>
        <c:choose><c:when test="${canMANAGER}"><td><input type="text" inputmode="decimal" name="priceOverride" maxlength="15" aria-label="交渉単価" value="<c:out value='${form.priceOverride[row.index]}'/>"></td><td><input type="text" name="priceReason" maxlength="250" aria-label="価格変更理由" value="<c:out value='${form.priceReason[row.index]}'/>"></td></c:when>
        <c:otherwise><td hidden><input type="hidden" name="priceOverride" value=""><input type="hidden" name="priceReason" value=""></td></c:otherwise></c:choose>
      </tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button name="op" value="save" type="submit" class="primary">下書き保存</button><button name="op" value="addLine" type="submit" formnovalidate>入力行を5行追加</button><a class="button" href="<c:out value='${ctx}'/>/orders.do">一覧へ戻る</a></div>
  </section>
</html:form>
<%@ include file="../fragments/footer.jspf" %>
