<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>配送計画の条件</h2><p class="help">配送予定日は本日の30日前～90日後、かつ各出荷予定日以降です。配送先住所・得意先名は既存受注から保存時に取り込みます。この画面から任意の住所に書き換えることはできません。</p>
  <html:form action="/dispatchManifests" method="post"><%@ include file="../fragments/identity.jspf" %>
    <div class="field-grid"><label>配送元倉庫<html:select property="warehouseId"><html:option value="">選択してください</html:option><html:options collection="warehouses" property="id" labelProperty="name"/></html:select></label>
      <label>配送区分<select name="carrier"><option value="">選択してください</option><c:forEach items="${carriers}" var="carrier"><option value="<c:out value='${carrier.key}'/>" <c:if test="${form.carrier eq carrier.key}">selected</c:if>><c:out value="${carrier.value}"/></option></c:forEach></select></label>
      <label>配送予定日<input type="date" name="plannedDispatchDate" value="<c:out value='${form.plannedDispatchDate}'/>" required></label><label>配送備考<html:textarea property="note" rows="3"/></label>
    </div>
    <h2>手動の配送順</h2><p class="help">上から順に配送先番号となります。変更する場合は行の出荷選択値と備考を入れ替えてください。未使用行は両欄を空欄にします。空の下書きは保存できますが公開前に出荷を追加してください。</p>
    <p class="help">候補は先頭100件と現在の配送表の出荷です（未割当は全<c:out value="${candidateCount}"/>件）。候補外は<a href="<c:out value='${ctx}'/>/dispatchManifests.do?op=candidates">候補一覧</a>の「出荷ID:更新番号」を入力してください。再計画では受注・出荷の変更内容を確認して再保存します。</p>
    <datalist id="dispatch-shipments"><c:forEach items="${shipmentChoices}" var="choice"><option value="<c:out value='${choice.key}'/>"><c:out value="${choice.value}"/></option></c:forEach></datalist>
    <div class="table-wrap"><table><thead><tr><th>配送順</th><th>出荷選択（ID:更新番号）</th><th>配送先備考</th></tr></thead><tbody><c:forEach items="${form.shipmentChoice}" var="selected" varStatus="row"><tr><td><c:out value="${row.count}"/></td><td><input type="text" name="shipmentChoice" list="dispatch-shipments" maxlength="30" aria-label="出荷選択" value="<c:out value='${selected}'/>"></td><td><input type="text" name="stopNote" maxlength="250" aria-label="配送先備考" value="<c:out value='${form.stopNote[row.index]}'/>"></td></tr></c:forEach></tbody></table></div>
    <div class="actions"><button name="op" value="save" class="primary">計画下書きを保存</button><button name="op" value="addLine" formnovalidate>配送先行を追加</button><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do">一覧へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
