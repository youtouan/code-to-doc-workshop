<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>照会条件</h2>
  <form action="<c:out value='${ctx}'/>/statements.do" method="get">
    <div class="field-grid"><label>得意先<select name="customerId" required><option value="">選択してください</option><c:forEach items="${customers}" var="c"><option value="<c:out value='${c.id}'/>" <c:if test="${form.customerId eq fn:escapeXml(c.id)}">selected</c:if>><c:out value="${c.code}"/> <c:out value="${c.name}"/></option></c:forEach></select></label><label>滞留債権の基準日<input type="date" name="asOf" value="<c:out value='${form.asOf}'/>"></label><label>元帳の開始日<input type="date" name="from" value="<c:out value='${form.from}'/>"></label><label>元帳の終了日<input type="date" name="to" value="<c:out value='${form.to}'/>"></label></div>
    <div class="actions"><button name="op" value="detail" class="primary">元帳を照会</button><button name="op" value="ageing">滞留債権を照会</button></div>
  </form>
</section>
<c:if test="${not empty statement}">
  <section class="card"><h2><c:out value="${statement.customer.name}"/> · 得意先元帳</h2>
    <div class="metrics"><div class="metric"><span>期首残高</span><strong><fmt:formatNumber value="${statement.openingBalance}" pattern="#,##0"/></strong></div><div class="metric"><span>借方合計</span><strong><fmt:formatNumber value="${statement.debitTotal}" pattern="#,##0"/></strong></div><div class="metric"><span>貸方合計</span><strong><fmt:formatNumber value="${statement.creditTotal}" pattern="#,##0"/></strong></div><div class="metric"><span>期末残高</span><strong><fmt:formatNumber value="${statement.closingBalance}" pattern="#,##0"/></strong></div></div>
    <div class="table-wrap"><table><thead><tr><th>日付</th><th>区分 / 伝票</th><th>内容</th><th class="number">借方</th><th class="number">貸方</th><th class="number">残高</th></tr></thead><tbody>
      <c:forEach items="${statement.lines}" var="line"><tr><td><fmt:formatDate value="${line.date}" pattern="yyyy-MM-dd"/></td><td><c:out value="${line.type}"/><br><c:out value="${line.documentNumber}"/></td><td><c:out value="${line.description}"/></td><td class="number"><fmt:formatNumber value="${line.debit}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${line.credit}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${line.balance}" pattern="#,##0.00"/></td></tr></c:forEach>
      <c:if test="${empty statement.lines}"><tr><td colspan="6" class="empty">対象期間の取引はありません。</td></tr></c:if>
    </tbody></table></div>
  </section>
</c:if>
<c:if test="${not empty ageing}">
  <section class="card"><h2><c:out value="${ageing.customer.name}"/> · 滞留債権</h2><p class="help">基準日 <fmt:formatDate value="${ageing.asOf}" pattern="yyyy-MM-dd"/> 時点。期限超過日数別に請求残高を表示します。</p>
    <div class="table-wrap"><table><thead><tr><th>期日前</th><th>1～30日</th><th>31～60日</th><th>61～90日</th><th>90日超</th><th>請求残高合計</th></tr></thead><tbody><tr><td class="number"><fmt:formatNumber value="${ageing.current}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${ageing.days1To30}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${ageing.days31To60}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${ageing.days61To90}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${ageing.daysOver90}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${ageing.totalOutstanding}" pattern="#,##0.00"/></td></tr></tbody></table></div>
    <dl class="totals"><dt>未消込入金</dt><dd><fmt:formatNumber value="${ageing.unallocatedReceipts}" pattern="#,##0.00"/></dd><dt>未適用クレジット</dt><dd><fmt:formatNumber value="${ageing.unappliedCredits}" pattern="#,##0.00"/></dd><dt>純債権残高</dt><dd><fmt:formatNumber value="${ageing.netBalance}" pattern="#,##0.00"/></dd></dl>
    <div class="table-wrap"><table><thead><tr><th>請求番号</th><th>支払期日</th><th class="number">超過日数</th><th class="number">請求残高</th></tr></thead><tbody><c:forEach items="${ageing.lines}" var="line"><tr><td><a href="<c:out value='${ctx}'/>/invoices.do?op=detail&amp;id=<c:out value='${line.invoiceId}'/>"><c:out value="${line.invoiceNumber}"/></a></td><td><fmt:formatDate value="${line.dueDate}" pattern="yyyy-MM-dd"/></td><td class="number"><c:out value="${line.daysOverdue}"/></td><td class="number"><fmt:formatNumber value="${line.outstandingAmount}" pattern="#,##0.00"/></td></tr></c:forEach></tbody></table></div>
  </section>
</c:if>
<%@ include file="../fragments/footer.jspf" %>
