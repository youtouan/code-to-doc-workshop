<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${salesReturn.number}"/> <span class="badge"><c:out value="${salesReturn.status}"/></span></h2>
  <dl><dt>元出荷</dt><dd><a href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${salesReturn.shipment.id}'/>"><c:out value="${salesReturn.shipment.number}"/></a> · <c:out value="${salesReturn.shipment.order.customerName}"/></dd><dt>返品理由</dt><dd><c:out value="${salesReturn.reason}"/></dd><dt>申請日 / 受領日</dt><dd><fmt:formatDate value="${salesReturn.requestedDate}" pattern="yyyy-MM-dd"/> / <fmt:formatDate value="${salesReturn.receivedDate}" pattern="yyyy-MM-dd"/></dd><dt>申請者 / 承認者 / 受領者</dt><dd><c:out value="${salesReturn.createdBy}"/> / <c:out value="${salesReturn.approvedBy}"/> / <c:out value="${salesReturn.receivedBy}"/></dd><dt>備考・処理理由</dt><dd class="prewrap"><c:out value="${salesReturn.notes}"/></dd></dl>
</section>
<section class="card"><h2>返品明細</h2>
  <div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">返品数</th><th>在庫復帰</th><th class="number">単価</th><th class="number">税抜金額</th></tr></thead><tbody>
    <c:forEach items="${salesReturn.lines}" var="line"><tr><td><c:out value="${line.productCode}"/> <c:out value="${line.productName}"/></td><td class="number"><c:out value="${line.quantity}"/></td><td><c:out value="${line.restock ? '販売可能在庫へ復帰' : '在庫復帰なし'}"/></td><td class="number"><fmt:formatNumber value="${line.unitPrice}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${line.netAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
  </tbody></table></div>
  <p class="help">受領は全明細一括です。受領済み返品の請求調整は請求業務の返品クレジットに反映されます。</p>
</section>
<c:if test="${salesReturn.status eq 'REQUESTED' or salesReturn.status eq 'APPROVED'}">
  <section class="card"><h2>返品操作</h2><form action="<c:out value='${ctx}'/>/returns.do" method="post">
    <%@ include file="../fragments/identity.jspf" %>
    <div class="field-grid"><label>却下・取消理由<textarea name="reason" maxlength="1000"><c:out value="${form.reason}"/></textarea></label><label>返品受領日<input type="date" name="receivedDate" value="<c:out value='${form.receivedDate}'/>"></label></div>
    <div class="actions">
      <c:if test="${canMANAGER and salesReturn.status eq 'REQUESTED' and salesReturn.createdBy ne actor.login}"><button name="op" value="approve" class="primary">返品を承認</button></c:if>
      <c:if test="${canMANAGER and salesReturn.status eq 'REQUESTED'}"><button name="op" value="reject" class="danger">申請を却下</button></c:if>
      <c:if test="${canWAREHOUSE and salesReturn.status eq 'APPROVED'}"><button name="op" value="receive" class="primary">全明細を受領確定</button></c:if>
      <c:if test="${canSALES or canWAREHOUSE or canMANAGER}"><button name="op" value="cancel" class="danger">返品を取消</button></c:if>
    </div>
  </form></section>
</c:if>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/returns.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
