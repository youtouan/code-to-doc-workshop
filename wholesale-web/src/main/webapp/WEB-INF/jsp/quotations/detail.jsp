<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${quote.number}"/> <span class="badge"><c:out value="${quote.status}"/></span></h2>
  <dl><dt>現在版 / 更新番号</dt><dd>第<c:out value="${quote.revisionNumber}"/>版 / <c:out value="${quote.version}"/></dd><dt>起票者 / 申請者 / 承認者</dt><dd><c:out value="${quote.createdBy}"/> / <c:out value="${quote.submittedBy}"/> / <c:out value="${quote.approvedBy}"/></dd><dt>顧客承諾日 / 参照番号</dt><dd><fmt:formatDate value="${quote.acceptedOn}" pattern="yyyy-MM-dd"/> / <c:out value="${quote.acceptanceReference}"/></dd><dt>承諾の記録担当 / 記録日時</dt><dd><c:out value="${quote.acceptedBy}"/> / <fmt:formatDate value="${quote.acceptedAt}" pattern="yyyy-MM-dd HH:mm"/></dd><dt>変換先受注</dt><dd><c:if test="${not empty quote.convertedOrder}"><a href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${quote.convertedOrder.id}'/>"><c:out value="${quote.convertedOrder.number}"/></a></c:if></dd></dl>
  <p class="help">顧客承諾は日付・参照番号を入力する社内記録です。電子署名、顧客本人確認、外部への送信・検証は行いません。</p>
  <c:if test="${quote.validUntil lt today and quote.status ne 'CONVERTED' and quote.status ne 'CANCELLED'}"><p class="notice">有効期限を過ぎています。必要なら改訂・再承認・再承諾してください。</p></c:if>
</section>
<%@ include file="../fragments/quotation-revision.jspf" %>
<section class="card"><h2>見積操作</h2><form action="<c:out value='${ctx}'/>/quotations.do" method="post"><%@ include file="../fragments/identity.jspf" %>
  <label>取下げ・差戻し・取消理由<textarea name="reason" maxlength="500"><c:out value="${form.reason}"/></textarea></label>
  <div class="actions">
    <c:if test="${canSALES or canMANAGER}">
      <c:if test="${quote.editable}"><a class="button" href="<c:out value='${ctx}'/>/quotations.do?op=edit&amp;id=<c:out value='${quote.id}'/>">下書きを編集</a><c:if test="${quote.validUntil ge today}"><button name="op" value="submit" class="primary">承認申請</button></c:if></c:if>
      <c:if test="${quote.status ne 'CONVERTED' and quote.status ne 'CANCELLED'}"><a class="button" href="<c:out value='${ctx}'/>/quotations.do?op=reviseForm&amp;id=<c:out value='${quote.id}'/>">理由付きで改訂</a></c:if>
      <c:if test="${(canMANAGER or quote.createdById eq actor.userId) and (quote.status eq 'SUBMITTED' or quote.status eq 'APPROVED' or quote.status eq 'ACCEPTED')}"><button name="op" value="withdraw">取下げ</button></c:if>
      <c:if test="${(canMANAGER or quote.createdById eq actor.userId) and quote.status ne 'CONVERTED' and quote.status ne 'CANCELLED'}"><button name="op" value="cancel" class="danger">取消</button></c:if>
      <c:if test="${quote.status eq 'ACCEPTED' and quote.validUntil ge today and quote.requestedDate ge today}"><button name="op" value="convert" class="primary">承認済み版を受注下書きへ変換</button></c:if>
    </c:if>
    <c:if test="${canMANAGER and quote.status eq 'SUBMITTED'}"><c:if test="${quote.createdById ne actor.userId and quote.submittedById ne actor.userId and quoteRevision.authoredById ne actor.userId and quote.validUntil ge today}"><button name="op" value="approve" class="primary">現在版を独立承認</button></c:if><button name="op" value="reject">差戻し</button></c:if>
    <c:if test="${(canSALES or canMANAGER or canBATCH) and quote.validUntil lt today and quote.status ne 'CONVERTED' and quote.status ne 'CANCELLED' and quote.status ne 'EXPIRED'}"><button name="op" value="expire">期限切れとして終了</button></c:if>
  </div>
</form><p class="help">受注変換は同じ見積から1件だけです。承認版の価格・税率を引き継いだDRAFT受注を作成し、通常の受注申請・承認へ進みます。受注下書きを別途編集する場合は通常の再価格計算ルールが適用されます。</p></section>
<c:if test="${(canSALES or canMANAGER) and quote.status eq 'APPROVED' and quote.validUntil ge today}">
  <section class="card"><h2>顧客承諾の手動記録</h2><form action="<c:out value='${ctx}'/>/quotations.do" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="op" value="accept">
    <div class="field-grid"><label>顧客承諾日<input type="date" name="acceptedOn" value="<c:out value='${form.acceptedOn}'/>" required></label><label>顧客承諾参照番号<input type="text" name="customerReference" maxlength="120" value="<c:out value='${form.customerReference}'/>" required></label></div><div class="actions"><button class="primary">承諾日・参照番号を記録</button></div>
  </form></section>
</c:if>
<section class="card"><h2>変更不能な版履歴</h2><div class="table-wrap"><table><thead><tr><th>版</th><th>見積日 / 有効期限</th><th class="number">税込金額</th><th>作成者 / 日時</th><th>理由</th></tr></thead><tbody><c:forEach items="${quote.revisions}" var="revision"><tr><td><a href="<c:out value='${ctx}'/>/quotations.do?op=revision&amp;id=<c:out value='${quote.id}'/>&amp;revisionNumber=<c:out value='${revision.revisionNumber}'/>">第<c:out value="${revision.revisionNumber}"/>版</a></td><td><fmt:formatDate value="${revision.quoteDate}" pattern="yyyy-MM-dd"/> / <fmt:formatDate value="${revision.validUntil}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${revision.totalAmount}" pattern="#,##0.00"/></td><td><c:out value="${revision.authoredBy}"/><br><fmt:formatDate value="${revision.authoredAt}" pattern="yyyy-MM-dd HH:mm"/></td><td><c:out value="${revision.changeReason}"/></td></tr></c:forEach></tbody></table></div></section>
<section class="card"><h2>状態遷移・操作履歴</h2><div class="table-wrap"><table><thead><tr><th>日時 / 担当者</th><th>版 / 操作</th><th>遷移</th><th>理由・参照</th></tr></thead><tbody><c:forEach items="${events}" var="event"><tr><td><fmt:formatDate value="${event.occurredAt}" pattern="yyyy-MM-dd HH:mm:ss"/><br><c:out value="${event.actor}"/></td><td>第<c:out value="${event.revisionNumber}"/>版 / <c:out value="${event.operation}"/></td><td><c:out value="${event.fromStatus}"/> → <c:out value="${event.toStatus}"/></td><td><c:out value="${event.reason}"/></td></tr></c:forEach></tbody></table></div></section>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/quotations.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
