<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <div class="actions"><a class="button" href="<c:out value='${ctx}'/>/stockReports.do?op=reconciliation">台帳整合性</a><a class="button" href="<c:out value='${ctx}'/>/stockReports.do?op=valuation">在庫評価</a><a class="button" href="<c:out value='${ctx}'/>/transfers.do">倉庫間移送</a></div>
  <p class="help">現在の在庫・引当・移送中データを読み取り専用で照会します。過去日時点の評価ではありません。検索語は商品・倉庫コード等で絞り込めます。</p>
  <form action="<c:out value='${ctx}'/>/stockReports.do" method="get" class="toolbar" role="search">
    <input type="hidden" name="op" value="<c:out value='${valuation ? "valuation" : "reconciliation"}'/>">
    <label>キーワード<input type="search" name="text" maxlength="100" value="<c:out value='${form.text}'/>"></label>
    <label>倉庫<select name="warehouseId"><option value="">すべて</option><c:forEach items="${warehouses}" var="w"><option value="<c:out value='${w.id}'/>" <c:if test="${form.warehouseId eq fn:escapeXml(w.id)}">selected</c:if>><c:out value="${w.code}"/> <c:out value="${w.name}"/></option></c:forEach></select></label>
    <c:if test="${not valuation}"><label>整合状態<select name="status"><option value="">すべて</option><option value="OK" <c:if test="${form.status eq 'OK'}">selected</c:if>>整合</option><option value="MISMATCH" <c:if test="${form.status eq 'MISMATCH'}">selected</c:if>>不整合</option></select></label></c:if>
    <label>表示件数<select name="size"><c:forTokens items="10,25,50,100" delims="," var="n"><option value="<c:out value='${n}'/>" <c:if test="${form.size eq n}">selected</c:if>><c:out value="${n}"/>件</option></c:forTokens></select></label>
    <button class="primary">照会</button>
  </form>
  <c:if test="${not empty reportAsOf}"><p class="help">取得日時：<fmt:formatDate value="${reportAsOf}" pattern="yyyy-MM-dd HH:mm:ss"/></p></c:if>
</section>
<c:choose><c:when test="${valuation}">
  <section class="card"><h2>在庫評価の算定基準</h2>
    <p>倉庫内の在庫は<strong>現在の商品標準原価</strong>、移送中在庫は<strong>移送出庫時の標準原価</strong>で評価します。移送下書き・承認だけでは移送用の在庫予約は行いません。</p>
    <p class="help">帰属評価額 = 倉庫内在庫評価額 + この倉庫から出庫した移送中評価額。到着待ち評価額は参照値で、二重計上を避けるため加算しません。保留在庫の利用可能数は0ですが、現在庫の評価からは除外しません。</p>
    <c:if test="${not empty results}"><dl>
      <dt>このページの現在庫評価</dt><dd><fmt:formatNumber value="${pagePhysicalValue}" pattern="#,##0.00"/> 円</dd>
      <dt>このページの出庫済移送中評価</dt><dd><fmt:formatNumber value="${pageOutgoingValue}" pattern="#,##0.00"/> 円</dd>
      <dt>このページの帰属評価合計</dt><dd><strong><fmt:formatNumber value="${pageAttributedValue}" pattern="#,##0.00"/> 円</strong></dd>
    </dl><p class="help">上記合計は表示中のページのみです。検索結果全体の合計ではありません。</p></c:if>
  </section>
  <section class="card"><h2>商品・倉庫別の評価</h2>
    <div class="table-wrap"><table><thead><tr><th>倉庫 / 商品</th><th class="number">現在庫 / 引当 / 利用可能</th><th class="number">現在の標準原価</th><th class="number">現在庫 / 引当 / 利用可能の評価</th><th class="number">出庫済移送中 数量 / 評価</th><th class="number">到着待ち 数量 / 評価</th><th class="number">帰属評価額</th></tr></thead><tbody>
      <c:forEach items="${results.items}" var="row"><tr>
        <td><c:out value="${row.warehouseCode}"/><br><a href="<c:out value='${ctx}'/>/products.do?op=detail&amp;id=<c:out value='${row.productId}'/>"><c:out value="${row.productCode}"/> <c:out value="${row.productName}"/></a><c:if test="${row.blocked}"><br><span class="badge">保留在庫</span></c:if></td>
        <td class="number"><c:out value="${row.onHand}"/> / <c:out value="${row.reserved}"/> / <c:out value="${row.available}"/></td>
        <td class="number"><fmt:formatNumber value="${row.currentStandardCost}" pattern="#,##0.00"/></td>
        <td class="number"><fmt:formatNumber value="${row.onHandValue}" pattern="#,##0.00"/><br><fmt:formatNumber value="${row.reservedValue}" pattern="#,##0.00"/><br><fmt:formatNumber value="${row.availableValue}" pattern="#,##0.00"/></td>
        <td class="number"><c:out value="${row.outgoingTransitQuantity}"/><br><fmt:formatNumber value="${row.outgoingTransitValue}" pattern="#,##0.00"/></td>
        <td class="number"><c:out value="${row.incomingTransitQuantity}"/><br><fmt:formatNumber value="${row.incomingTransitValue}" pattern="#,##0.00"/></td>
        <td class="number"><strong><fmt:formatNumber value="${row.attributedInventoryValue}" pattern="#,##0.00"/></strong></td>
      </tr></c:forEach>
      <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">条件に一致する在庫評価データはありません。</td></tr></c:if>
    </tbody></table></div>
    <%@ include file="../fragments/pagination.jspf" %>
  </section>
