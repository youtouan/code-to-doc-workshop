<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/delivery-notice.jspf" %>
<section class="card"><h2><c:out value="${deliverySummary.shipmentNumber}"/> · <c:out value="${deliverySummary.customerName}"/></h2>
  <p class="help">実出荷日 <fmt:formatDate value="${deliverySummary.shippedBusinessDate}" pattern="yyyy-MM-dd"/> 以降、現在以前の実際の試行日時を入力してください。RESCHEDULEDの次回予定日は試行翌日～90日後です。他の結果では次回予定日を空欄にします。</p>
  <c:if test="${not empty form.targetEventId}"><p>訂正対象記録ID：<strong><c:out value="${form.targetEventId}"/></strong>。対象は保存済み記録から読み込みました。以下は新しく追記する訂正内容です。元の記録は書き換えません。</p></c:if>
  <html:form action="/deliveryAttempts" method="post"><%@ include file="../fragments/token.jspf" %><html:hidden property="shipmentId"/><html:hidden property="targetEventId"/><html:hidden property="expectedLatestEventId"/><html:hidden property="requestKey"/>
    <input type="hidden" name="op" value="<c:out value='${empty form.targetEventId ? "record" : "correct"}'/>">
    <div class="field-grid"><label>実際の配送試行日時（日本時間）<input type="datetime-local" step="0.001" name="attemptAt" value="<c:out value='${form.attemptAt}'/>" required></label>
      <label>手動報告結果<html:select property="outcome"><html:option value="">選択してください</html:option><html:option value="DELIVERED">DELIVERED — 配達完了との報告</html:option><html:option value="FAILED">FAILED — 配送失敗との報告</html:option><html:option value="RESCHEDULED">RESCHEDULED — 再配送予定との報告</html:option></html:select></label>
      <label>報告会社名（個人情報を含めない）<html:text property="reportingCompany" maxlength="120"/></label><label>手入力の報告参照番号<html:text property="evidenceReference" maxlength="120"/></label>
      <label>報告理由（FAILED・RESCHEDULEDは必須）<html:textarea property="reason" rows="3"/></label><label>次回配送予定日（RESCHEDULEDのみ）<input type="date" name="nextAttemptDate" value="<c:out value='${form.nextAttemptDate}'/>"></label>
      <c:if test="${not empty form.targetEventId}"><label class="wide">訂正理由（必須）<html:textarea property="correctionReason" rows="3"/></label></c:if>
    </div>
    <p class="help">同じ処理キーの再送は元の記録を返します。記録中に履歴が更新された場合は、入力を控えて最新履歴を開き直してください。保存後の記録は上書き・削除できません。</p>
    <div class="actions"><button class="primary"><c:out value="${empty form.targetEventId ? '手動報告を記録' : '訂正イベントを追加'}"/></button><a class="button" href="<c:out value='${ctx}'/>/deliveryAttempts.do?op=detail&amp;shipmentId=<c:out value='${form.shipmentId}'/>">最新履歴へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
