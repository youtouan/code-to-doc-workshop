<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${managedUser.login}"/> · <c:out value="${managedUser.displayName}"/></h2>
  <dl><dt>利用状態</dt><dd><c:out value="${managedUser.active ? '利用中' : '停止中'}"/></dd><dt>権限</dt><dd><c:out value="${managedUser.roles}"/></dd>
    <dt>認証失敗回数</dt><dd><c:out value="${managedUser.failedAttempts}"/>回</dd>
    <dt>ロック期限</dt><dd><fmt:formatDate value="${managedUser.lockedUntil}" pattern="yyyy-MM-dd HH:mm:ss"/></dd>
    <dt>最終ログイン</dt><dd><fmt:formatDate value="${managedUser.lastLoginAt}" pattern="yyyy-MM-dd HH:mm:ss"/></dd>
    <dt>パスワード変更日時</dt><dd><fmt:formatDate value="${managedUser.passwordChangedAt}" pattern="yyyy-MM-dd HH:mm:ss"/></dd>
    <dt>更新番号</dt><dd><c:out value="${managedUser.version}"/></dd>
  </dl>
  <div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/users.do?op=edit&amp;id=<c:out value='${managedUser.id}'/>">表示名・権限・利用状態を編集</a><a class="button" href="<c:out value='${ctx}'/>/audit.do?entityType=AppUser&amp;entityId=<c:out value='${managedUser.id}'/>">この利用者の管理履歴</a><a class="button" href="<c:out value='${ctx}'/>/users.do">一覧へ</a></div>
</section>
<c:if test="${managedUser.active and (managedUser.failedAttempts gt 0 or not empty managedUser.lockedUntil)}">
  <section class="card"><h2>認証ロックの解除</h2><p class="help">認証失敗回数とロック期限を初期化します。利用停止中の場合は、先に利用状態を変更してください。</p>
    <form action="<c:out value='${ctx}'/>/users.do" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="op" value="unlock"><button>認証ロックを解除</button></form>
  </section>
</c:if>
<c:choose><c:when test="${managedUser.id ne actor.userId}">
  <section class="card"><h2>パスワードの再設定</h2><p class="help">対象利用者のパスワードを変更し、認証失敗・ロック状態も解除します。新しいパスワードは安全な別経路で本人へ伝えてください。</p>
    <form action="<c:out value='${ctx}'/>/users.do" method="post"><%@ include file="../fragments/identity.jspf" %><input type="hidden" name="op" value="resetPassword">
      <div class="field-grid"><label>新しいパスワード<input type="password" name="newPassword" minlength="12" maxlength="128" autocomplete="new-password" required></label><label>新しいパスワード（確認）<input type="password" name="confirmation" minlength="12" maxlength="128" autocomplete="new-password" required></label></div>
      <div class="actions"><button class="danger">パスワードを再設定</button></div>
    </form>
  </section>
</c:when><c:otherwise><section class="card"><p>自分のパスワードは<a href="<c:out value='${ctx}'/>/password.do">パスワード変更画面</a>で変更してください。</p></section></c:otherwise></c:choose>
<%@ include file="../fragments/footer.jspf" %>
