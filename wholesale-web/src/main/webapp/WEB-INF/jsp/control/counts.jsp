<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">棚卸開始時に対象在庫を凍結します。実棚入力 → 確認提出 → 独立した承認で数量差を反映して凍結解除します。中止時も理由付きで解除します。</p>
  <c:if test="${canWAREHOUSE or canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/counts.do?op=new">棚卸を開始</a></div></c:if>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>棚卸番号</th><th>倉庫</th><th>状態</th><th>開始者 / 日時</th><th>確認者 / 承認者</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/counts.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.warehouse.name}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><c:out value="${item.createdBy}"/><br><fmt:formatDate value="${item.createdAt}" pattern="yyyy-MM-dd HH:mm"/></td><td><c:out value="${item.reviewedBy}"/> / <c:out value="${item.approvedBy}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="5" class="empty">条件に一致する棚卸はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
