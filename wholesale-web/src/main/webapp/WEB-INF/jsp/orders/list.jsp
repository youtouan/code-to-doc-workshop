<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <c:if test="${canSALES or canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/orders.do?op=new">受注を登録</a></div></c:if>
  <p class="help">下書き → 申請 → 承認 → 在庫引当 → 出荷。承認は起票者以外の管理者が行います。日付条件は受注日です。</p>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>受注番号</th><th>得意先</th><th>受注日 / 希望納期</th><th>状態</th><th class="number">受注金額</th><th class="number">未出荷 / 引当 / 出荷済</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr>
      <td><a class="mono" href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><span class="muted"><c:out value="${item.externalReference}"/></span></td>
      <td><c:out value="${item.customerName}"/></td><td><fmt:formatDate value="${item.orderDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.requestedDate}" pattern="yyyy-MM-dd"/></td>
      <td><span class="badge"><c:out value="${item.status}"/></span></td><td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/></td>
      <td class="number"><c:out value="${item.openQuantity}"/> / <c:out value="${item.allocatedQuantity}"/> / <c:out value="${item.shippedQuantity}"/></td>
    </tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する受注はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
