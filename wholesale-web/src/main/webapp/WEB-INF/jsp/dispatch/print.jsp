<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<c:set var="manifest" value="${printView.manifest}"/><c:set var="readiness" value="${printView.readiness}"/>
<section class="card"><h2>配送表・積込確認票 <c:out value="${manifest.number}"/></h2>
  <dl><dt>状態</dt><dd><c:out value="${manifest.status}"/></dd><dt>配送元 / 配送区分</dt><dd><c:out value="${manifest.warehouse.name}"/> / <c:out value="${manifest.carrier}"/></dd><dt>配送予定日</dt><dd><fmt:formatDate value="${manifest.plannedDispatchDate}" pattern="yyyy-MM-dd"/></dd><dt>出力日時</dt><dd><fmt:formatDate value="${printView.generatedAt}" pattern="yyyy-MM-dd HH:mm:ss"/></dd><dt>備考</dt><dd><c:out value="${manifest.note}"/></dd></dl>
  <p class="help">ブラウザーの印刷機能（Ctrl+P）を使用してください。本票は手動配送順と既存受注の住所写しです。経路最適化・運送会社の受領確認・配達完了を証明する帳票ではありません。</p>
</section>
<%@ include file="../fragments/dispatch-readiness.jspf" %>
<c:forEach items="${printView.stops}" var="stop">
  <section class="card"><h2>配送先 <c:out value="${stop.sequence}"/> · <c:out value="${stop.customerName}"/></h2>
    <dl><dt>納品先（計画時写し）</dt><dd><c:out value="${stop.deliveryAddress}"/></dd><dt>出荷 / 受注</dt><dd><c:out value="${stop.shipmentNumber}"/> / <c:out value="${stop.orderNumber}"/></dd><dt>出荷状態</dt><dd><c:out value="${stop.shipmentStatus}"/></dd><dt>手入力管理番号</dt><dd><c:out value="${stop.trackingReference}"/></dd><dt>配送先備考</dt><dd><c:out value="${stop.note}"/></dd></dl>
    <div class="table-wrap"><table><thead><tr><th>商品コード / 品名</th><th class="number">出荷数量</th><th>単位</th><th class="number">現在の返品受領済数量</th></tr></thead><tbody><c:forEach items="${stop.items}" var="item"><tr><td><c:out value="${item.productCode}"/> <c:out value="${item.productName}"/></td><td class="number"><c:out value="${item.quantity}"/></td><td><c:out value="${item.unit}"/></td><td class="number"><c:out value="${item.currentReturnedQuantity}"/></td></tr></c:forEach></tbody></table></div>
    <p class="help">返品数量は出力時点の現在値です。未配達数量を推定したものではありません。</p>
  </section>
</c:forEach>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do?op=detail&amp;id=<c:out value='${manifest.id}'/>">配送表詳細へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
