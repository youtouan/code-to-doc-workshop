<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <p class="help">利用可能数 = 現在庫 − 引当済。在庫保留中の商品は出荷引当に利用できません。SHORT は発注点割れです。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>倉庫</th><th>商品</th><th class="number">現在庫</th><th class="number">引当済</th><th class="number">利用可能</th><th>保留</th><th>最終移動</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><c:out value="${item.warehouse.name}"/></td><td><a href="<c:out value='${ctx}'/>/stock.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.product.code}"/> <c:out value="${item.product.name}"/></a></td><td class="number"><c:out value="${item.onHand}"/></td><td class="number"><c:out value="${item.reserved}"/></td><td class="number"><strong><c:out value="${item.available}"/></strong></td><td><c:out value="${item.blocked ? '保留中' : '通常'}"/></td><td><fmt:formatDate value="${item.lastMovementAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">条件に一致する在庫はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
