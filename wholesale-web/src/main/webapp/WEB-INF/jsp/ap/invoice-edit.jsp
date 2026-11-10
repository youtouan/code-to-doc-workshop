<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<html:form action="/apInvoices" method="post"><%@ include file="../fragments/identity.jspf" %>
  <section class="card"><h2>仕入先請求書の内容</h2>
    <p class="help">仕入先が発行した請求書の数量・単価・税率を入力します。保存済み請求を編集すると入荷照合と差異承認がリセットされます。保存後に仕入先は変更できません。</p>
    <div class="field-grid">
      <c:choose><c:when test="${empty form.id}"><label>仕入先<html:select property="supplierId"><html:option value="">選択してください</html:option><html:options collection="suppliers" property="id" labelProperty="name"/></html:select></label></c:when><c:otherwise><div><span class="field">仕入先</span><p><c:out value="${apInvoice.supplierName}"/></p><html:hidden property="supplierId"/></div></c:otherwise></c:choose>
      <label>仕入先請求番号<html:text property="supplierInvoiceNumber" maxlength="80"/></label>
      <label>仕入先請求日<input type="date" name="invoiceDate" value="<c:out value='${form.invoiceDate}'/>" required></label>
      <label>支払期日（空欄は仕入先支払サイト）<input type="date" name="dueDate" value="<c:out value='${form.dueDate}'/>"></label>
      <label class="wide">備考<html:textarea property="note" rows="3"/></label>
    </div>
  </section>
  <section class="card"><h2>仕入先請求明細</h2><p class="help">税率は10%なら0.10、8%なら0.08、非課税なら0。数量は1～1,000,000、明細は200行までです。未使用行は全欄を空欄にしてください。金額はサーバーで再計算されます。</p>
    <p class="help">商品IDで指定します。候補は利用中の商品ですが、過去の検収入荷に対応する停止中の商品IDも入力できます。<a href="<c:out value='${ctx}'/>/products.do">商品一覧</a>の詳細、または<a href="<c:out value='${ctx}'/>/apReports.do?op=receipts">検収入荷一覧</a>でIDを確認してください。存在しない商品や自由記述の経費行は登録できません。</p>
    <datalist id="ap-invoice-products"><c:forEach items="${products}" var="product"><option value="<c:out value='${product.id}'/>"><c:out value="${product.code}"/> <c:out value="${product.name}"/></option></c:forEach></datalist>
    <div class="table-wrap"><table><thead><tr><th>行</th><th>商品</th><th>請求書記載の品名（任意）</th><th>数量</th><th>単価（円）</th><th>税率</th></tr></thead><tbody>
      <c:forEach items="${form.productId}" var="selected" varStatus="row"><tr><td><c:out value="${row.count}"/></td>
        <td><input type="text" name="productId" maxlength="19" inputmode="numeric" list="ap-invoice-products" aria-label="請求商品ID" value="<c:out value='${selected}'/>"></td>
        <td><input type="text" name="description" maxlength="160" aria-label="品名" value="<c:out value='${form.description[row.index]}'/>"></td>
        <td><input type="text" name="quantity" inputmode="numeric" maxlength="7" aria-label="数量" value="<c:out value='${form.quantity[row.index]}'/>"></td>
        <td><input type="text" name="unitPrice" inputmode="decimal" maxlength="15" aria-label="単価" value="<c:out value='${form.unitPrice[row.index]}'/>"></td>
        <td><input type="text" name="taxRate" inputmode="decimal" maxlength="6" aria-label="税率" value="<c:out value='${form.taxRate[row.index]}'/>"></td>
      </tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button name="op" value="save" class="primary">下書きを保存</button><button name="op" value="addLine" formnovalidate>入力行を追加</button><a class="button" href="<c:out value='${ctx}'/>/apInvoices.do">一覧へ</a></div>
  </section>
</html:form>
<%@ include file="../fragments/footer.jspf" %>
