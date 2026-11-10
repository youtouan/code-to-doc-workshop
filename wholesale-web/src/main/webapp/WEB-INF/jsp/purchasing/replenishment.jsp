<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>補充候補の計算</h2><p class="help">現在庫・引当・発注残を考慮し、発注点と調達条件から補充数量を提案します。この画面の表示だけでは発注されません。</p>
  <form action="<c:out value='${ctx}'/>/replenishment.do" method="get" class="toolbar"><label>倉庫<select name="warehouseId" required><option value="">選択してください</option><c:forEach items="${warehouses}" var="w"><option value="<c:out value='${w.id}'/>" <c:if test="${form.warehouseId eq fn:escapeXml(w.id)}">selected</c:if>><c:out value="${w.name}"/></option></c:forEach></select></label><label>基準日<input type="date" name="from" value="<c:out value='${form.from}'/>" required></label><button class="primary">候補を計算</button></form>
  <div class="table-wrap"><table><thead><tr><th>商品 / 推奨仕入先</th><th class="number">在庫 / 引当 / 発注残</th><th class="number">予測可能数</th><th class="number">推奨数量 / 単価</th><th>入荷予定 / 注意事項</th><th>操作</th></tr></thead><tbody>
    <c:forEach items="${suggestions}" var="item"><tr><td><c:out value="${item.product.code}"/> <c:out value="${item.product.name}"/><br><c:out value="${item.supplier.name}"/></td><td class="number"><c:out value="${item.onHand}"/> / <c:out value="${item.reserved}"/> / <c:out value="${item.openPurchaseQuantity}"/></td><td class="number"><c:out value="${item.projectedAvailable}"/></td><td class="number"><c:out value="${item.suggestedQuantity}"/><br><fmt:formatNumber value="${item.unitCost}" pattern="#,##0.00"/></td><td><fmt:formatDate value="${item.expectedDate}" pattern="yyyy-MM-dd"/><br><c:out value="${item.warning}"/></td><td>
      <c:if test="${(canWAREHOUSE or canMANAGER) and not empty item.supplier and item.suggestedQuantity gt 0}">
        <fmt:formatDate value="${item.expectedDate}" pattern="yyyy-MM-dd" var="expected"/>
        <c:url value="/purchases.do" var="newPurchase"><c:param name="op" value="new"/><c:param name="supplierId" value="${item.supplier.id}"/><c:param name="warehouseId" value="${item.warehouse.id}"/><c:param name="productId" value="${item.product.id}"/><c:param name="quantity" value="${item.suggestedQuantity}"/><c:param name="expectedDate" value="${expected}"/></c:url>
        <a class="button" href="<c:out value='${newPurchase}'/>">発注下書きへ</a>
      </c:if>
    </td></tr></c:forEach>
    <c:if test="${empty suggestions}"><tr><td colspan="6" class="empty">倉庫を指定してください。補充対象がない場合も空欄となります。</td></tr></c:if>
  </tbody></table></div>
</section>
<section class="card"><h2>入荷予定を超過した発注明細</h2><div class="table-wrap"><table><thead><tr><th>発注 / 仕入先</th><th>商品</th><th>入荷予定日</th><th class="number">発注残数</th></tr></thead><tbody>
  <c:forEach items="${overdueLines}" var="line"><tr><td><a href="<c:out value='${ctx}'/>/purchases.do?op=detail&amp;id=<c:out value='${line.order.id}'/>"><c:out value="${line.order.number}"/></a><br><c:out value="${line.order.supplierName}"/></td><td><c:out value="${line.productCode}"/> <c:out value="${line.productName}"/></td><td><fmt:formatDate value="${line.expectedDate}" pattern="yyyy-MM-dd"/></td><td class="number"><c:out value="${line.outstandingQuantity}"/></td></tr></c:forEach>
  <c:if test="${empty overdueLines}"><tr><td colspan="4" class="empty">対象倉庫の入荷遅延はありません。</td></tr></c:if>
</tbody></table></div></section>
<%@ include file="../fragments/footer.jspf" %>
