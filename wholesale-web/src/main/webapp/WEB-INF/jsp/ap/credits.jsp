<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">計上済み仕入先請求の詳細から財務値引を申請します。値引は買掛金を減額する処理で、返品・在庫移動は行いません。期間条件は値引日です。</p>
  <%@ include file="../fragments/ap-search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>値引管理番号 / 仕入先番号</th><th>元請求 / 仕入先</th><th>状態</th><th>値引日 / 計上日</th><th class="number">税込値引額</th><th>申請者 / 承認者</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/apCredits.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.supplierCreditNumber}"/></td><td><c:out value="${item.invoice.number}"/><br><c:out value="${item.supplier.name}"/></td><td><c:out value="${item.status}"/></td><td><fmt:formatDate value="${item.creditDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.postedDate}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/></td><td><c:out value="${item.createdBy}"/> / <c:out value="${item.approvedBy}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する財務値引はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
