<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><c:if test="${canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/suppliers.do?op=new">仕入先を登録</a></div></c:if>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>コード</th><th>仕入先名</th><th>状態</th><th>締日 / 支払サイト</th><th class="number">最低発注額</th><th>標準納期</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/suppliers.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.code}"/></a></td><td><c:out value="${item.name}"/></td><td><c:out value="${item.active ? '利用中' : '停止中'}"/><c:if test="${item.onHold}"> / 発注保留</c:if></td><td><c:out value="${item.closingDay eq 31 ? '末日' : item.closingDay}"/> / <c:out value="${item.paymentTermDays}"/>日</td><td class="number"><fmt:formatNumber value="${item.minimumOrderAmount}" pattern="#,##0.00"/></td><td><c:out value="${item.defaultLeadTimeDays}"/>日</td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する仕入先はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
