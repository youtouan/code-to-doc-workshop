<%@ include file="fragments/tags.jspf" %>
<%@ include file="fragments/header.jspf" %>
<section class="card login">
  <h2>業務をはじめる</h2><p class="help">管理者から発行された利用者IDでログインしてください。連続した認証失敗は一時的なロックの対象です。</p>
  <html:form action="/login" method="post">
    <%@ include file="fragments/token.jspf" %>
    <input type="hidden" name="op" value="login">
    <label for="login">利用者ID<html:text property="login" styleId="login" maxlength="50"/></label>
    <label for="password">パスワード<input type="password" id="password" name="password" autocomplete="current-password" maxlength="128" required></label>
    <button type="submit" class="primary">ログイン</button>
  </html:form>
  <p class="help">操作のない状態が30分続くと、再ログインが必要です。</p>
</section>
<%@ include file="fragments/footer.jspf" %>
