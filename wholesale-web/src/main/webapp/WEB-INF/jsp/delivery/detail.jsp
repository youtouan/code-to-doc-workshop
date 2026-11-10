<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/delivery-notice.jspf" %>
<section class="card"><h2><c:out value="${deliverySummary.shipmentNumber}"/> · <c:out value="${deliverySummary.customerName}"/></h2>
  <dl><dt>有効な手動報告結果</dt><dd><c:out value="${deliverySummary.outcome}"/>（運送会社による検証なし）</dd><dt>その試行日時</dt><dd><fmt:formatDate value="${deliverySummary.attemptAt}" pattern="yyyy-MM-dd HH:mm:ss.SSS"/></dd><dt>次回予定日</dt><dd><fmt:formatDate value="${deliverySummary.nextAttemptDate}" pattern="yyyy-MM-dd"/></dd><dt>記録締切日時</dt><dd><c:choose><c:when test="${historical}"><fmt:formatDate value="${deliverySummary.asOfRecordedAt}" pattern="yyyy-MM-dd HH:mm:ss.SSS"/></c:when><c:otherwise>現在の記録</c:otherwise></c:choose></dd><dt>履歴の最新記録ID</dt><dd><c:out value="${deliverySummary.latestEventId}" default="未記録"/></dd><dt>出荷数量</dt><dd><c:out value="${deliverySummary.shippedQuantity}"/></dd><dt>現在の返品受領数量</dt><dd><c:out value="${deliverySummary.currentReturnedQuantity}"/>（記録締切時点の数量ではありません。未配達数量は推定しません）</dd><dt>配送区分 / 手入力番号</dt><dd><c:out value="${deliverySummary.carrier}"/> / <c:out value="${deliverySummary.trackingReference}"/></dd></dl>
  <p class="help">記録締切までに記録されていた訂正・取消を反映し、有効な報告のうち試行日時が最も遅いものを表示します。後日登録された過去の試行が最新結果を不当に上書きするものではありません。</p>
  <div class="actions"><c:if test="${not historical and (canWAREHOUSE or canSALES or canBATCH) and not empty deliverySummary.shippedBusinessDate}"><a class="button primary" href="<c:out value='${ctx}'/>/deliveryAttempts.do?op=new&amp;shipmentId=<c:out value='${deliverySummary.shipmentId}'/>">新しい手動報告</a></c:if><a class="button" href="<c:out value='${ctx}'/>/deliveryAttempts.do?op=detail&amp;shipmentId=<c:out value='${deliverySummary.shipmentId}'/>">現在の履歴を再表示</a><a class="button" href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${deliverySummary.shipmentId}'/>">元出荷へ</a></div>
</section>
<section class="card"><h2>追記専用の全履歴</h2><p class="help">下の検索条件は履歴行を絞り込みます。上の有効結果は記録締切に基づきます。過去の記録締切を指定した画面は読取専用です。既に訂正・取消された報告への再訂正・再取消はサービスが拒否します。</p>
  <%@ include file="../fragments/delivery-filter.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>記録ID / 通番 / 種別</th><th>試行日時 / 結果 / 次回予定</th><th>報告会社 / 参照番号 / 理由</th><th>記録日時 / 担当者</th><th>訂正・取消先 / 理由</th><th>管理者操作</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="event"><tr>
      <td class="mono">#<c:out value="${event.id}"/> / <c:out value="${event.sequenceNumber}"/><br><c:out value="${event.eventType}"/></td>
      <td><fmt:formatDate value="${event.attemptAt}" pattern="yyyy-MM-dd HH:mm:ss.SSS"/><br><c:out value="${event.outcome}"/><br><fmt:formatDate value="${event.nextAttemptDate}" pattern="yyyy-MM-dd"/></td>
      <td><c:out value="${event.reportingCompany}"/><br><c:out value="${event.evidenceReference}"/><br><c:out value="${event.reason}"/></td>
      <td><fmt:formatDate value="${event.recordedAt}" pattern="yyyy-MM-dd HH:mm:ss.SSS"/><br><c:out value="${event.recordedBy}"/></td>
      <td><c:out value="${event.supersedesId}"/><br><c:out value="${event.correctionReason}"/></td>
      <td><c:if test="${canMANAGER and not historical and event.eventType ne 'REVERSAL'}">
        <form action="<c:out value='${ctx}'/>/deliveryAttempts.do" method="post">
          <%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="editCorrection"><input type="hidden" name="shipmentId" value="<c:out value='${form.shipmentId}'/>"><input type="hidden" name="targetEventId" value="<c:out value='${event.id}'/>">
          <button>この記録を訂正入力</button>
        </form>
        <form action="<c:out value='${ctx}'/>/deliveryAttempts.do" method="post">
          <%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="reverse"><input type="hidden" name="shipmentId" value="<c:out value='${form.shipmentId}'/>"><input type="hidden" name="targetEventId" value="<c:out value='${event.id}'/>"><input type="hidden" name="expectedLatestEventId" value="<c:out value='${form.expectedLatestEventId}'/>"><input type="hidden" name="requestKey" value="<c:out value='${reversalKeys[event.id]}'/>">
          <label>記録取消理由<input type="text" name="correctionReason" maxlength="500" required value="<c:out value='${form.targetEventId eq fn:escapeXml(event.id) ? form.correctionReason : ""}'/>"></label><button class="danger">取消イベントを追加</button>
        </form>
      </c:if></td>
    </tr></c:forEach><c:if test="${empty results.items}"><tr><td colspan="6" class="empty">該当する手動報告の履歴はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
