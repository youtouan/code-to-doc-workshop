<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>受注 <c:out value="${order.number}"/> · <c:out value="${order.customerName}"/></h2>
  <p class="help">引当済み数量の範囲で出荷する数量を指定します。一部の商品だけの出荷も可能です。出荷しない行は0または空欄にしてください。既存の未確定指示数量も検証されます。</p>
  <html:form action="/shipments" method="post">
    <%@ include file="../fragments/token.jspf" %><html:hidden property="orderId"/><html:hidden property="version"/><input type="hidden" name="op" value="instruct">
    <div class="field-grid"><label>出荷予定日<input type="date" name="plannedDate" value="<c:out value='${form.plannedDate}'/>" required></label>
      <label>運送会社<select name="carrier" required><option value="">選択してください</option><c:forEach items="${carriers}" var="c"><option value="<c:out value='${c.key}'/>" <c:if test="${form.carrier eq c.key}">selected</c:if>><c:out value="${c.value}"/></option></c:forEach></select></label>
      <label class="wide">出荷備考<html:textarea property="note" rows="3"/></label>
    </div>
    <div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">新規指示可能数</th><th>出荷指示数量</th><th>入数</th></tr></thead><tbody>
      <c:forEach items="${order.lines}" var="line" varStatus="row"><tr><td><c:out value="${line.productCode}"/> <c:out value="${line.productName}"/><input type="hidden" name="lineId" value="<c:out value='${line.id}'/>"></td><td class="number"><c:out value="${instructionCapacity[line.id]}"/></td><td><input type="text" name="quantity" inputmode="numeric" maxlength="9" aria-label="出荷指示数量" value="<c:out value='${form.quantity[row.index]}'/>"></td><td><c:out value="${line.packSize}"/> <c:out value="${line.unit}"/></td></tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button type="submit" class="primary">出荷指示を作成</button><a class="button" href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${order.id}'/>">受注へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
