<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>出荷 <c:out value="${shipment.number}"/> · <c:out value="${shipment.order.customerName}"/></h2>
  <p class="help">返品しない明細は0にしてください。破損・期限切れの場合は在庫復帰できません。未処理の返品申請も含め、出荷数を超えないよう検証します。</p>
  <html:form action="/returns" method="post">
    <%@ include file="../fragments/token.jspf" %><html:hidden property="shipmentId"/><input type="hidden" name="op" value="request">
    <div class="field-grid"><label>返品理由<html:select property="returnReason"><html:option value="CUSTOMER_CHANGE">得意先都合</html:option><html:option value="MISSHIP">誤出荷</html:option><html:option value="DAMAGED">破損</html:option><html:option value="EXPIRED">期限切れ</html:option></html:select></label><label>返品備考<html:textarea property="notes" rows="3"/></label></div>
    <div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">出荷済 / 返品済</th><th>返品申請数</th><th>受領時の在庫処理</th></tr></thead><tbody>
      <c:forEach items="${shipment.lines}" var="line" varStatus="row"><tr><td><c:out value="${line.productCode}"/> <c:out value="${line.productName}"/><input type="hidden" name="lineId" value="<c:out value='${line.id}'/>"></td><td class="number"><c:out value="${line.quantity}"/> / <c:out value="${line.returnedQuantity}"/></td><td><input type="text" name="quantity" maxlength="9" inputmode="numeric" aria-label="返品申請数" value="<c:out value='${form.quantity[row.index]}'/>"></td><td><select name="restock" aria-label="在庫処理"><option value="false" <c:if test="${form.restock[row.index] ne 'true'}">selected</c:if>>在庫に戻さない</option><option value="true" <c:if test="${form.restock[row.index] eq 'true'}">selected</c:if>>販売可能在庫へ戻す</option></select></td></tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button type="submit" class="primary">返品を申請</button><a class="button" href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${shipment.id}'/>">出荷へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
