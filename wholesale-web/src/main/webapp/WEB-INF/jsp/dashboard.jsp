<%@ include file="fragments/tags.jspf" %>
<%@ include file="fragments/header.jspf" %>
<p class="help">現在の業務状況 · <fmt:formatDate value="${dashboard.asOf}" pattern="yyyy年MM月dd日 HH:mm"/> 更新</p>
<div class="metrics">
  <div class="metric"><span>自分の下書き</span><strong><c:out value="${dashboard.counts.myDrafts}"/></strong><a href="<c:out value='${ctx}'/>/orders.do?status=DRAFT">受注一覧へ</a></div>
  <div class="metric"><span>承認待ち</span><strong><c:out value="${dashboard.counts.awaitingApproval}"/></strong><a href="<c:out value='${ctx}'/>/orders.do?status=SUBMITTED">承認キューへ</a></div>
  <div class="metric"><span>納期超過 / 欠品受注</span><strong><c:out value="${dashboard.counts.lateOrders}"/> / <c:out value="${dashboard.counts.shortOrders}"/></strong><a href="<c:out value='${ctx}'/>/orders.do">受注を確認</a></div>
  <div class="metric"><span>本日までの出荷指示</span><strong><c:out value="${dashboard.counts.shipmentsDue}"/></strong><a href="<c:out value='${ctx}'/>/shipments.do?status=INSTRUCTED">出荷キューへ</a></div>
  <div class="metric"><span>発注点割れ</span><strong><c:out value="${dashboard.counts.stockBelowReorder}"/></strong><a href="<c:out value='${ctx}'/>/replenishment.do">補充を検討</a></div>
  <div class="metric"><span>保留在庫 / 本日の入庫</span><strong><c:out value="${dashboard.counts.blockedStock}"/> / <c:out value="${dashboard.counts.receiptsToday}"/></strong><a href="<c:out value='${ctx}'/>/stock.do">在庫を確認</a></div>
  <div class="metric"><span>返品処理待ち</span><strong><c:out value="${dashboard.counts.returnsPending}"/></strong><a href="<c:out value='${ctx}'/>/returns.do">返品キューへ</a></div>
  <div class="metric"><span>未請求の出荷</span><strong><c:out value="${dashboard.counts.unbilledShipments}"/></strong><c:if test="${canBILLING or canMANAGER}"><a href="<c:out value='${ctx}'/>/invoices.do">請求業務へ</a></c:if></div>
</div>
<div class="grid">
  <section class="card"><h2>受注ワークキュー</h2>
    <div class="table-wrap"><table><thead><tr><th>受注番号 / 得意先</th><th>希望納期</th><th>状態</th></tr></thead><tbody>
      <c:forEach items="${dashboard.orders}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/orders.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.party}"/></td><td><fmt:formatDate value="${item.dueDate}" pattern="yyyy-MM-dd"/><c:if test="${item.overdue}"><br><span class="badge">納期超過</span></c:if></td><td><span class="badge"><c:out value="${item.status}"/></span></td></tr></c:forEach>
      <c:if test="${empty dashboard.orders}"><tr><td colspan="3" class="empty">処理待ちの受注はありません。</td></tr></c:if>
    </tbody></table></div>
    <c:if test="${canSALES or canMANAGER}"><div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/orders.do?op=new">受注を登録</a></div></c:if>
  </section>
  <section class="card"><h2>出荷ワークキュー</h2>
    <div class="table-wrap"><table><thead><tr><th>出荷番号 / 得意先</th><th>出荷予定日</th><th>状態</th></tr></thead><tbody>
      <c:forEach items="${dashboard.shipments}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.party}"/></td><td><fmt:formatDate value="${item.dueDate}" pattern="yyyy-MM-dd"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td></tr></c:forEach>
      <c:if test="${empty dashboard.shipments}"><tr><td colspan="3" class="empty">未確定の出荷指示はありません。</td></tr></c:if>
    </tbody></table></div>
  </section>
</div>
<%@ include file="fragments/footer.jspf" %>
