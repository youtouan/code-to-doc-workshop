<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <p class="help">コード・名称は必須です。利用停止にしても過去の伝票・履歴は削除されません。</p>
  <html:form action="${route}" method="post">
    <%@ include file="../fragments/identity.jspf" %><input type="hidden" name="op" value="save">
    <div class="field-grid">
      <label>コード<html:text property="code" maxlength="30"/></label>
      <label>名称<html:text property="name" maxlength="100"/></label>
      <label>利用状態<html:select property="active"><html:option value="true">利用中</html:option><html:option value="false">停止中</html:option></html:select></label>
      <c:if test="${kind eq 'customers'}">
        <label>取引保留<html:select property="onHold"><html:option value="false">通常</html:option><html:option value="true">取引を保留</html:option></html:select></label>
        <label>与信限度額（円）<html:text property="creditLimit" maxlength="15"/></label>
        <label>締日<html:select property="closingDay"><html:option value="10">10日</html:option><html:option value="20">20日</html:option><html:option value="31">月末</html:option></html:select></label>
        <label>支払サイト（日数）<html:text property="paymentTermDays" maxlength="3"/></label>
        <label>税端数処理<html:select property="taxRounding"><html:option value="DOWN">切り捨て</html:option><html:option value="UP">切り上げ</html:option><html:option value="HALF_UP">四捨五入</html:option></html:select></label>
        <label>郵便番号<html:text property="postalCode" maxlength="12"/></label>
        <label>電話番号<html:text property="telephone" maxlength="30"/></label>
      </c:if>
      <c:if test="${kind ne 'products'}"><label class="wide">住所<html:text property="address" maxlength="250"/></label></c:if>
      <c:if test="${kind eq 'products'}">
        <label>単位<html:text property="unit" maxlength="20"/></label>
        <label>税区分<html:select property="taxCategory"><html:option value="STANDARD">標準税率</html:option><html:option value="REDUCED">軽減税率</html:option><html:option value="EXEMPT">非課税</html:option></html:select></label>
        <label>標準売価（円）<html:text property="listPrice" maxlength="15"/></label>
        <label>標準原価（円）<html:text property="standardCost" maxlength="15"/></label>
        <label>入数（受注数量の単位）<html:text property="packSize" maxlength="9"/></label>
        <label>発注点<html:text property="reorderPoint" maxlength="9"/></label>
        <label>標準補充数量<html:text property="reorderQuantity" maxlength="9"/></label>
      </c:if>
      <c:if test="${kind ne 'warehouses'}"><label class="wide">備考<html:textarea property="notes" rows="4"/></label></c:if>
    </div>
    <div class="actions"><button type="submit" class="primary">保存</button><a class="button" href="<c:out value='${ctx}${route}'/>">一覧へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
