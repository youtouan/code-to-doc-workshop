<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><div class="actions"><c:if test="${canWAREHOUSE or canMANAGER}"><a class="button primary" href="<c:out value='${ctx}'/>/purchases.do?op=new">仕入発注を作成</a></c:if><a class="button" href="<c:out value='${ctx}'/>/replenishment.do">補充候補を見る</a></div>
  <p class="help">承認済み発注に対して分納・検品を登録できます。不良品は在庫に計上せず、良品の受領で発注残数を減らします。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>発注番号</th><th>仕入先 / 倉庫</th><th>状態</th><th>発注日 / 入荷予定</th><th class="number">税抜発注額</th><th class="number">発注残数</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/purchases.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.supplierName}"/><br><c:out value="${item.warehouse.name}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><fmt:formatDate value="${item.orderDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.expectedDate}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/></td><td class="number"><c:out value="${item.outstandingQuantity}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する発注はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
