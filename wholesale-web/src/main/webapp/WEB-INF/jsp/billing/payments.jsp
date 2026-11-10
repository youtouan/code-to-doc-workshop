<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><div class="actions"><c:if test="${canBILLING}"><a class="button primary" href="<c:out value='${ctx}'/>/payments.do?op=new">入金を登録</a></c:if><a class="button" href="<c:out value='${ctx}'/>/statements.do?op=cash">入出金履歴</a><a class="button" href="<c:out value='${ctx}'/>/statements.do">元帳・滞留債権</a></div>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>入金番号</th><th>得意先</th><th>入金日</th><th>方法 / 状態</th><th class="number">入金額</th><th class="number">消込済</th><th class="number">未消込</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/payments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.customerName}"/></td><td><fmt:formatDate value="${item.receivedDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${item.method}"/><br><span class="badge"><c:out value="${item.status}"/></span></td><td class="number"><fmt:formatNumber value="${item.amount}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${item.allocatedAmount}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${item.unallocatedAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">条件に一致する入金はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
