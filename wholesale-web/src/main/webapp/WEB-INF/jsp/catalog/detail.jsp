<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <h2><c:out value="${form.code}"/> · <c:out value="${form.name}"/></h2>
  <dl>
    <dt>利用状態</dt><dd><c:out value="${form.active eq 'true' ? '利用中' : '停止中'}"/></dd>
    <c:if test="${kind eq 'customers'}">
      <dt>取引状態</dt><dd><c:out value="${form.onHold eq 'true' ? '取引保留' : '通常'}"/></dd>
      <dt>与信限度額</dt><dd><c:out value="${form.creditLimit}"/> 円</dd>
      <dt>締日 / 支払サイト</dt><dd><c:out value="${form.closingDay eq '31' ? '末日' : form.closingDay}"/> / <c:out value="${form.paymentTermDays}"/>日</dd>
      <dt>税端数処理</dt><dd><c:out value="${form.taxRounding}"/></dd>
      <dt>郵便番号</dt><dd><c:out value="${form.postalCode}"/></dd><dt>電話番号</dt><dd><c:out value="${form.telephone}"/></dd>
    </c:if>
    <c:if test="${kind ne 'products'}"><dt>住所</dt><dd><c:out value="${form.address}"/></dd></c:if>
    <c:if test="${kind eq 'products'}">
      <dt>商品ID</dt><dd class="mono"><c:out value="${form.id}"/></dd>
      <dt>税区分</dt><dd><c:out value="${form.taxCategory}"/></dd>
      <dt>標準売価 / 原価</dt><dd><c:out value="${form.listPrice}"/> / <c:out value="${form.standardCost}"/> 円</dd>
      <dt>入数</dt><dd><c:out value="${form.packSize}"/> <c:out value="${form.unit}"/></dd>
      <dt>発注点 / 補充数量</dt><dd><c:out value="${form.reorderPoint}"/> / <c:out value="${form.reorderQuantity}"/></dd>
    </c:if>
    <c:if test="${kind ne 'warehouses'}"><dt>備考</dt><dd class="prewrap"><c:out value="${form.notes}"/></dd></c:if>
    <dt>更新番号</dt><dd class="mono"><c:out value="${form.version}"/></dd>
  </dl>
  <div class="actions">
    <c:if test="${canMaintain}"><a class="button primary" href="<c:out value='${ctx}${route}'/>?op=edit&amp;id=<c:out value='${form.id}'/>">編集</a></c:if>
    <c:if test="${kind eq 'customers'}"><a class="button" href="<c:out value='${ctx}'/>/prices.do?customerId=<c:out value='${form.id}'/>">価格条件</a><a class="button" href="<c:out value='${ctx}'/>/orders.do?customerId=<c:out value='${form.id}'/>">受注履歴</a></c:if>
    <a class="button" href="<c:out value='${ctx}${route}'/>">一覧へ戻る</a>
  </div>
</section>
<%@ include file="../fragments/footer.jspf" %>
