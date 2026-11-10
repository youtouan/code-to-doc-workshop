<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">数量調整は申請者以外の管理者が承認すると反映されます。理由・数量差・原価・承認者は監査履歴に残ります。</p>
  <c:if test="${canWAREHOUSE or canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/adjustments.do?op=new">調整を申請</a></div></c:if>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>調整番号</th><th>倉庫 / 商品</th><th>状態</th><th class="number">増減数 / 金額</th><th>申請者 / 理由</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/adjustments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.warehouse.name}"/><br><c:out value="${item.product.code}"/> <c:out value="${item.product.name}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td class="number"><c:out value="${item.quantityChange}"/><br><fmt:formatNumber value="${item.valueChange}" pattern="#,##0.00"/></td><td><c:out value="${item.proposedBy}"/><br><c:out value="${item.reason}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="5" class="empty">条件に一致する調整はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
