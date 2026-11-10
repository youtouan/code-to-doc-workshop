<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<div class="grid">
  <section class="card"><h2><c:out value="${order.number}"/> <span class="badge"><c:out value="${order.status}"/></span></h2>
    <dl><dt>得意先</dt><dd><a href="<c:out value='${ctx}'/>/customers.do?op=detail&amp;id=<c:out value='${order.customer.id}'/>"><c:out value="${order.customerName}"/></a></dd>
      <dt>出荷倉庫</dt><dd><c:out value="${order.warehouse.name}"/></dd><dt>受注日 / 希望納期</dt><dd><fmt:formatDate value="${order.orderDate}" pattern="yyyy-MM-dd"/> / <fmt:formatDate value="${order.requestedDate}" pattern="yyyy-MM-dd"/></dd>
      <dt>納品先</dt><dd><c:out value="${order.deliveryAddress}"/></dd><dt>先方注文番号</dt><dd><c:out value="${order.externalReference}"/></dd><dt>備考</dt><dd class="prewrap"><c:out value="${order.notes}"/></dd>
    </dl>
  </section>
  <section class="card"><h2>金額・処理履歴</h2>
    <dl><dt>税抜金額</dt><dd class="number"><fmt:formatNumber value="${order.netAmount}" pattern="#,##0.00"/> 円</dd><dt>消費税</dt><dd class="number"><fmt:formatNumber value="${order.taxAmount}" pattern="#,##0.00"/> 円</dd><dt>受注合計</dt><dd class="number"><strong><fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/> 円</strong></dd>
      <dt>起票者 / 起票日時</dt><dd><c:out value="${order.createdBy}"/> / <fmt:formatDate value="${order.createdAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>承認者 / 承認日時</dt><dd><c:out value="${order.approvedBy}"/> / <fmt:formatDate value="${order.approvedAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>取消理由</dt><dd><c:out value="${order.cancellationReason}"/></dd><dt>更新番号</dt><dd><c:out value="${order.version}"/></dd>
    </dl>
  </section>
</div>
<section class="card"><h2>受注明細</h2>
  <div class="table-wrap"><table><thead><tr><th>行 / 商品</th><th class="number">受注数</th><th class="number">引当 / 出荷済</th><th class="number">未出荷 / 不足</th><th class="number">単価</th><th>税率</th><th class="number">税抜金額</th></tr></thead><tbody>
    <c:forEach items="${order.lines}" var="line"><tr><td><c:out value="${line.lineNumber}"/> · <c:out value="${line.productCode}"/><br><c:out value="${line.productName}"/><br><span class="muted">入数<c:out value="${line.packSize}"/> <c:out value="${line.unit}"/></span></td><td class="number"><c:out value="${line.quantity}"/></td><td class="number"><c:out value="${line.allocatedQuantity}"/> / <c:out value="${line.shippedQuantity}"/></td><td class="number"><c:out value="${line.openQuantity}"/> / <c:out value="${line.shortageQuantity}"/></td><td class="number"><fmt:formatNumber value="${line.unitPrice}" pattern="#,##0.00"/><br><c:out value="${line.priceReason}"/></td><td><fmt:formatNumber value="${line.taxRate}" type="percent"/></td><td class="number"><fmt:formatNumber value="${line.netAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
  </tbody></table></div>
</section>
<section class="card"><h2>受注操作</h2>
  <p class="help">差戻し・取下げ・取消・引当解除・納期変更には理由が必要です。引当は利用可能在庫の範囲で行われます。</p>
  <form action="<c:out value='${ctx}'/>/orders.do" method="post">
    <%@ include file="../fragments/identity.jspf" %>
    <div class="field-grid">
      <label>操作理由<textarea name="reason" maxlength="1000"><c:out value="${form.reason}"/></textarea></label>
      <label>変更後の希望納期<input type="date" name="requestedDate" value="<c:out value='${form.requestedDate}'/>"></label>
    </div>
    <div class="actions">
      <c:if test="${canSALES or canMANAGER}">
        <c:if test="${order.editable}"><a class="button" href="<c:out value='${ctx}'/>/orders.do?op=edit&amp;id=<c:out value='${order.id}'/>">下書き編集</a><button name="op" value="submit" class="primary">承認申請</button></c:if>
        <c:if test="${order.status eq 'SUBMITTED'}"><button name="op" value="withdraw">申請取下げ</button></c:if>
        <button name="op" value="copy">複写して新しい下書き</button>
        <c:if test="${order.status ne 'SHIPPED' and order.status ne 'CANCELLED' and order.status ne 'CLOSED_PARTIAL'}"><button name="op" value="cancel" class="danger">未出荷分を取消</button></c:if>
        <c:if test="${order.status eq 'APPROVED' or order.status eq 'PART_ALLOCATED' or order.status eq 'ALLOCATED' or order.status eq 'PART_SHIPPED'}"><button name="op" value="reschedule">納期変更</button><a class="button" href="<c:out value='${ctx}'/>/orderAmendments.do?op=new&amp;orderId=<c:out value='${order.id}'/>">数量・納期の独立承認付き変更申請</a></c:if>
      </c:if>
      <c:if test="${canMANAGER and order.status eq 'SUBMITTED' and order.createdBy ne actor.login}"><button name="op" value="approve" class="primary">承認</button><button name="op" value="reject">差戻し</button></c:if>
      <c:if test="${(canSALES or canWAREHOUSE or canMANAGER or canBATCH) and (order.status eq 'APPROVED' or order.status eq 'PART_ALLOCATED' or order.status eq 'ALLOCATED' or order.status eq 'PART_SHIPPED')}">
        <button name="op" value="allocate" class="primary">在庫を引当</button>
        <c:if test="${(canSALES or canWAREHOUSE or canMANAGER) and order.allocatedQuantity gt 0}"><button name="op" value="release">引当解除</button></c:if>
        <c:if test="${(canWAREHOUSE or canMANAGER) and order.allocatedQuantity gt 0}"><a class="button" href="<c:out value='${ctx}'/>/shipments.do?op=new&amp;orderId=<c:out value='${order.id}'/>">出荷指示を作成</a></c:if>
      </c:if>
      <a class="button" href="<c:out value='${ctx}'/>/orders.do">一覧へ</a>
    </div>
  </form>
</section>
<section class="card"><h2>関連する出荷</h2>
  <div class="table-wrap"><table><thead><tr><th>出荷番号</th><th>状態</th><th>予定日</th><th>出荷日</th></tr></thead><tbody>
    <c:forEach items="${shipments}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.status}"/></td><td><fmt:formatDate value="${item.plannedDate}" pattern="yyyy-MM-dd"/></td><td><fmt:formatDate value="${item.shippedDate}" pattern="yyyy-MM-dd"/></td></tr></c:forEach>
    <c:if test="${empty shipments}"><tr><td colspan="4" class="empty">出荷指示はまだありません。</td></tr></c:if>
  </tbody></table></div>
</section>
<section class="card"><h2>承認済み受注の変更申請履歴</h2>
  <div class="table-wrap"><table><thead><tr><th>変更申請番号</th><th>状態</th><th>申請者 / 日時</th><th>理由</th></tr></thead><tbody>
    <c:forEach items="${amendments}" var="amendment"><tr><td><a href="<c:out value='${ctx}'/>/orderAmendments.do?op=detail&amp;id=<c:out value='${amendment.id}'/>"><c:out value="${amendment.number}"/></a></td><td><c:out value="${amendment.status}"/></td><td><c:out value="${amendment.requestedBy}"/> / <fmt:formatDate value="${amendment.requestedAt}" pattern="yyyy-MM-dd HH:mm"/></td><td><c:out value="${amendment.reason}"/></td></tr></c:forEach>
    <c:if test="${empty amendments}"><tr><td colspan="4" class="empty">変更申請はありません。</td></tr></c:if>
  </tbody></table></div>
</section>
<%@ include file="../fragments/footer.jspf" %>
