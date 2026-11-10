<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${transfer.number}"/> <span class="badge"><c:out value="${transfer.status}"/></span></h2>
  <dl><dt>移送経路</dt><dd><c:out value="${transfer.sourceWarehouse.name}"/> → <c:out value="${transfer.destinationWarehouse.name}"/></dd><dt>備考</dt><dd class="prewrap"><c:out value="${transfer.note}"/></dd><dt>起票 / 承認 / 出庫</dt><dd><c:out value="${transfer.createdBy}"/> / <c:out value="${transfer.approvedBy}"/> / <c:out value="${transfer.dispatchedBy}"/></dd><dt>出庫日時</dt><dd><fmt:formatDate value="${transfer.dispatchedAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>取消理由</dt><dd><c:out value="${transfer.cancellationReason}"/></dd><dt>移送中数量 / 評価額</dt><dd><c:out value="${transfer.inTransitQuantity}"/> / <fmt:formatNumber value="${transfer.inTransitValue}" pattern="#,##0.00"/> 円</dd></dl>
</section>
<section class="card"><h2>移送明細</h2><div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">予定数</th><th class="number">出庫 / 受領 / 損失</th><th class="number">移送中</th><th class="number">出庫時原価</th></tr></thead><tbody>
  <c:forEach items="${transfer.lines}" var="line"><tr><td><c:out value="${line.product.code}"/> <c:out value="${line.product.name}"/></td><td class="number"><c:out value="${line.quantity}"/></td><td class="number"><c:out value="${line.dispatchedQuantity}"/> / <c:out value="${line.receivedQuantity}"/> / <c:out value="${line.lostQuantity}"/></td><td class="number"><c:out value="${line.inTransitQuantity}"/></td><td class="number"><fmt:formatNumber value="${line.unitCost}" pattern="#,##0.00"/></td></tr></c:forEach>
</tbody></table></div></section>
<c:if test="${transfer.status eq 'DRAFT' or transfer.status eq 'SUBMITTED' or transfer.status eq 'APPROVED'}">
  <section class="card"><h2>申請・承認・出庫</h2><form action="<c:out value='${ctx}'/>/transfers.do" method="post">
    <%@ include file="../fragments/identity.jspf" %><label>取消理由<textarea name="reason" maxlength="500"><c:out value="${form.reason}"/></textarea></label>
    <div class="actions">
      <c:if test="${(canWAREHOUSE or canMANAGER) and transfer.status eq 'DRAFT'}"><a class="button" href="<c:out value='${ctx}'/>/transfers.do?op=edit&amp;id=<c:out value='${transfer.id}'/>">編集</a><button name="op" value="submit" class="primary">申請</button></c:if>
      <c:if test="${canMANAGER and transfer.status eq 'SUBMITTED' and transfer.createdById ne actor.userId}"><button name="op" value="approve" class="primary">承認</button></c:if>
      <c:if test="${(canWAREHOUSE or canBATCH) and transfer.status eq 'APPROVED'}"><button name="op" value="dispatch" class="primary">元倉庫から出庫</button></c:if>
      <c:if test="${canWAREHOUSE or canMANAGER}"><button name="op" value="cancel" class="danger">移送を取消</button></c:if>
    </div>
  </form></section>
</c:if>
<c:if test="${(transfer.status eq 'IN_TRANSIT' or transfer.status eq 'PART_RECEIVED') and (canWAREHOUSE or canBATCH or canMANAGER)}">
  <section class="card"><h2>分割受領・移送損失</h2><p class="help">今回受領した数量のみ入力します。未処理行は0。管理者は移送中残数の損失を理由付きで確定できます。損失処理では在庫を二重に減少させません。</p>
    <form action="<c:out value='${ctx}'/>/transfers.do" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="requestKey" value="<c:out value='${form.requestKey}'/>">
      <div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">移送中残数</th><th>今回数量</th></tr></thead><tbody><c:forEach items="${transfer.lines}" var="line" varStatus="row"><tr><td><c:out value="${line.product.code}"/> <c:out value="${line.product.name}"/><input type="hidden" name="productId" value="<c:out value='${line.product.id}'/>"></td><td class="number"><c:out value="${line.inTransitQuantity}"/></td><td><input type="text" name="quantity" inputmode="numeric" maxlength="9" aria-label="今回数量" value="<c:out value='${form.quantity[row.index]}'/>"></td></tr></c:forEach></tbody></table></div>
      <label>受領備考・損失理由<textarea name="note" maxlength="500"><c:out value="${form.note}"/></textarea></label>
      <div class="actions"><c:if test="${canWAREHOUSE or canBATCH}"><button name="op" value="receive" class="primary">この数量を受領</button></c:if><c:if test="${canMANAGER}"><button name="op" value="loss" class="danger">この数量を移送損失として確定</button></c:if></div>
    </form>
  </section>
</c:if>
<section class="card"><h2>受領・損失の履歴</h2><div class="table-wrap"><table><thead><tr><th>処理伝票 / 区分</th><th>日時 / 操作者</th><th>明細</th><th>備考 / 受付キー</th></tr></thead><tbody>
  <c:forEach items="${results.items}" var="event"><tr><td><c:out value="${event.number}"/><br><c:out value="${event.kind}"/></td><td><fmt:formatDate value="${event.createdAt}" pattern="yyyy-MM-dd HH:mm"/><br><c:out value="${event.createdBy}"/></td><td><c:forEach items="${event.lines}" var="line"><c:out value="${line.transferLine.product.code}"/> × <c:out value="${line.quantity}"/><br></c:forEach></td><td><c:out value="${event.note}"/><br><span class="mono"><c:out value="${event.requestKey}"/></span></td></tr></c:forEach>
  <c:if test="${empty results.items}"><tr><td colspan="4" class="empty">受領・損失の履歴はありません。</td></tr></c:if>
</tbody></table></div><%@ include file="../fragments/pagination.jspf" %></section>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/transfers.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
