<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<html:form action="/purchases" method="post"><%@ include file="../fragments/identity.jspf" %>
  <section class="card"><h2>発注条件</h2><div class="field-grid">
    <label>仕入先<html:select property="supplierId"><html:option value="">選択してください</html:option><html:options collection="suppliers" property="id" labelProperty="name"/></html:select></label><label>入荷倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label><label>発注日<input type="date" name="orderDate" value="<c:out value='${form.orderDate}'/>" required></label><label>入荷予定日<input type="date" name="expectedDate" value="<c:out value='${form.expectedDate}'/>" required></label><label class="wide">発注備考<html:textarea property="note" rows="3"/></label>
  </div></section>
  <section class="card"><h2>発注明細</h2><p class="help">単価・仕入入数・調達日数は有効な調達条件から取得します。明細入荷予定日は任意で、空欄なら発注の予定日を使用します。削除する行は全項目を空欄にしてください。</p>
    <div class="table-wrap"><table><thead><tr><th>行</th><th>商品</th><th>発注数量</th><th>明細入荷予定日</th><th>明細備考</th></tr></thead><tbody>
      <c:forEach items="${form.productId}" var="selected" varStatus="row"><tr><td><c:out value="${row.count}"/></td><td><select name="productId" aria-label="商品"><option value="">未使用行</option><c:forEach items="${products}" var="p"><option value="<c:out value='${p.id}'/>" <c:if test="${selected eq fn:escapeXml(p.id)}">selected</c:if>><c:out value="${p.code}"/> <c:out value="${p.name}"/></option></c:forEach></select></td><td><input type="text" name="quantity" maxlength="9" inputmode="numeric" aria-label="発注数量" value="<c:out value='${form.quantity[row.index]}'/>"></td><td><input type="date" name="lineExpectedDate" aria-label="明細入荷予定日" value="<c:out value='${form.lineExpectedDate[row.index]}'/>"></td><td><input type="text" name="lineNote" maxlength="500" aria-label="明細備考" value="<c:out value='${form.lineNote[row.index]}'/>"></td></tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button name="op" value="save" class="primary">下書き保存</button><button name="op" value="addLine" formnovalidate>入力行を追加</button><a class="button" href="<c:out value='${ctx}'/>/purchases.do">一覧へ</a></div>
  </section>
</html:form>
<%@ include file="../fragments/footer.jspf" %>
