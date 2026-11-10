<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <p class="help">得意先・商品・期間・最低数量に応じた契約単価です。受注時の価格は、その伝票に保存されます。</p>
  <form action="<c:out value='${ctx}'/>/prices.do" method="get" class="toolbar">
    <label>得意先<select name="customerId" required><option value="">選択してください</option><c:forEach items="${customers}" var="c"><option value="<c:out value='${c.id}'/>" <c:if test="${form.customerId eq fn:escapeXml(c.id)}">selected</c:if>><c:out value="${c.code}"/> <c:out value="${c.name}"/></option></c:forEach></select></label>
    <button type="submit" class="primary">価格条件を表示</button>
    <c:if test="${canMANAGER}"><a class="button" href="<c:out value='${ctx}'/>/prices.do?op=new&amp;customerId=<c:out value='${form.customerId}'/>">条件追加</a></c:if>
  </form>
  <div class="table-wrap"><table><thead><tr><th>商品</th><th>適用期間</th><th class="number">最低数量</th><th class="number">契約単価</th><th>備考</th><th>操作</th></tr></thead><tbody>
    <c:forEach items="${agreements}" var="item"><tr><td><c:out value="${item.product.code}"/> <c:out value="${item.product.name}"/></td><td><fmt:formatDate value="${item.validFrom}" pattern="yyyy-MM-dd"/> ～ <fmt:formatDate value="${item.validTo}" pattern="yyyy-MM-dd"/></td><td class="number"><c:out value="${item.minimumQuantity}"/></td><td class="number"><fmt:formatNumber value="${item.unitPrice}" pattern="#,##0.00"/></td><td><c:out value="${item.notes}"/></td><td><c:if test="${canMANAGER}"><a href="<c:out value='${ctx}'/>/prices.do?op=edit&amp;id=<c:out value='${item.id}'/>&amp;customerId=<c:out value='${form.customerId}'/>">編集</a></c:if></td></tr></c:forEach>
    <c:if test="${empty agreements}"><tr><td colspan="6" class="empty">得意先を選択してください。価格条件がない場合は標準売価が適用されます。</td></tr></c:if>
  </tbody></table></div>
</section>
<%@ include file="../fragments/footer.jspf" %>
