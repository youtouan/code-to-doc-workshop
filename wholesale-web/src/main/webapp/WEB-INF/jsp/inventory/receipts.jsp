<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <div class="actions"><c:if test="${canWAREHOUSE or canMANAGER or canBATCH}"><a class="button primary" href="<c:out value='${ctx}'/>/receipts.do?op=new">直接入庫を登録</a></c:if><a class="button" href="<c:out value='${ctx}'/>/purchases.do">仕入発注に対する入荷はこちら</a></div>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>入庫番号</th><th>入庫日</th><th>倉庫</th><th>商品</th><th class="number">数量</th><th class="number">単価</th><th>参照番号</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/receipts.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><fmt:formatDate value="${item.receiptDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${item.warehouse.name}"/></td><td><c:out value="${item.product.code}"/> <c:out value="${item.product.name}"/></td><td class="number"><c:out value="${item.quantity}"/></td><td class="number"><fmt:formatNumber value="${item.unitCost}" pattern="#,##0.00"/></td><td><c:out value="${item.reference}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">条件に一致する入庫はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
