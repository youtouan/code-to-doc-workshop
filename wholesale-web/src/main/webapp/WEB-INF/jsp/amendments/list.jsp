<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">承認済み・未完了受注の数量・納期変更を申請し、別の管理者が承認して一括適用します。商品追加・削除・価格変更は対象外です。新規申請は受注詳細から開始します。期間条件は申請日時です。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>変更申請 / 受注</th><th>得意先</th><th>状態</th><th>申請者 / 日時</th><th>元納期 → 提案納期</th><th class="number">税込金額（元 / 提案）</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/orderAmendments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><a href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${item.order.id}'/>"><c:out value="${item.order.number}"/></a></td><td><c:out value="${item.order.customerName}"/></td><td><c:out value="${item.status}"/></td><td><c:out value="${item.requestedBy}"/><br><fmt:formatDate value="${item.requestedAt}" pattern="yyyy-MM-dd HH:mm"/></td><td><fmt:formatDate value="${item.originalRequestedDate}" pattern="yyyy-MM-dd"/> → <fmt:formatDate value="${item.requestedDate}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${item.originalTotalAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${item.proposedTotalAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する変更申請はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
