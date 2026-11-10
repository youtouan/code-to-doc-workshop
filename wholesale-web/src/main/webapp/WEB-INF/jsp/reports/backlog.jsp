<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>現在の受注残を、現在の希望納期で照会</h2>
  <p>対象は現在も未出荷数量がある承認済み受注明細です。期間条件は<strong>現在の希望納期</strong>であり、指定した過去日時点の受注残を再現するものではありません。</p>
  <p class="help">未出荷金額は現在の受注残の税抜金額で、売上認識額ではありません。遅延・数量不足・得意先保留・関連マスター停止を明細別に確認できます。</p>
</section>
<%@ include file="../fragments/operations-filter.jspf" %>
<section class="card"><h2>未出荷明細・例外</h2>
  <div class="table-wrap"><table><thead><tr><th>受注 / 行 / 状態</th><th>得意先 / 倉庫</th><th>商品</th><th>現在の希望納期</th><th class="number">未出荷 / 引当 / 不足</th><th class="number">未出荷税抜金額</th><th>例外状態</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="row"><tr><td><a href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${row.orderId}'/>"><c:out value="${row.orderNumber}"/></a> / <c:out value="${row.lineNumber}"/><br><c:out value="${row.status}"/></td><td><c:out value="${row.customerCode}"/> <c:out value="${row.customerName}"/><br><c:out value="${row.warehouseCode}"/></td><td><c:out value="${row.productCode}"/> <c:out value="${row.productName}"/></td><td><fmt:formatDate value="${row.requestedDate}" pattern="yyyy-MM-dd"/></td><td class="number"><c:out value="${row.openQuantity}"/> / <c:out value="${row.allocatedQuantity}"/> / <c:out value="${row.shortageQuantity}"/></td><td class="number"><fmt:formatNumber value="${row.openAmount}" pattern="#,##0.00"/></td>
      <td><c:if test="${row.daysLate gt 0}"><span class="badge">遅延<c:out value="${row.daysLate}"/>日</span> </c:if><c:if test="${row.shortageQuantity gt 0}"><span class="badge">数量不足</span> </c:if><c:if test="${row.customerOnHold}"><span class="badge">得意先保留</span> </c:if><c:if test="${row.masterInactive}"><span class="badge">マスター停止</span></c:if><c:if test="${not row.exception}">例外なし</c:if></td>
    </tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">指定した納期条件に該当する現在の受注残はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
