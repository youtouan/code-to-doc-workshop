<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${receipt.number}"/></h2>
  <dl><dt>入庫日</dt><dd><fmt:formatDate value="${receipt.receiptDate}" pattern="yyyy-MM-dd"/></dd><dt>倉庫</dt><dd><c:out value="${receipt.warehouse.name}"/></dd><dt>商品</dt><dd><c:out value="${receipt.product.code}"/> <c:out value="${receipt.product.name}"/></dd><dt>数量</dt><dd><c:out value="${receipt.quantity}"/></dd><dt>入庫単価</dt><dd><fmt:formatNumber value="${receipt.unitCost}" pattern="#,##0.00"/> 円</dd><dt>参照番号</dt><dd><c:out value="${receipt.reference}"/></dd><dt>備考</dt><dd class="prewrap"><c:out value="${receipt.note}"/></dd><dt>登録者 / 日時</dt><dd><c:out value="${receipt.createdBy}"/> / <fmt:formatDate value="${receipt.createdAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>受付キー</dt><dd class="mono"><c:out value="${receipt.requestKey}"/></dd></dl>
  <div class="actions"><a class="button" href="<c:out value='${ctx}'/>/receipts.do">一覧へ</a><a class="button" href="<c:out value='${ctx}'/>/stock.do?warehouseId=<c:out value='${receipt.warehouse.id}'/>">倉庫の在庫を確認</a></div>
</section>
<%@ include file="../fragments/footer.jspf" %>
