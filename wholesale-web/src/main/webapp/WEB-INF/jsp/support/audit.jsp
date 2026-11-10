<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <p class="help">業務操作の記録を読み取り専用で照会します。対象種別・対象ID・操作者・操作区分は完全一致、キーワードは操作区分と詳細の部分一致です。</p>
  <form action="<c:out value='${ctx}'/>/audit.do" method="get" role="search">
    <div class="field-grid">
      <label>対象種別<input type="text" name="entityType" maxlength="100" value="<c:out value='${form.entityType}'/>" list="audit-entity-types"></label>
      <datalist id="audit-entity-types"><option value="SalesOrder"><option value="Shipment"><option value="SalesReturn"><option value="Invoice"><option value="PaymentReceipt"><option value="PaymentAllocation"><option value="PurchaseOrder"><option value="StockTransfer"><option value="StockAdjustment"><option value="StockCount"><option value="Customer"><option value="Product"><option value="Warehouse"><option value="Supplier"><option value="AppUser"></datalist>
      <label>対象ID<input type="text" name="entityId" inputmode="numeric" maxlength="19" value="<c:out value='${form.entityId}'/>"></label>
      <label>操作者の利用者ID<input type="text" name="actorLogin" maxlength="50" value="<c:out value='${form.actorLogin}'/>"></label>
      <label>操作区分<input type="text" name="operation" maxlength="60" value="<c:out value='${form.operation}'/>"></label>
      <label>開始日<input type="date" name="from" value="<c:out value='${form.from}'/>"></label>
      <label>終了日<input type="date" name="to" value="<c:out value='${form.to}'/>"></label>
      <label>キーワード<input type="search" name="text" maxlength="100" value="<c:out value='${form.text}'/>"></label>
      <label>表示件数<select name="size"><c:forTokens items="10,25,50,100" delims="," var="n"><option value="<c:out value='${n}'/>" <c:if test="${form.size eq n}">selected</c:if>><c:out value="${n}"/>件</option></c:forTokens></select></label>
    </div>
    <div class="actions"><button class="primary">監査記録を検索</button><a class="button" href="<c:out value='${ctx}'/>/audit.do">条件クリア</a></div>
  </form>
</section>
<section class="card"><h2>監査記録</h2>
  <div class="table-wrap"><table><thead><tr><th>イベントID / 日時</th><th>操作者</th><th>操作区分</th><th>対象種別 / ID</th><th>詳細</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="event"><tr>
      <td><a class="mono" href="<c:out value='${ctx}'/>/audit.do?op=detail&amp;id=<c:out value='${event.id}'/>">#<c:out value="${event.id}"/></a><br><fmt:formatDate value="${event.occurredAt}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
      <td><c:out value="${event.actor}"/></td><td><c:out value="${event.operation}"/></td>
      <td><c:out value="${event.entityType}"/><br><c:out value="${event.entityId}"/></td>
      <td class="prewrap"><c:out value="${event.detail}"/></td>
    </tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="5" class="empty">条件に一致する監査記録はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
