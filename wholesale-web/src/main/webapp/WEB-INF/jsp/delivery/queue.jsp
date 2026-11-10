<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/delivery-notice.jspf" %>
<section class="card"><p class="help">既に報告された有効な配送結果のキューです。初期表示はFAILED・RESCHEDULEDで、未記録の出荷は含みません。新規報告は<a href="<c:out value='${ctx}'/>/shipments.do?status=CONFIRMED">確定済み出荷一覧</a>から開始してください。</p>
  <%@ include file="../fragments/delivery-filter.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>出荷 / 受注</th><th>得意先 / 倉庫</th><th>手動報告結果 / 試行日時</th><th>次回予定日</th><th>手入力管理番号</th><th class="number">出荷数量 / 現在返品受領数</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="summary"><c:url value="/deliveryAttempts.do" var="historyUrl"><c:param name="op" value="detail"/><c:param name="shipmentId" value="${summary.shipmentId}"/><c:param name="asOfRecordedAt" value="${form.asOfRecordedAt}"/></c:url><tr><td><a href="<c:out value='${historyUrl}'/>"><c:out value="${summary.shipmentNumber}"/></a><br><c:out value="${summary.orderNumber}"/></td><td><c:out value="${summary.customerName}"/><br><c:out value="${summary.warehouseCode}"/></td><td><c:out value="${summary.outcome}"/><br><fmt:formatDate value="${summary.attemptAt}" pattern="yyyy-MM-dd HH:mm:ss.SSS"/></td><td><fmt:formatDate value="${summary.nextAttemptDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${summary.carrier}"/> / <c:out value="${summary.trackingReference}"/></td><td class="number"><c:out value="${summary.shippedQuantity}"/> / <c:out value="${summary.currentReturnedQuantity}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する有効な手動報告はありません。配送完了や未配達の有無を保証する表示ではありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
