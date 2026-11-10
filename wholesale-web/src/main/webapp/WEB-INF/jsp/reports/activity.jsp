<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>在庫移動台帳の記録日時による活動量</h2>
  <p>期間条件は<strong>在庫移動台帳に記録された日時</strong>です。伝票に入力した過去の入庫日・出荷日などの業務日付に置き換えて集計していません。</p>
  <p class="help">各行は記録日・倉庫・移動区分の集計です。数量は異なる商品の単位換算をしない合計で、記録日時点の在庫残高ではありません。在庫整合性・評価は別の<a href="<c:out value='${ctx}'/>/stockReports.do">在庫レポート</a>で確認してください。</p>
</section>
<%@ include file="../fragments/operations-filter.jspf" %>
<section class="card"><h2>日別・倉庫別・移動区分別</h2>
  <div class="table-wrap"><table><thead><tr><th>台帳記録日</th><th>倉庫 / 移動区分</th><th class="number">移動件数</th><th class="number">入庫 / 出庫 / 純増減</th><th class="number">引当増 / 減 / 純増減</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="row"><tr><td><fmt:formatDate value="${row.recordedDate}" pattern="yyyy-MM-dd"/></td><td><c:out value="${row.warehouseCode}"/><br><c:out value="${row.movementType}"/></td><td class="number"><c:out value="${row.movementCount}"/></td><td class="number"><c:out value="${row.inboundQuantity}"/> / <c:out value="${row.outboundQuantity}"/> / <c:out value="${row.netQuantity}"/></td><td class="number"><c:out value="${row.reservationIncrease}"/> / <c:out value="${row.reservationDecrease}"/> / <c:out value="${row.netReservationChange}"/></td></tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="5" class="empty">指定した記録日時の条件に該当する移動はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
