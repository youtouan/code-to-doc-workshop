<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">確定済み出荷の詳細から返品申請を作成します。申請者以外の管理者が承認し、倉庫担当者が全明細を一括受領します。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>返品番号 / 出荷番号</th><th>得意先</th><th>状態</th><th>返品理由</th><th>申請日 / 受領日</th><th>申請者</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/returns.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.shipment.number}"/></td><td><c:out value="${item.shipment.order.customerName}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><c:out value="${item.reason}"/></td><td><fmt:formatDate value="${item.requestedDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.receivedDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${item.createdBy}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する返品はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
