<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${shipment.number}"/> <span class="badge"><c:out value="${shipment.status}"/></span></h2>
  <dl><dt>受注 / 得意先</dt><dd><a href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${shipment.order.id}'/>"><c:out value="${shipment.order.number}"/></a> · <c:out value="${shipment.order.customerName}"/></dd>
    <dt>出荷予定日 / 実績日</dt><dd><fmt:formatDate value="${shipment.plannedDate}" pattern="yyyy-MM-dd"/> / <fmt:formatDate value="${shipment.shippedDate}" pattern="yyyy-MM-dd"/></dd>
    <dt>運送会社 / 送り状</dt><dd><c:out value="${shipment.carrier}"/> / <c:out value="${shipment.trackingNumber}"/></dd><dt>備考</dt><dd class="prewrap"><c:out value="${shipment.note}"/></dd>
    <dt>指示者 / 日時</dt><dd><c:out value="${shipment.createdBy}"/> / <fmt:formatDate value="${shipment.createdAt}" pattern="yyyy-MM-dd HH:mm"/></dd>
    <dt>確定者 / 日時</dt><dd><c:out value="${shipment.confirmedBy}"/> / <fmt:formatDate value="${shipment.confirmedAt}" pattern="yyyy-MM-dd HH:mm"/></dd>
    <dt>取消理由</dt><dd><c:out value="${shipment.cancellationReason}"/></dd>
    <dt>請求書</dt><dd><c:choose><c:when test="${not empty shipment.invoice and (canBILLING or canMANAGER)}"><a href="<c:out value='${ctx}'/>/invoices.do?op=detail&amp;id=<c:out value='${shipment.invoice.id}'/>"><c:out value="${shipment.invoice.number}"/></a></c:when><c:when test="${not empty shipment.invoice}">請求書作成済み</c:when><c:otherwise>未請求</c:otherwise></c:choose></dd>
  </dl>
</section>
<section class="card"><h2>出荷明細</h2>
  <div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">出荷数</th><th class="number">返品済数</th><th class="number">単価</th><th>税率</th><th class="number">税抜金額</th></tr></thead><tbody>
    <c:forEach items="${shipment.lines}" var="line"><tr><td><c:out value="${line.productCode}"/> <c:out value="${line.productName}"/></td><td class="number"><c:out value="${line.quantity}"/> <c:out value="${line.unit}"/></td><td class="number"><c:out value="${line.returnedQuantity}"/></td><td class="number"><fmt:formatNumber value="${line.unitPrice}" pattern="#,##0.00"/></td><td><fmt:formatNumber value="${line.taxRate}" type="percent"/></td><td class="number"><fmt:formatNumber value="${line.netAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
  </tbody></table></div>
  <dl class="totals"><dt>税抜 / 税額</dt><dd><fmt:formatNumber value="${shipment.netAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${shipment.taxAmount}" pattern="#,##0.00"/></dd><dt>税込合計</dt><dd><fmt:formatNumber value="${shipment.totalAmount}" pattern="#,##0.00"/> 円</dd></dl>
</section>
<c:if test="${shipment.status eq 'INSTRUCTED' and (canWAREHOUSE or canMANAGER or canBATCH)}">
  <section class="card"><h2>出荷確定・指示取消</h2><p class="help">出荷確定は在庫を減少させます。指示取消は在庫引当を維持し、受注から再指示できます。</p>
    <form action="<c:out value='${ctx}'/>/shipments.do" method="post"><%@ include file="../fragments/identity.jspf" %>
      <div class="field-grid"><label>実際の出荷日<input type="date" name="shippedDate" value="<c:out value='${form.shippedDate}'/>"></label><label>送り状番号<input type="text" name="trackingNumber" maxlength="100" value="<c:out value='${form.trackingNumber}'/>"></label><label class="wide">指示取消理由<textarea name="reason" maxlength="1000"><c:out value="${form.reason}"/></textarea></label></div>
      <div class="actions"><c:if test="${canWAREHOUSE or canBATCH}"><button name="op" value="confirm" class="primary">出荷を確定</button></c:if><c:if test="${canWAREHOUSE or canMANAGER}"><button name="op" value="cancel" class="danger">指示を取消</button></c:if></div>
    </form>
  </section>
</c:if>
<div class="actions"><c:if test="${shipment.status eq 'CONFIRMED' and (canSALES or canWAREHOUSE or canMANAGER)}"><a class="button" href="<c:out value='${ctx}'/>/returns.do?op=new&amp;shipmentId=<c:out value='${shipment.id}'/>">この出荷から返品申請</a></c:if><c:if test="${shipment.status eq 'CONFIRMED'}"><a class="button" href="<c:out value='${ctx}'/>/deliveryAttempts.do?op=detail&amp;shipmentId=<c:out value='${shipment.id}'/>">配送手動記録・履歴</a></c:if><a class="button" href="<c:out value='${ctx}'/>/shipments.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
