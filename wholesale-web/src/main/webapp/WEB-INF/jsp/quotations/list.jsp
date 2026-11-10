<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">営業担当者は交渉価格を理由付きで提案できます。別の管理者による承認と顧客承諾記録を経て、承認済み価格・税率を保持した受注下書きへ変換します。期間条件は現在版の見積日です。</p>
  <c:if test="${canSALES or canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/quotations.do?op=new">見積提案を作成</a></div></c:if>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>見積番号 / 現在版</th><th>顧客名（版の写し）</th><th>状態</th><th>見積日 / 有効期限</th><th>提案納期</th><th class="number">税込合計</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/quotations.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br>第<c:out value="${item.revisionNumber}"/>版</td><td><c:out value="${item.currentRevision.customerName}"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><fmt:formatDate value="${item.quoteDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.validUntil}" pattern="yyyy-MM-dd"/></td><td><fmt:formatDate value="${item.requestedDate}" pattern="yyyy-MM-dd"/></td><td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する見積はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
