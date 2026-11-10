<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<html:form action="/quotations" method="post"><%@ include file="../fragments/identity.jspf" %><html:hidden property="saveMode"/>
  <section class="card"><h2>見積条件</h2><p class="help">保存のたびに新しい変更不能な版を作成します。改訂では承認・顧客承諾が無効となり、再申請が必要です。有効期限は見積日から180日以内、納期は見積日から365日以内です。</p>
    <div class="field-grid"><label>得意先<html:select property="customerId"><html:option value="">選択してください</html:option><html:options collection="customers" property="id" labelProperty="name"/></html:select></label><label>倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label>
      <label>見積日<input type="date" name="quoteDate" value="<c:out value='${form.quoteDate}'/>" required></label><label>有効期限<input type="date" name="validUntil" value="<c:out value='${form.validUntil}'/>" required></label><label>提案納期<input type="date" name="requestedDate" value="<c:out value='${form.requestedDate}'/>" required></label><label>客先参照番号<html:text property="externalReference" maxlength="80"/></label>
      <label class="wide">納入先（空欄で得意先住所）<html:text property="deliveryAddress" maxlength="300"/></label><label class="wide">備考<html:textarea property="notes" rows="3"/></label>
      <c:if test="${revising}"><label class="wide">改訂理由（必須）<html:textarea property="reason" rows="3"/></label></c:if>
    </div>
  </section>
  <section class="card"><h2>商品・交渉価格の提案</h2>
    <p class="help">通常の受注編集と異なり、この見積では営業担当者も交渉単価を提案できます。価格確定には独立した管理者の承認が必要です。交渉単価を空欄にすると価格条件を再計算します。入数の倍数、数量1～1,000,000、最大200明細です。未使用行は全欄を空欄にしてください。</p>
    <div class="table-wrap"><table><thead><tr><th>商品 / 入数</th><th>数量</th><th>交渉提案単価（任意）</th><th>交渉理由（提案時必須）</th></tr></thead><tbody>
      <c:forEach items="${form.productId}" var="selected" varStatus="row"><tr><td><select name="productId" aria-label="見積商品"><option value="">未使用行</option><c:forEach items="${products}" var="product"><option value="<c:out value='${product.id}'/>" <c:if test="${selected eq fn:escapeXml(product.id)}">selected</c:if>><c:out value="${product.code}"/> <c:out value="${product.name}"/> / 入数<c:out value="${product.packSize}"/></option></c:forEach></select></td><td><input type="text" name="quantity" maxlength="7" inputmode="numeric" aria-label="見積数量" value="<c:out value='${form.quantity[row.index]}'/>"></td><td><input type="text" name="negotiatedUnitPrice" maxlength="15" inputmode="decimal" aria-label="交渉提案単価" value="<c:out value='${form.negotiatedUnitPrice[row.index]}'/>"></td><td><input type="text" name="negotiationReason" maxlength="300" aria-label="交渉理由" value="<c:out value='${form.negotiationReason[row.index]}'/>"></td></tr></c:forEach>
    </tbody></table></div><div class="actions"><button name="op" value="<c:out value='${revising ? "revise" : "save"}'/>" class="primary">新しい版を保存</button><button name="op" value="addLine" formnovalidate>入力行を追加</button><a class="button" href="<c:out value='${ctx}'/>/quotations.do">一覧へ</a></div>
  </section>
</html:form>
<%@ include file="../fragments/footer.jspf" %>
