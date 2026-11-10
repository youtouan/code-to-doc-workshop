<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><div class="actions"><c:if test="${canBILLING or canBATCH}"><a class="button primary" href="<c:out value='${ctx}'/>/invoices.do?op=new">請求書を作成</a></c:if><c:if test="${canBILLING or canMANAGER}"><a class="button" href="<c:out value='${ctx}'/>/invoices.do?op=credits">返品クレジット</a></c:if></div>
  <p class="help">締日までの未請求出荷を集約します。下書きを確定すると債権に計上され、入金消込が可能になります。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>請求番号</th><th>得意先</th><th>状態</th><th>締日 / 支払期日</th><th class="number">請求額</th><th class="number">消込済 / クレジット</th><th class="number">請求残高</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/invoices.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.customerName}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><fmt:formatDate value="${item.periodEnd}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.dueDate}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${item.paidAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${item.creditedAmount}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${item.outstandingAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">条件に一致する請求はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
