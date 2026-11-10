<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>売上認識の基準</h2>
  <p><strong>確定済み出荷の出荷日</strong>による税抜出荷額から、<strong>受領済み返品の受領日</strong>による税抜返品額を差し引きます。受注日・請求日・返品申請日ではありません。</p>
  <p class="help">区分名は現在の得意先・商品・倉庫マスター名です。過去の原価や粗利を推定・表示していません。期間外の出荷に対する当期返品も含むため純売上が負になる場合があります。数量は明細数量の単純合計で、異なる商品の単位換算はしていません。</p>
</section>
<%@ include file="../fragments/operations-filter.jspf" %>
<section class="card"><h2>得意先・商品・倉庫別の売上</h2>
  <c:if test="${not empty results}"><p>表示中のページの純売上合計：<strong><fmt:formatNumber value="${pageNetAmount}" pattern="#,##0.00"/> 円（税抜）</strong></p><p class="help">検索結果全体の総額ではありません。出荷・返品件数は各集計区分内の伝票件数です。</p></c:if>
  <div class="table-wrap"><table><thead><tr><th>コード / 現在の名称</th><th class="number">出荷数 / 返品数 / 純数量</th><th class="number">出荷件数 / 返品件数</th><th class="number">税抜出荷額</th><th class="number">税抜返品額</th><th class="number">純売上（税抜）</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="row"><tr><td><a href="<c:out value='${ctx}${dimensionRoute}'/>?op=detail&amp;id=<c:out value='${row.dimensionId}'/>"><c:out value="${row.code}"/> <c:out value="${row.name}"/></a></td><td class="number"><c:out value="${row.shippedQuantity}"/> / <c:out value="${row.returnedQuantity}"/> / <c:out value="${row.netQuantity}"/></td><td class="number"><c:out value="${row.shipmentCount}"/> / <c:out value="${row.returnCount}"/></td><td class="number"><fmt:formatNumber value="${row.shippedAmount}" pattern="#,##0.00"/></td><td class="number"><fmt:formatNumber value="${row.returnedAmount}" pattern="#,##0.00"/></td><td class="number"><strong><fmt:formatNumber value="${row.netAmount}" pattern="#,##0.00"/></strong></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">対象期間の出荷・返品実績はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
