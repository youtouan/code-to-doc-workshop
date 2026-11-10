<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <p class="help">ADMIN専用の利用者管理です。利用停止・権限変更は次の画面操作から反映されます。パスワード・ハッシュは表示しません。</p>
  <div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/users.do?op=new">利用者を登録</a></div>
  <%@ include file="../fragments/search.jspf" %>
  <div class="table-wrap"><table><thead><tr><th>利用者ID</th><th>表示名</th><th>権限</th><th>利用状態</th><th>認証失敗 / ロック期限</th><th>最終ログイン</th></tr></thead><tbody>
    <c:forEach items="${results.items}" var="user"><tr>
      <td><a href="<c:out value='${ctx}'/>/users.do?op=detail&amp;id=<c:out value='${user.id}'/>"><c:out value="${user.login}"/></a></td>
      <td><c:out value="${user.displayName}"/></td><td><c:out value="${user.roles}"/></td>
      <td><span class="badge"><c:out value="${user.active ? '利用中' : '停止中'}"/></span></td>
      <td><c:out value="${user.failedAttempts}"/>回<br><fmt:formatDate value="${user.lockedUntil}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
      <td><fmt:formatDate value="${user.lastLoginAt}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
    </tr></c:forEach>
    <c:if test="${empty results.items}"><tr><td colspan="6" class="empty">条件に一致する利用者はありません。</td></tr></c:if>
  </tbody></table></div>
  <%@ include file="../fragments/pagination.jspf" %>
</section>
<%@ include file="../fragments/footer.jspf" %>
