<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">仕入先への支払と請求消込を一括計上します。支払額と消込合計は一致させてください。取消は全消込を戻し、元帳に取消日で記録されます。期間条件は支払日です。</p>
  <div class="actions"><c:if test="${canBILLING}"><a class="button primary" href="<c:out value='${ctx}'/>/apPayments.do?op=new">支払を登録</a></c:if><a class="button" href="<c:out value='${ctx}'/>/apReports.do?op=open">未払残高を確認</a></div>
  <%@ include file="../fragments/ap-search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>支払番号</th><th>仕入先</th><th>支払日</th><th>方法 / 照合番号</th><th>状態</th><th class="number">支払額</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/apPayments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.supplierName}"/></td><td><fmt:formatDate value="${item.paymentDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${item.method}"/><br><c:out value="${item.reference}"/></td><td><c:out value="${item.status}"/></td><td class="number"><fmt:formatNumber value="${item.amount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する支払はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
