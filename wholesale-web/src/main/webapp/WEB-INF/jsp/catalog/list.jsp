<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <c:if test="${canMaintain}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}${route}'/>?op=new">新規登録</a></div></c:if>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>コード</th><th>名称</th><th>利用状態</th>
    <c:choose><c:when test="${kind eq 'customers'}"><th>締日 / 支払サイト</th><th class="number">与信限度額</th><th>取引保留</th></c:when>
    <c:when test="${kind eq 'products'}"><th>税区分 / 入数</th><th class="number">標準売価</th><th>発注点 / 補充数</th></c:when>
    <c:otherwise><th>住所</th></c:otherwise></c:choose>
  </tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr>
      <td class="mono"><a href="<c:out value='${ctx}${route}'/>?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.code}"/></a></td>
      <td><c:out value="${item.name}"/></td><td><span class="badge"><c:out value="${item.active ? '利用中' : '停止中'}"/></span></td>
      <c:choose><c:when test="${kind eq 'customers'}"><td><c:out value="${item.closingDay eq 31 ? '末日' : item.closingDay}"/> / <c:out value="${item.paymentTermDays}"/>日</td><td class="number"><fmt:formatNumber value="${item.creditLimit}" pattern="#,##0.00"/></td><td><c:out value="${item.onHold ? '保留' : '通常'}"/></td></c:when>
      <c:when test="${kind eq 'products'}"><td><c:out value="${item.taxCategory}"/> / <c:out value="${item.packSize}"/><c:out value="${item.unit}"/></td><td class="number"><fmt:formatNumber value="${item.listPrice}" pattern="#,##0.00"/></td><td><c:out value="${item.reorderPoint}"/> / <c:out value="${item.reorderQuantity}"/></td></c:when>
      <c:otherwise><td><c:out value="${item.address}"/></td></c:otherwise></c:choose>
    </tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">検索条件に一致するデータはありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
