<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>支払先の選択</h2><p class="help">入力を始める前に仕入先を選択します。仕入先を選び直すと未保存の入力は破棄されます。</p>
  <form action="<c:out value='${ctx}'/>/apPayments.do" method="get" class="toolbar"><input type="hidden" name="op" value="new"><label>仕入先<select name="supplierId" required><option value="">選択してください</option><c:forEach items="${suppliers}" var="supplier"><option value="<c:out value='${supplier.id}'/>" <c:if test="${form.supplierId eq fn:escapeXml(supplier.id)}">selected</c:if>><c:out value="${supplier.code}"/> <c:out value="${supplier.name}"/></option></c:forEach></select></label><button>支払入力を開始</button></form>
</section>
<c:if test="${not empty form.supplierId}">
  <section class="card"><h2><c:out value="${selectedSupplier.name}"/> への支払</h2><p class="help">候補は未払請求の先頭100件です（全<c:out value="${openItems.total}"/>件）。<a href="<c:out value='${ctx}'/>/apReports.do?op=open&amp;supplierId=<c:out value='${form.supplierId}'/>">未払一覧</a>で他の請求IDも確認できます。同じ仕入先の計上済み請求のみ指定してください。</p>
    <datalist id="ap-open-invoices"><c:forEach items="${openItems.items}" var="item"><option value="<c:out value='${item.invoiceId}'/>"><c:out value="${item.number}"/> / <c:out value="${item.supplierInvoiceNumber}"/> / 未払 <c:out value="${item.outstandingAmount}"/>円</option></c:forEach></datalist>
    <html:form action="/apPayments" method="post"><%@ include file="../fragments/token.jspf" %><html:hidden property="supplierId"/><html:hidden property="requestKey"/>
      <div class="field-grid"><label>支払日<input type="date" name="paymentDate" value="<c:out value='${form.paymentDate}'/>" required></label><label>支払額（消込額合計と一致）<html:text property="amount" maxlength="15"/></label>
        <label>支払方法<html:select property="method"><html:option value="BANK_TRANSFER">銀行振込</html:option><html:option value="CASH">現金</html:option><html:option value="CHEQUE">小切手</html:option></html:select></label><label>振込照合番号<html:text property="reference" maxlength="100"/></label><label class="wide">備考<html:textarea property="note" rows="3"/></label>
      </div>
      <div class="table-wrap"><table><thead><tr><th>支払先請求ID</th><th>今回の消込額（円）</th></tr></thead><tbody><c:forEach items="${form.invoiceId}" var="invoiceId" varStatus="row"><tr><td><input type="text" name="invoiceId" inputmode="numeric" maxlength="19" list="ap-open-invoices" aria-label="支払先請求ID" value="<c:out value='${invoiceId}'/>"></td><td><input type="text" name="allocationAmount" inputmode="decimal" maxlength="15" aria-label="消込額" value="<c:out value='${form.allocationAmount[row.index]}'/>"></td></tr></c:forEach></tbody></table></div>
      <p class="help">消込明細は200件までです。未使用行は両欄を空欄にします。二重送信は受付キーで検出し、同じ内容の再送は元の支払伝票を返します。</p>
      <div class="actions"><button name="op" value="pay" class="primary">支払・全消込を計上</button><button name="op" value="addLine" formnovalidate>入力行を追加</button><a class="button" href="<c:out value='${ctx}'/>/apPayments.do">一覧へ</a></div>
    </html:form>
  </section>
</c:if>
<%@ include file="../fragments/footer.jspf" %>
