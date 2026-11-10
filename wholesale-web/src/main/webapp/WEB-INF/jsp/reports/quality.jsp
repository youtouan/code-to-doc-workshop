<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>実際の検収数量と入荷日による仕入品質</h2>
  <p>対象期間内の<strong>実際の入荷日</strong>を基準に、良品・不良の検収数量を集計します。遅延は入荷日を<strong>現在の発注明細の入荷予定日</strong>と比較して判定します。</p>
  <p class="help">予定日変更前の約束を再現する遅延評価ではありません。不良率＝不良数÷（良品数＋不良数）、遅延良品率＝遅延良品数÷良品数です。金額は検収時に記録した良品仕入金額（税抜）です。</p>
</section>
<%@ include file="../fragments/operations-filter.jspf" %>
<section class="card"><h2>仕入先別の品質・遅延</h2>
  <div class="table-wrap"><table><thead><tr><th>仕入先</th><th class="number">入荷件数</th><th class="number">良品 / 不良</th><th class="number">不良率</th><th class="number">遅延良品数 / 率</th><th class="number">良品仕入金額（税抜）</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="row"><tr><td><a href="<c:out value='${ctx}'/>/suppliers.do?op=detail&amp;id=<c:out value='${row.supplierId}'/>"><c:out value="${row.supplierCode}"/> <c:out value="${row.supplierName}"/></a></td><td class="number"><c:out value="${row.receiptCount}"/></td><td class="number"><c:out value="${row.acceptedQuantity}"/> / <c:out value="${row.rejectedQuantity}"/></td><td class="number"><fmt:formatNumber value="${row.rejectionPercent}" pattern="0.00"/>%</td><td class="number"><c:out value="${row.lateAcceptedQuantity}"/> / <fmt:formatNumber value="${row.lateAcceptedPercent}" pattern="0.00"/>%</td><td class="number"><fmt:formatNumber value="${row.acceptedAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する検収実績はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
