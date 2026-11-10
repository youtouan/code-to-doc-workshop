<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">引当済み受注の詳細画面から出荷指示を作成します。出荷確定時に在庫が減少し、請求対象になります。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>出荷番号 / 受注番号</th><th>得意先</th><th>状態</th><th>予定日 / 出荷日</th><th>運送会社 / 送り状</th><th class="number">税抜金額</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.order.number}"/></td><td><c:out value="${item.order.customerName}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><fmt:formatDate value="${item.plannedDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.shippedDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${item.carrier}"/><br><c:out value="${item.trackingNumber}"/></td><td class="number"><fmt:formatNumber value="${item.netAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する出荷はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
