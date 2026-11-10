<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${payment.number}"/> <span class="badge"><c:out value="${payment.status}"/></span></h2>
  <dl><dt>得意先</dt><dd><c:out value="${payment.customerName}"/></dd><dt>入金日 / 方法</dt><dd><fmt:formatDate value="${payment.receivedDate}" pattern="yyyy-MM-dd"/> / <c:out value="${payment.method}"/></dd><dt>入金額</dt><dd><fmt:formatNumber value="${payment.amount}" pattern="#,##0.00"/> 円</dd><dt>消込済 / 未消込</dt><dd><fmt:formatNumber value="${payment.allocatedAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${payment.unallocatedAmount}" pattern="#,##0.00"/> 円</dd><dt>照合番号</dt><dd><c:out value="${payment.reference}"/></dd><dt>備考</dt><dd class="prewrap"><c:out value="${payment.notes}"/></dd><dt>取消理由</dt><dd><c:out value="${payment.cancellationReason}"/></dd><dt>受付キー</dt><dd class="mono"><c:out value="${payment.requestKey}"/></dd></dl>
</section>
<c:if test="${canBILLING and payment.status eq 'POSTED'}">
  <section class="card"><h2>請求書へ消込</h2><p class="help">同じ得意先の確定済み請求残高のみ選択できます。未消込額と請求残高の両方を超えない金額を指定します。</p>
    <html:form action="/payments" method="post"><%@ include file="../fragments/identity.jspf" %>
      <div class="field-grid"><label>請求書<select name="invoiceId"><option value="">選択してください</option><c:forEach items="${openInvoices}" var="i"><option value="<c:out value='${i.id}'/>" <c:if test="${form.invoiceId eq fn:escapeXml(i.id)}">selected</c:if>><c:out value="${i.number}"/> 残高<c:out value="${i.outstandingAmount}"/>円</option></c:forEach></select></label><label>消込額（円）<html:text property="amount" maxlength="15"/></label><label class="wide">入金取消理由<html:textarea property="reason" rows="2"/></label></div>
      <div class="actions"><button name="op" value="allocate" class="primary">消込を登録</button><c:if test="${payment.allocatedAmount eq 0}"><button name="op" value="cancel" class="danger">入金全体を取消</button></c:if></div>
    </html:form>
  </section>
</c:if>
<section class="card"><h2>消込履歴</h2><div class="table-wrap"><table><thead><tr><th>請求書 / 消込日</th><th class="number">消込額</th><th>状態 / 操作者</th><th>取消</th></tr></thead><tbody>
  <c:forEach items="${payment.allocations}" var="allocation"><tr><td><a href="<c:out value='${ctx}'/>/invoices.do?op=detail&amp;id=<c:out value='${allocation.invoice.id}'/>"><c:out value="${allocation.invoice.number}"/></a><br><fmt:formatDate value="${allocation.allocationDate}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${allocation.amount}" pattern="#,##0.00"/></td><td><c:out value="${allocation.status}"/> / <c:out value="${allocation.createdBy}"/></td><td>
    <c:set var="reversalInput" value=""/><c:if test="${form.allocationId eq fn:escapeXml(allocation.id)}"><c:set var="reversalInput" value="${form.reason}"/></c:if>
    <c:choose><c:when test="${canBILLING and allocation.status eq 'ACTIVE'}"><form action="<c:out value='${ctx}'/>/payments.do" method="post"><%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="reverse"><input type="hidden" name="id" value="<c:out value='${payment.id}'/>"><input type="hidden" name="allocationId" value="<c:out value='${allocation.id}'/>"><input type="hidden" name="allocationVersion" value="<c:out value='${allocation.version}'/>"><label>消込取消理由<input type="text" name="reason" maxlength="500" required value="<c:out value='${reversalInput}'/>"></label><button class="danger">この消込を取消</button></form></c:when><c:otherwise><c:out value="${allocation.reversalReason}"/><br><c:out value="${allocation.reversedBy}"/> <fmt:formatDate value="${allocation.reversedAt}" pattern="yyyy-MM-dd HH:mm"/></c:otherwise></c:choose>
  </td></tr></c:forEach><c:if test="${empty payment.allocations}"><tr><td colspan="4" class="empty">消込履歴はありません。</td></tr></c:if>
</tbody></table></div></section>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/payments.do">一覧へ</a><a class="button" href="<c:out value='${ctx}'/>/statements.do?customerId=<c:out value='${payment.customer.id}'/>">得意先元帳</a></div>
<%@ include file="../fragments/footer.jspf" %>
