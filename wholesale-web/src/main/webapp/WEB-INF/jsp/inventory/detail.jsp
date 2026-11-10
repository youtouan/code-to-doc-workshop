<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${stock.warehouse.name}"/> · <c:out value="${stock.product.code}"/> <c:out value="${stock.product.name}"/></h2>
  <div class="metrics"><div class="metric"><span>現在庫</span><strong><c:out value="${stock.onHand}"/></strong></div><div class="metric"><span>引当済</span><strong><c:out value="${stock.reserved}"/></strong></div><div class="metric"><span>利用可能</span><strong><c:out value="${stock.available}"/></strong></div><div class="metric"><span>出庫制限</span><strong><c:out value="${stock.blocked ? '保留' : '通常'}"/></strong></div></div>
  <div class="actions"><a class="button" href="<c:out value='${ctx}'/>/stock.do">在庫一覧へ</a>
    <c:if test="${canWAREHOUSE or canMANAGER or canBATCH}"><a class="button" href="<c:out value='${ctx}'/>/receipts.do?op=new&amp;warehouseId=<c:out value='${stock.warehouse.id}'/>&amp;productId=<c:out value='${stock.product.id}'/>">直接入庫</a></c:if><c:if test="${canWAREHOUSE or canMANAGER}"><a class="button" href="<c:out value='${ctx}'/>/adjustments.do?op=new&amp;warehouseId=<c:out value='${stock.warehouse.id}'/>&amp;productId=<c:out value='${stock.product.id}'/>">在庫調整</a></c:if>
  </div>
</section>
<section class="card"><h2>在庫移動履歴</h2>
  <form action="<c:out value='${ctx}'/>/stock.do" method="get" class="toolbar"><input type="hidden" name="op" value="detail"><input type="hidden" name="id" value="<c:out value='${form.id}'/>"><label>開始日<input type="date" name="from" value="<c:out value='${form.from}'/>"></label><label>終了日<input type="date" name="to" value="<c:out value='${form.to}'/>"></label><button class="primary">絞り込む</button></form>
  <div class="table-wrap"><table><thead><tr><th>日時 / 操作者</th><th>区分</th><th>伝票</th><th class="number">在庫増減</th><th class="number">引当増減</th><th class="number">移動後在庫 / 引当</th><th>理由・備考</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><fmt:formatDate value="${item.occurredAt}" pattern="yyyy-MM-dd HH:mm:ss"/><br><c:out value="${item.actor}"/></td><td><c:out value="${item.movementType}"/></td><td><c:out value="${item.documentType}"/><br><c:out value="${item.documentNumber}"/></td><td class="number"><c:out value="${item.quantityChange}"/></td><td class="number"><c:out value="${item.reservedChange}"/></td><td class="number"><c:out value="${item.onHandAfter}"/> / <c:out value="${item.reservedAfter}"/></td><td><c:out value="${item.note}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">対象期間の移動履歴はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
