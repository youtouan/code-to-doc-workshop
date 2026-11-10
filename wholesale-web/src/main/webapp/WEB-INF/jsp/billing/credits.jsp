<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">返品受領時に作成されるクレジットを表示します。元請求の状態に応じて適用され、未適用額は得意先の債権残高でも確認できます。</p>
  <form action="<c:out value='${ctx}'/>/invoices.do" method="get" class="toolbar"><input type="hidden" name="op" value="credits"><label>得意先<select name="customerId" required><option value="">選択してください</option><c:forEach items="${customers}" var="c"><option value="<c:out value='${c.id}'/>" <c:if test="${form.customerId eq fn:escapeXml(c.id)}">selected</c:if>><c:out value="${c.name}"/></option></c:forEach></select></label><button class="primary">表示</button></form>
  <div class="table-wrap"><table><thead><tr><th>クレジット番号 / 返品</th><th>状態</th><th>発行日</th><th>元請求書</th><th class="number">金額</th><th class="number">適用 / 未適用</th></tr></thead><tbody>
    <c:forEach items="${credits}" var="item"><tr><td><c:out value="${item.number}"/><br><a href="<c:out value='${ctx}'/>/returns.do?op=detail&amp;id=<c:out value='${item.salesReturn.id}'/>"><c:out value="${item.salesReturn.number}"/></a></td><td><c:out value="${item.status}"/></td><td><fmt:formatDate value="${item.issuedDate}" pattern="yyyy-MM-dd"/></td><td><c:if test="${not empty item.invoice}"><a href="<c:out value='${ctx}'/>/invoices.do?op=detail&amp;id=<c:out value='${item.invoice.id}'/>"><c:out value="${item.invoice.number}"/></a></c:if></td><td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${item.appliedAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${item.unappliedAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty credits}"><tr><td colspan="6" class="empty">得意先を選択してください。対象のクレジットがない場合も空欄となります。</td></tr></c:if>
  </tbody></table></div>
</section>
<%@ include file="../fragments/footer.jspf" %>
