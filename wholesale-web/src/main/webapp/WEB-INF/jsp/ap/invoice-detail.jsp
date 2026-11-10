<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${apInvoice.number}"/> <span class="badge"><c:out value="${apInvoice.status}"/></span></h2>
  <div class="grid"><dl><dt>仕入先 / 請求番号</dt><dd><c:out value="${apInvoice.supplierName}"/> / <c:out value="${apInvoice.supplierInvoiceNumber}"/></dd><dt>仕入先住所</dt><dd><c:out value="${apInvoice.supplierAddress}"/></dd>
    <dt>請求日 / 支払期日</dt><dd><fmt:formatDate value="${apInvoice.invoiceDate}" pattern="yyyy-MM-dd"/> / <fmt:formatDate value="${apInvoice.dueDate}" pattern="yyyy-MM-dd"/></dd><dt>備考</dt><dd class="prewrap"><c:out value="${apInvoice.notes}"/></dd>
    <dt>起票 / 最終変更</dt><dd><c:out value="${apInvoice.createdBy}"/> / <c:out value="${apInvoice.lastChangedBy}"/></dd><dt>計上日 / 担当</dt><dd><fmt:formatDate value="${apInvoice.postedDate}" pattern="yyyy-MM-dd"/> / <c:out value="${apInvoice.postedBy}"/></dd>
    <dt>取消理由</dt><dd><c:out value="${apInvoice.cancellationReason}"/></dd></dl>
    <dl><dt>税抜 / 消費税</dt><dd class="number"><fmt:formatNumber value="${apInvoice.netAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${apInvoice.taxAmount}" pattern="#,##0.00"/></dd><dt>税込請求額</dt><dd class="number"><fmt:formatNumber value="${apInvoice.totalAmount}" pattern="#,##0.00"/></dd>
    <dt>支払済 / 値引計上</dt><dd class="number"><fmt:formatNumber value="${apInvoice.paidAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${apInvoice.creditedAmount}" pattern="#,##0.00"/></dd><dt>未払残高</dt><dd class="number"><strong><fmt:formatNumber value="${apInvoice.outstandingAmount}" pattern="#,##0.00"/> 円</strong></dd>
    <dt>差異状態</dt><dd><c:out value="${apInvoice.varianceStatus}"/></dd><dt>差異額 / 絶対差異合計</dt><dd class="number"><fmt:formatNumber value="${apInvoice.varianceAmount}" pattern="#,##0.00"/> / <fmt:formatNumber value="${apInvoice.absoluteVarianceAmount}" pattern="#,##0.00"/></dd>
    <dt>差異承認者 / 理由</dt><dd><c:out value="${apInvoice.varianceApprovedBy}"/> / <c:out value="${apInvoice.varianceReason}"/></dd></dl></div>
</section>
<section class="card"><h2>請求明細・検収照合</h2>
  <div class="table-wrap"><table><thead><tr><th>行ID / 商品・品名</th><th class="number">請求 / 照合済 / 未照合</th><th class="number">単価 / 税率 / 税抜金額</th><th>照合先の入荷・発注 / 数量 / 原価</th></tr></thead><tbody>
    <c:forEach items="${apInvoice.lines}" var="line"><tr><td><span class="mono">#<c:out value="${line.id}"/></span> · <c:out value="${line.productCode}"/><br><c:out value="${line.description}"/></td><td class="number"><c:out value="${line.quantity}"/> / <c:out value="${line.matchedQuantity}"/> / <c:out value="${line.unmatchedQuantity}"/></td><td class="number"><fmt:formatNumber value="${line.unitPrice}" pattern="#,##0.00"/> / <fmt:formatNumber value="${line.taxRate}" type="percent" maxFractionDigits="2"/><br><fmt:formatNumber value="${line.netAmount}" pattern="#,##0.00"/></td>
      <td><c:forEach items="${line.matches}" var="match"><div><a href="<c:out value='${ctx}'/>/purchaseReceipts.do?op=detail&amp;id=<c:out value='${match.receiptLine.receipt.id}'/>"><c:out value="${match.receiptNumber}"/></a> / <c:out value="${match.purchaseOrderNumber}"/> × <c:out value="${match.quantity}"/><br>検収単価 <c:out value="${match.receiptUnitCost}"/> / 発注単価 <c:out value="${match.orderedUnitCost}"/><br>請求差異 <c:out value="${match.varianceAmount}"/> / 発注差異 <c:out value="${match.purchaseVarianceAmount}"/></div></c:forEach><c:if test="${empty line.matches}">未照合</c:if></td>
    </tr></c:forEach>
  </tbody></table></div>
</section>
<c:if test="${apInvoice.status eq 'DRAFT'}">
  <section class="card"><h2>照合・差異承認・計上</h2><p class="help">計上には全数量の照合が必要です。差異がある場合は起票者・最終変更者とは別の管理者が承認します。明細または照合を変更すると承認はリセットされます。</p>
    <form action="<c:out value='${ctx}'/>/apInvoices.do" method="post"><%@ include file="../fragments/identity.jspf" %>
      <label>差異承認・取消理由<textarea name="reason" maxlength="500"><c:out value="${form.reason}"/></textarea></label>
      <div class="actions"><c:if test="${canBILLING}"><a class="button" href="<c:out value='${ctx}'/>/apInvoices.do?op=edit&amp;id=<c:out value='${apInvoice.id}'/>">請求明細を編集</a><a class="button" href="<c:out value='${ctx}'/>/apMatches.do?op=edit&amp;id=<c:out value='${apInvoice.id}'/>">検収入荷と照合</a>
        <c:if test="${apInvoice.fullyMatched and (apInvoice.varianceStatus eq 'NONE' or apInvoice.varianceStatus eq 'APPROVED')}"><button name="op" value="post" class="primary">買掛金を計上</button></c:if><button name="op" value="cancel" class="danger">下書き取消</button></c:if>
        <c:if test="${canMANAGER and apInvoice.fullyMatched and apInvoice.varianceStatus eq 'REQUIRED' and apInvoice.createdById ne actor.userId and apInvoice.lastChangedById ne actor.userId}"><button name="op" value="approveVariance" class="primary">差異を承認</button></c:if>
      </div>
    </form>
  </section>
</c:if>
<div class="actions"><c:if test="${canBILLING and apInvoice.status eq 'POSTED'}"><a class="button" href="<c:out value='${ctx}'/>/apCredits.do?op=new&amp;invoiceId=<c:out value='${apInvoice.id}'/>">財務値引を申請</a><a class="button" href="<c:out value='${ctx}'/>/apPayments.do?op=new&amp;supplierId=<c:out value='${apInvoice.supplier.id}'/>">仕入先へ支払</a></c:if><a class="button" href="<c:out value='${ctx}'/>/apReports.do?op=statement&amp;supplierId=<c:out value='${apInvoice.supplier.id}'/>">仕入先元帳</a><a class="button" href="<c:out value='${ctx}'/>/apInvoices.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
