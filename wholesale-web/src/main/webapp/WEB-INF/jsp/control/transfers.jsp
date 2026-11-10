<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">移送は下書き → 申請 → 独立した承認 → 出庫 → 受領の順に処理します。移送中在庫は元倉庫から減少し、受領するまで移送先には加算されません。</p>
  <c:if test="${canWAREHOUSE or canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/transfers.do?op=new">移送下書きを作成</a></div></c:if>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>移送番号</th><th>移送元 → 移送先</th><th>状態</th><th>起票者 / 日時</th><th>出庫日時</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/transfers.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.sourceWarehouse.name}"/> → <c:out value="${item.destinationWarehouse.name}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><c:out value="${item.createdBy}"/><br><fmt:formatDate value="${item.createdAt}" pattern="yyyy-MM-dd HH:mm"/></td><td><fmt:formatDate value="${item.dispatchedAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="5" class="empty">条件に一致する移送はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
