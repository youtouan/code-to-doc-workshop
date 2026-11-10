<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><html:form action="/suppliers" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="op" value="save">
  <div class="field-grid"><label>仕入先コード<html:text property="code" maxlength="30"/></label><label>名称<html:text property="name" maxlength="100"/></label>
    <label>利用状態<html:select property="active"><html:option value="true">利用中</html:option><html:option value="false">停止中</html:option></html:select></label><label>発注保留<html:select property="onHold"><html:option value="false">通常</html:option><html:option value="true">発注保留</html:option></html:select></label>
    <label>締日<html:select property="closingDay"><html:option value="10">10日</html:option><html:option value="20">20日</html:option><html:option value="31">月末</html:option></html:select></label><label>支払サイト（日数）<html:text property="paymentTermDays" maxlength="3"/></label>
    <label>標準納期（日数）<html:text property="defaultLeadTimeDays" maxlength="3"/></label><label>最低発注額（円）<html:text property="minimumOrderAmount" maxlength="15"/></label>
    <label>税端数処理<html:select property="taxRounding"><html:option value="DOWN">切り捨て</html:option><html:option value="UP">切り上げ</html:option><html:option value="HALF_UP">四捨五入</html:option></html:select></label><label>郵便番号<html:text property="postalCode" maxlength="12"/></label><label>電話番号<html:text property="telephone" maxlength="30"/></label><label>住所<html:text property="address" maxlength="250"/></label><label class="wide">発注条件<html:textarea property="orderingInstructions" rows="3"/></label><label class="wide">備考<html:textarea property="notes" rows="3"/></label>
  </div><div class="actions"><button class="primary">保存</button><a class="button" href="<c:out value='${ctx}'/>/suppliers.do">一覧へ</a></div>
</html:form></section>
<%@ include file="../fragments/footer.jspf" %>
