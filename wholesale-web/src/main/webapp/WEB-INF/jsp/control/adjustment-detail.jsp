<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${adjustment.number}"/> <span class="badge"><c:out value="${adjustment.status}"/></span></h2>
  <dl><dt>倉庫 / 商品</dt><dd><c:out value="${adjustment.warehouse.name}"/> / <c:out value="${adjustment.product.code}"/> <c:out value="${adjustment.product.name}"/></dd><dt>増減数量</dt><dd><strong><c:out value="${adjustment.quantityChange}"/></strong></dd><dt>申請時原価 / 評価増減</dt><dd><fmt:formatNumber value="${adjustment.unitCost}" pattern="#,##0.00"/> / <fmt:formatNumber value="${adjustment.valueChange}" pattern="#,##0.00"/> 円</dd><dt>調整理由</dt><dd class="prewrap"><c:out value="${adjustment.reason}"/></dd><dt>申請者 / 日時</dt><dd><c:out value="${adjustment.proposedBy}"/> / <fmt:formatDate value="${adjustment.proposedAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>処理者 / 日時</dt><dd><c:out value="${adjustment.decidedBy}"/> / <fmt:formatDate value="${adjustment.decidedAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>処理理由</dt><dd><c:out value="${adjustment.decisionReason}"/></dd></dl>
</section>
<c:if test="${adjustment.status eq 'PROPOSED' and (canMANAGER or (canWAREHOUSE and adjustment.proposedById eq actor.userId))}">
  <section class="card"><h2>承認・却下・取消</h2><form action="<c:out value='${ctx}'/>/adjustments.do" method="post"><%@ include file="../fragments/identity.jspf" %><label>処理理由<textarea name="reason" maxlength="500" required><c:out value="${form.reason}"/></textarea></label><div class="actions"><c:if test="${canMANAGER and adjustment.proposedById ne actor.userId}"><button name="op" value="approve" class="primary">承認して在庫へ反映</button><button name="op" value="reject" class="danger">却下</button></c:if><button name="op" value="cancel">申請取消</button></div></form></section>
</c:if>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/adjustments.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
