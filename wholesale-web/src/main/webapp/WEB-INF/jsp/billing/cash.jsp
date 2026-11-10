<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">入金と取消をそれぞれの発生日で表示します。消込は入出金ではないため、この履歴には加算しません。</p>
  <form action="<c:out value='${ctx}'/>/statements.do" method="get" class="toolbar"><input type="hidden" name="op" value="cash"><label>開始日<input type="date" name="from" value="<c:out value='${form.from}'/>"></label><label>終了日<input type="date" name="to" value="<c:out value='${form.to}'/>"></label><label>キーワード<input type="search" name="text" value="<c:out value='${form.text}'/>" maxlength="100"></label><button class="primary">検索</button></form>
  <div class="table-wrap"><table><thead><tr><th>日付 / 区分</th><th>入金番号 / 得意先</th><th>方法 / 照合番号</th><th class="number">入金</th><th class="number">取消出金</th><th>操作者 / 理由</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><fmt:formatDate value="${item.date}" pattern="yyyy-MM-dd"/><br><c:out value="${item.type}"/></td><td><a href="<c:out value='${ctx}'/>/payments.do?op=detail&amp;id=<c:out value='${item.receiptId}'/>"><c:out value="${item.receiptNumber}"/></a><br><c:out value="${item.customerName}"/></td><td><c:out value="${item.method}"/><br><c:out value="${item.reference}"/></td><td class="number"><fmt:formatNumber value="${item.inflow}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${item.outflow}" pattern="#,##0.00"/></td><td><c:out value="${item.actor}"/><br><c:out value="${item.reason}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する入出金はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
