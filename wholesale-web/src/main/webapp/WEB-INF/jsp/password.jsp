<%@ include file="fragments/tags.jspf" %>
<%@ include file="fragments/header.jspf" %>
<section class="card">
  <p class="help">現在のパスワードと、新しいパスワードを入力してください。新しいパスワードは12～128文字で、大文字・小文字・数字を含めてください。入力したパスワードは画面に再表示されません。</p>
  <html:form action="/password" method="post">
    <%@ include file="fragments/token.jspf" %><input type="hidden" name="op" value="change">
    <div class="field-grid">
      <label class="wide">現在のパスワード<input type="password" name="currentPassword" autocomplete="current-password" maxlength="128" required></label>
      <label>新しいパスワード<input type="password" name="newPassword" autocomplete="new-password" minlength="12" maxlength="128" required></label>
      <label>新しいパスワード（確認）<input type="password" name="confirmation" autocomplete="new-password" minlength="12" maxlength="128" required></label>
    </div>
    <div class="actions"><button class="primary" type="submit">パスワードを変更</button><a class="button" href="<c:out value='${ctx}'/>/dashboard.do">戻る</a></div>
  </html:form>
</section>
<%@ include file="fragments/footer.jspf" %>
