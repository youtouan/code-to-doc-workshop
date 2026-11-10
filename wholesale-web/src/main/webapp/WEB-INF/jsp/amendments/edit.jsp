<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${order.number}"/> · <c:out value="${order.customerName}"/></h2>
  <p class="help">変更する行だけ新しい総数量を入力してください。差分数量ではありません。空欄は数量変更なしです。1～1,000,000、受注時の入数の倍数、出荷済＋取消済以上が必要です。商品の追加・削除・単価・税率変更はできません。</p>
  <p class="help">申請は元受注・全明細の状態を保存します。承認前に別の変更・引当・出荷が発生すると再申請が必要です。未確定出荷指示の取消は倉庫担当者が明示的に行い、変更適用では自動取消しません。</p>
  <html:form action="/orderAmendments" method="post"><%@ include file="../fragments/token.jspf" %><html:hidden property="orderId"/><html:hidden property="expectedOrderVersion"/><input type="hidden" name="op" value="request">
    <div class="field-grid"><div><span class="field">現在の納期</span><p><fmt:formatDate value="${order.requestedDate}" pattern="yyyy-MM-dd"/></p></div><label>変更後納期（空欄は変更なし）<input type="date" name="requestedDate" value="<c:out value='${form.requestedDate}'/>"></label><label class="wide">変更理由（必須）<html:textarea property="reason" rows="3"/></label></div>
    <div class="table-wrap"><table><thead><tr><th>行 / 商品</th><th class="number">現在総数 / 引当</th><th class="number">出荷済 / 取消済</th><th>受注時入数</th><th class="number">保存済み単価 / 税率</th><th>変更後総数量</th></tr></thead><tbody>
      <c:forEach items="${order.lines}" var="line" varStatus="row"><tr><td><c:out value="${line.lineNumber}"/> · <c:out value="${line.productCode}"/> <c:out value="${line.productName}"/><input type="hidden" name="orderLineId" value="<c:out value='${line.id}'/>"></td><td class="number"><c:out value="${line.quantity}"/> / <c:out value="${line.allocatedQuantity}"/></td><td class="number"><c:out value="${line.shippedQuantity}"/> / <c:out value="${line.cancelledQuantity}"/></td><td><c:out value="${line.packSize}"/></td><td class="number"><fmt:formatNumber value="${line.unitPrice}" pattern="#,##0.00"/> / <fmt:formatNumber value="${line.taxRate}" type="percent"/></td><td><input type="text" name="targetQuantity" maxlength="7" inputmode="numeric" aria-label="変更後総数量" value="<c:out value='${form.targetQuantity[row.index]}'/>"></td></tr></c:forEach>
    </tbody></table></div><div class="actions"><button class="primary">変更を申請</button><a class="button" href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${order.id}'/>">受注へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
