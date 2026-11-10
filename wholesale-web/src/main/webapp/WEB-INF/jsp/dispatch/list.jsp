<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">同じ倉庫・配送区分の未確定出荷を手動の配送順でまとめます。計画・公開だけでは出荷確定や追加の在庫引当を行いません。期間条件は配送予定日または候補の出荷予定日です。</p>
  <div class="actions"><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do">配送表一覧</a><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do?op=candidates">未割当の出荷指示</a><c:if test="${canWAREHOUSE or canMANAGER}"><a class="button primary" href="<c:out value='${ctx}'/>/dispatchManifests.do?op=new">配送表を計画</a></c:if></div>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table>
    <c:choose><c:when test="${candidateMode}"><thead><tr><th>出荷 / 受注</th><th>得意先 / 受注の納品先</th><th>倉庫 / 配送区分</th><th>出荷予定日</th><th>選択値（ID:更新番号）</th><th>操作</th></tr></thead><tbody>
      <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/shipments.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.order.number}"/></td><td><c:out value="${item.order.customerName}"/><br><c:out value="${item.order.deliveryAddress}"/></td><td><c:out value="${item.warehouse.code}"/> / <c:out value="${item.carrier}"/></td><td><fmt:formatDate value="${item.plannedDate}" pattern="yyyy-MM-dd"/></td><td class="mono"><c:out value="${item.id}"/>:<c:out value="${item.version}"/></td><td><c:if test="${canWAREHOUSE or canMANAGER}"><a class="button" href="<c:out value='${ctx}'/>/dispatchManifests.do?op=new&amp;shipmentChoice=<c:out value='${item.id}'/>:<c:out value='${item.version}'/>">この出荷から計画</a></c:if></td></tr></c:forEach>
    </tbody></c:when><c:otherwise><thead><tr><th>配送表番号</th><th>倉庫 / 配送区分</th><th>配送予定日 / 実出荷日</th><th>状態</th><th>計画者 / 公開者 / 確定者</th></tr></thead><tbody>
      <c:forEach items="${results.items}" var="item"><tr><td><a href="<c:out value='${ctx}'/>/dispatchManifests.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a></td><td><c:out value="${item.warehouse.name}"/> / <c:out value="${item.carrier}"/></td><td><fmt:formatDate value="${item.plannedDispatchDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.dispatchDate}" pattern="yyyy-MM-dd"/></td><td><span class="badge"><c:out value="${item.status}"/></span></td><td><c:out value="${item.createdBy}"/> / <c:out value="${item.releasedBy}"/> / <c:out value="${item.dispatchedBy}"/></td></tr></c:forEach>
    </tbody></c:otherwise></c:choose>
  </table></div><c:if test="${empty results.items}"><p class="empty">条件に一致する配送表・出荷候補はありません。</p></c:if><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
