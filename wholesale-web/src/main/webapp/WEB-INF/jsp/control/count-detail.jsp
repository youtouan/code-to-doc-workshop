<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${count.number}"/> · <c:out value="${count.warehouse.name}"/> <span class="badge"><c:out value="${count.status}"/></span></h2>
  <dl><dt>開始 / 確認 / 承認</dt><dd><c:out value="${count.createdBy}"/> / <c:out value="${count.reviewedBy}"/> / <c:out value="${count.approvedBy}"/></dd><dt>備考</dt><dd><c:out value="${count.note}"/></dd><dt>未入力 / 差異 / 引当不足</dt><dd><c:out value="${count.uncountedLines}"/> / <c:out value="${count.discrepancyLines}"/> / <c:out value="${count.reservationShortageLines}"/> 行</dd><dt>差異評価額</dt><dd><fmt:formatNumber value="${count.countedDifferenceValue}" pattern="#,##0.00"/> 円</dd><dt>取消理由</dt><dd><c:out value="${count.cancellationReason}"/></dd></dl>
</section>
<section class="card"><h2>実棚入力・差異確認</h2><p class="help">実棚0は「0」と入力してください。空欄は未入力として扱います。帳簿数と異なる場合は差異理由が必要です。ページを移動する前に入力内容を保存してください。</p>
  <p class="help">引当済数量を下回る実測値も保存・確認提出できます。引当不足が残る場合は承認できません。承認失敗時は在庫数量と棚卸の凍結状態を維持します。</p>
  <form action="<c:out value='${ctx}'/>/counts.do" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="pageNumber" value="<c:out value='${form.pageNumber}'/>"><input type="hidden" name="size" value="<c:out value='${form.size}'/>">
    <div class="table-wrap"><table><thead><tr><th>商品</th><th class="number">開始時在庫 / 引当</th><th>実棚数量</th><th class="number">差異 / 引当不足</th><th>差異理由</th><th>入力者</th></tr></thead><tbody>
      <c:forEach items="${results.items}" var="line" varStatus="row"><tr><td><c:out value="${line.product.code}"/> <c:out value="${line.product.name}"/><input type="hidden" name="lineId" value="<c:out value='${line.id}'/>"></td><td class="number"><c:out value="${line.snapshotOnHand}"/> / <c:out value="${line.snapshotReserved}"/></td>
        <c:choose><c:when test="${count.status eq 'COUNTING' and (canWAREHOUSE or canBATCH)}"><td><input type="text" name="countedQuantity" inputmode="numeric" maxlength="9" aria-label="実棚数量" value="<c:out value='${form.countedQuantity[row.index]}'/>"></td><td class="number"><c:out value="${line.difference}"/> / <c:out value="${line.reservationShortage}"/></td><td><input type="text" name="lineNote" maxlength="500" aria-label="差異理由" value="<c:out value='${form.lineNote[row.index]}'/>"></td></c:when>
        <c:otherwise><td class="number"><c:out value="${line.countedQuantity}" default="未入力"/></td><td class="number"><c:out value="${line.difference}"/> / <c:out value="${line.reservationShortage}"/></td><td><c:out value="${line.note}"/></td></c:otherwise></c:choose>
        <td><c:out value="${line.countedBy}"/></td></tr></c:forEach>
    </tbody></table></div>
    <c:if test="${count.status eq 'COUNTING' and (canWAREHOUSE or canBATCH)}"><div class="actions"><button name="op" value="record" class="primary">このページの実棚を保存</button></div></c:if>
  </form><%@ include file="../fragments/pagination.jspf" %>
</section>
<c:if test="${(count.status eq 'COUNTING' or count.status eq 'REVIEWED') and (canWAREHOUSE or canMANAGER)}">
  <section class="card"><h2>確認・承認・中止</h2><p class="help">すべての実棚を保存した後に確認提出してください。棚卸開始者自身は承認できません。</p>
    <form action="<c:out value='${ctx}'/>/counts.do" method="post"><%@ include file="../fragments/identity.jspf" %><label>再入力・中止理由<textarea name="reason" maxlength="500"><c:out value="${form.reason}"/></textarea></label>
      <div class="actions"><c:if test="${count.status eq 'COUNTING'}"><button name="op" value="review" class="primary">確認提出</button></c:if><c:if test="${count.status eq 'REVIEWED'}"><button name="op" value="reopen">実棚の再入力へ戻す</button><c:if test="${canMANAGER and count.createdById ne actor.userId}"><button name="op" value="approve" class="primary">差異を反映し凍結解除</button></c:if></c:if><button name="op" value="cancel" class="danger">棚卸中止・凍結解除</button></div>
    </form>
  </section>
</c:if>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/counts.do">一覧へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
