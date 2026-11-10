<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${apInvoice.number}"/> · <c:out value="${apInvoice.supplierName}"/></h2>
  <p class="help">値引しない行は0または空欄にします。入力するのは税抜値引額です。税額は元請求の税率・端数処理から計算します。申請中分も含む累計上限と未払残高を検証します。</p>
  <html:form action="/apCredits" method="post"><%@ include file="../fragments/token.jspf" %><html:hidden property="invoiceId"/><html:hidden property="version"/><input type="hidden" name="op" value="propose">
    <div class="field-grid"><label>仕入先値引番号<html:text property="supplierCreditNumber" maxlength="80"/></label><label>値引日<input type="date" name="creditDate" value="<c:out value='${form.creditDate}'/>" required></label><label class="wide">財務値引理由<html:textarea property="reason" rows="3"/></label></div>
    <div class="table-wrap"><table><thead><tr><th>元請求明細</th><th class="number">請求税抜金額</th><th>税率</th><th>今回の税抜値引額</th></tr></thead><tbody>
      <c:forEach items="${apInvoice.lines}" var="line" varStatus="row"><tr><td><c:out value="${line.productCode}"/> <c:out value="${line.description}"/><input type="hidden" name="invoiceLineId" value="<c:out value='${line.id}'/>"></td><td class="number"><fmt:formatNumber value="${line.netAmount}" pattern="#,##0.00"/></td><td><fmt:formatNumber value="${line.taxRate}" type="percent" maxFractionDigits="2"/></td><td><input type="text" name="netAmount" maxlength="15" inputmode="decimal" aria-label="税抜値引額" value="<c:out value='${form.netAmount[row.index]}'/>"></td></tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button class="primary">財務値引を申請</button><a class="button" href="<c:out value='${ctx}'/>/apInvoices.do?op=detail&amp;id=<c:out value='${apInvoice.id}'/>">元請求へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
