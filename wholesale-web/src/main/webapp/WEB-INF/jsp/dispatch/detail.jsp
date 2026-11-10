<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${manifest.number}"/> <span class="badge"><c:out value="${manifest.status}"/></span></h2>
  <dl><dt>配送元 / 配送区分</dt><dd><c:out value="${manifest.warehouse.name}"/> / <c:out value="${manifest.carrier}"/></dd><dt>配送予定日 / 実出荷日</dt><dd><fmt:formatDate value="${manifest.plannedDispatchDate}" pattern="yyyy-MM-dd"/> / <fmt:formatDate value="${manifest.dispatchDate}" pattern="yyyy-MM-dd"/></dd><dt>配送備考</dt><dd class="prewrap"><c:out value="${manifest.note}"/></dd><dt>計画 / 公開 / 確定担当</dt><dd><c:out value="${manifest.createdBy}"/> / <c:out value="${manifest.releasedBy}"/> / <c:out value="${manifest.dispatchedBy}"/></dd><dt>取消理由</dt><dd><c:out value="${manifest.cancellationReason}"/></dd></dl>
  <div class="actions"><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do?op=print&amp;id=<c:out value='${manifest.id}'/>">配送表・積込票の印刷表示</a><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do">一覧へ</a></div>
</section>
<%@ include file="../fragments/dispatch-readiness.jspf" %>
<section class="card"><h2>手動配送順・受注住所の計画時写し</h2><div class="table-wrap"><table><thead><tr><th>順序 / 出荷</th><th>得意先 / 納品先</th><th>配送先備考</th><th>出荷状態 / 手入力管理番号</th></tr></thead><tbody><c:forEach items="${manifest.stops}" var="stop"><tr><td><c:out value="${stop.stopSequence}"/> · <a href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${stop.shipment.id}'/>"><c:out value="${stop.shipment.number}"/></a></td><td><c:out value="${stop.customerName}"/><br><c:out value="${stop.deliveryAddress}"/></td><td><c:out value="${stop.note}"/></td><td><c:out value="${stop.shipment.status}"/><br><c:out value="${stop.shipment.trackingNumber}"/><c:if test="${stop.shipment.status eq 'CONFIRMED'}"><br><a href="<c:out value='${ctx}'/>/deliveryAttempts.do?op=detail&amp;shipmentId=<c:out value='${stop.shipment.id}'/>">配送手動記録へ</a></c:if></td></tr></c:forEach></tbody></table></div></section>
<c:if test="${(canWAREHOUSE or canMANAGER) and (manifest.status eq 'DRAFT' or manifest.status eq 'RELEASED')}">
  <section class="card"><h2>公開・再計画・取消</h2><p class="help">公開は計画の準備状態を確定する社内処理です。受注・出荷が変わった場合は再計画 → 編集保存 → 公開の順で見直してください。</p>
    <form action="<c:out value='${ctx}'/>/dispatchManifests.do" method="post"><%@ include file="../fragments/identity.jspf" %><label>再計画・取消理由<textarea name="reason" maxlength="500"><c:out value="${form.reason}"/></textarea></label><div class="actions">
      <c:if test="${manifest.status eq 'DRAFT'}"><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do?op=edit&amp;id=<c:out value='${manifest.id}'/>">計画を編集</a><c:if test="${readiness.ready}"><button name="op" value="release" class="primary">計画を公開</button></c:if></c:if>
      <c:if test="${manifest.status eq 'RELEASED'}"><button name="op" value="replan">下書きへ戻して再計画</button></c:if><button name="op" value="cancel" class="danger">配送表を取消</button>
    </div></form>
  </section>
</c:if>
<c:if test="${manifest.status eq 'RELEASED' and (canWAREHOUSE or canBATCH)}">
  <section class="card"><h2>全出荷の社内確定</h2>
    <p class="help">全出荷に実際の管理番号を手入力してください。OWNは自社配送管理番号、その他は送り状・配送管理番号です。番号の自動生成、運送会社への照会・送信・検証は行いません。確定は既存出荷処理を全件一括で実行します。</p>
    <c:choose><c:when test="${manifest.plannedDispatchDate le today and readiness.ready}">
      <form action="<c:out value='${ctx}'/>/dispatchManifests.do" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="op" value="confirm"><label>実出荷日<input type="date" name="dispatchDate" value="<c:out value='${form.dispatchDate}'/>" required></label>
        <div class="table-wrap"><table><thead><tr><th>配送順 / 出荷番号</th><th><c:out value="${manifest.carrier eq 'OWN' ? '自社配送管理番号（必須・手入力）' : '送り状・配送管理番号（必須・手入力、未照会）'}"/></th></tr></thead><tbody><c:forEach items="${manifest.stops}" var="stop" varStatus="row"><tr><td><c:out value="${stop.stopSequence}"/> · <c:out value="${stop.shipment.number}"/><input type="hidden" name="shipmentId" value="<c:out value='${stop.shipment.id}'/>"></td><td><input type="text" name="trackingReference" maxlength="80" aria-label="手入力の配送管理番号" value="<c:out value='${form.trackingReference[row.index]}'/>" required></td></tr></c:forEach></tbody></table></div>
        <div class="actions"><button class="primary">全出荷を一括確定</button></div>
      </form>
    </c:when><c:otherwise><p>予定日前、または準備確認で問題があります。予定日・最新状態を確認してください。</p></c:otherwise></c:choose>
  </section>
</c:if>
<%@ include file="../fragments/footer.jspf" %>
