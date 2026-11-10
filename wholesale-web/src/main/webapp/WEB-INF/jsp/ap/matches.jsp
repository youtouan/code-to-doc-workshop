<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${apInvoice.number}"/> · <c:out value="${apInvoice.supplierName}"/></h2>
  <p class="help">保存はこの請求の照合一覧全体を置き換えます。数量0の行は除外します。別仕入先・別商品・請求日より後の入荷・他請求分を含めた過剰照合はサーバーで拒否されます。</p>
  <p class="help">候補は請求日以前の未照合入荷の先頭100件と既存の照合です（未照合入荷は全<c:out value="${availableReceiptCount}"/>件）。候補外は<a href="<c:out value='${ctx}'/>/apReports.do?op=receipts&amp;supplierId=<c:out value='${apInvoice.supplier.id}'/>">検収入荷一覧</a>で明細IDを確認し、直接入力できます。</p>
  <datalist id="ap-receipt-lines"><c:forEach items="${receiptChoices}" var="choice"><option value="<c:out value='${choice.key}'/>"><c:out value="${choice.value}"/></option></c:forEach></datalist>
  <html:form action="/apMatches" method="post"><%@ include file="../fragments/identity.jspf" %>
    <div class="table-wrap"><table><thead><tr><th>請求明細</th><th>検収入荷明細ID</th><th>照合数量</th></tr></thead><tbody>
      <c:forEach items="${form.invoiceLineId}" var="selected" varStatus="row"><tr>
        <td><select name="invoiceLineId" aria-label="請求明細"><option value="">未使用行</option><c:forEach items="${apInvoice.lines}" var="line"><option value="<c:out value='${line.id}'/>" <c:if test="${selected eq fn:escapeXml(line.id)}">selected</c:if>>#<c:out value="${line.id}"/> <c:out value="${line.productCode}"/> <c:out value="${line.description}"/> / 請求数<c:out value="${line.quantity}"/></option></c:forEach></select></td>
        <td><input type="text" name="receiptLineId" inputmode="numeric" maxlength="19" list="ap-receipt-lines" aria-label="検収入荷明細ID" value="<c:out value='${form.receiptLineId[row.index]}'/>"></td>
        <td><input type="text" name="quantity" inputmode="numeric" maxlength="9" aria-label="照合数量" value="<c:out value='${form.quantity[row.index]}'/>"></td>
      </tr></c:forEach>
    </tbody></table></div>
    <div class="actions"><button name="op" value="save" class="primary">照合一覧を保存</button><button name="op" value="addLine" formnovalidate>入力行を追加</button><button name="op" value="clear" class="danger" formnovalidate>全照合を解除</button><a class="button" href="<c:out value='${ctx}'/>/apInvoices.do?op=detail&amp;id=<c:out value='${apInvoice.id}'/>">請求へ戻る</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