</c:when><c:otherwise>
  <section class="card"><h2>在庫・台帳・引当の照合</h2>
    <p class="help">現在庫と移動台帳、引当残高と予約明細、予約明細と受注明細を照合します。台帳記録や棚卸凍結の矛盾も判定対象です。この画面に自動修復・調整機能はありません。</p>
    <c:if test="${not empty results}"><p>このページの不整合：<strong><c:out value="${pageMismatches}"/> 件</strong></p></c:if>
    <div class="table-wrap"><table><thead><tr><th>倉庫 / 商品</th><th>判定</th><th class="number">現在庫 / 台帳 / 現在庫−台帳</th><th class="number">引当 / 台帳 / 引当−台帳</th><th class="number">予約数 / 引当−予約</th><th class="number">受注引当 / 予約−受注</th><th class="number">台帳破損 / 不正予約 / 棚卸凍結数</th></tr></thead><tbody>
      <c:forEach items="${results.items}" var="row"><tr>
        <td><c:out value="${row.warehouseCode}"/><br><a href="<c:out value='${ctx}'/>/stock.do?op=detail&amp;id=<c:out value='${row.balanceId}'/>"><c:out value="${row.productCode}"/> <c:out value="${row.productName}"/></a><c:if test="${row.blocked}"><br><span class="badge">保留中</span></c:if></td>
        <td><span class="badge"><c:out value="${row.consistent ? '整合' : '不整合・要確認'}"/></span></td>
        <td class="number"><c:out value="${row.onHand}"/> / <c:out value="${row.ledgerOnHand}"/> / <c:out value="${row.stockDifference}"/></td>
        <td class="number"><c:out value="${row.reserved}"/> / <c:out value="${row.ledgerReserved}"/> / <c:out value="${row.reservedLedgerDifference}"/></td>
        <td class="number"><c:out value="${row.reservationQuantity}"/> / <c:out value="${row.reservationDifference}"/></td>
        <td class="number"><c:out value="${row.allocatedOrderQuantity}"/> / <c:out value="${row.allocationDifference}"/></td>
        <td class="number"><c:out value="${row.brokenMovementSnapshots}"/> / <c:out value="${row.invalidReservations}"/> / <c:out value="${row.activeCountHolds}"/></td>
      </tr></c:forEach>
      <c:if test="${empty results.items}"><tr><td colspan="7" class="empty">条件に一致する照合データはありません。</td></tr></c:if>
    </tbody></table></div>
    <p class="help">不整合時は在庫詳細の移動履歴と関連伝票を確認し、問い合わせ番号と対象IDを管理者へ連絡してください。根拠のない数量調整で台帳差を埋めないでください。</p>
    <%@ include file="../fragments/pagination.jspf" %>
  </section>
</c:otherwise></c:choose>
<%@ include file="../fragments/footer.jspf" %>
