<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><p class="help">仕入先の請求を登録し、発注・検収入荷と数量・単価を照合します。差異は別担当の管理者が承認してから買掛金を計上します。期間条件は仕入先請求日です。</p>
  <div class="actions"><c:if test="${canBILLING}"><a class="button primary" href="<c:out value='${ctx}'/>/apInvoices.do?op=new">仕入先請求を登録</a></c:if><a class="button" href="<c:out value='${ctx}'/>/apReports.do?op=variance">差異承認待ち</a><a class="button" href="<c:out value='${ctx}'/>/apReports.do?op=receipts">未請求の検収入荷</a></div>
  <%@ include file="../fragments/ap-search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>管理番号 / 仕入先請求番号</th><th>仕入先</th><th>請求日 / 支払期日</th><th>計上 / 差異</th><th class="number">税込請求額 / 未払残高</th><th>数量照合</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="item"><tr>
      <td><a href="<c:out value='${ctx}'/>/apInvoices.do?op=detail&amp;id=<c:out value='${item.id}'/>"><c:out value="${item.number}"/></a><br><c:out value="${item.supplierInvoiceNumber}"/></td><td><c:out value="${item.supplierName}"/></td>
      <td><fmt:formatDate value="${item.invoiceDate}" pattern="yyyy-MM-dd"/><br><fmt:formatDate value="${item.dueDate}" pattern="yyyy-MM-dd"/></td><td><span class="badge"><c:out value="${item.status}"/></span><br><c:out value="${item.varianceStatus}"/></td>
      <td class="number"><fmt:formatNumber value="${item.totalAmount}" pattern="#,##0.00"/><br><fmt:formatNumber value="${item.outstandingAmount}" pattern="#,##0.00"/></td><td><c:out value="${item.fullyMatched ? '全数量照合済' : '未照合あり'}"/></td>
    </tr></c:forEach><c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する仕入先請求はありません。</td></tr></c:if>
  </tbody></table></div><%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
