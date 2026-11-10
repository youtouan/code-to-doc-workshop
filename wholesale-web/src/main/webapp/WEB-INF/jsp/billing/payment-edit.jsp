<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">入金を先に登録し、確定済み請求書に対して必要な金額だけ消込できます。請求先と異なる得意先への消込はできません。</p>
  <html:form action="/payments" method="post"><%@ include file="../fragments/token.jspf" %><html:hidden property="requestKey"/><input type="hidden" name="op" value="receive">
    <div class="field-grid"><label>得意先<html:select property="customerId"><html:option value="">選択してください</html:option><html:options collection="customers" property="id" labelProperty="name"/></html:select></label>
      <label>入金日<input type="date" name="receivedDate" value="<c:out value='${form.receivedDate}'/>" required></label><label>入金額（円）<html:text property="amount" maxlength="15"/></label>
      <label>入金方法<html:select property="method"><html:option value="BANK_TRANSFER">銀行振込</html:option><html:option value="CASH">現金</html:option><html:option value="CHEQUE">小切手</html:option></html:select></label><label>照合番号<html:text property="reference" maxlength="100"/></label><label class="wide">備考<html:textarea property="notes" rows="3"/></label>
    </div><div class="actions"><button class="primary">入金を登録</button><a class="button" href="<c:out value='${ctx}'/>/payments.do">一覧へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
