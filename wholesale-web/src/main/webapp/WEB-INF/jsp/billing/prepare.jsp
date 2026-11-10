<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>締日の確認</h2>
  <p class="help">初期値は本日以前で直近の締日（10日・20日・末日）です。請求する得意先の締日に合わせて変更してください。</p>
  <form action="<c:out value='${ctx}'/>/invoices.do" method="get" class="toolbar"><input type="hidden" name="op" value="new"><label>対象の締日<input type="date" name="periodEnd" value="<c:out value='${form.periodEnd}'/>" required></label><button>この日に締める得意先を確認</button></form>
  <c:if test="${not empty closingCustomers}"><p>対象得意先：<c:forEach items="${closingCustomers}" var="c"><span class="badge"><c:out value="${c.code}"/> <c:out value="${c.name}"/></span> </c:forEach></p></c:if>
</section>
<section class="card"><h2>請求書を準備</h2><p class="help">得意先の締日（10日・20日・末日）を指定してください。既存の同期間請求がある場合は、その請求を表示します。未請求出荷がなければ作成されません。</p>
  <html:form action="/invoices" method="post"><%@ include file="../fragments/token.jspf" %><input type="hidden" name="op" value="prepare">
    <div class="field-grid"><label>得意先<html:select property="customerId"><html:option value="">選択してください</html:option><html:options collection="customers" property="id" labelProperty="name"/></html:select></label><label>締日<input type="date" name="periodEnd" value="<c:out value='${form.periodEnd}'/>" required></label></div>
    <div class="actions"><button class="primary">請求下書きを作成</button><a class="button" href="<c:out value='${ctx}'/>/invoices.do">一覧へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
