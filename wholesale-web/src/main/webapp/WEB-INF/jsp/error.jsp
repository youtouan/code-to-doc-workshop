<%@ include file="fragments/tags.jspf" %>
<c:if test="${empty pageTitle}"><c:set var="pageTitle" value="ページを表示できません"/></c:if>
<%@ include file="fragments/header.jspf" %>
<section class="card">
  <h2>操作は完了していません</h2>
  <p>対象データの状態や入力内容をご確認ください。複数の画面で編集していた場合は、一覧から最新の内容を開き直してください。</p>
  <p class="help">通信やシステムエラーの場合、再実行の前に一覧で登録状況をご確認ください。</p>
  <div class="actions"><a class="button primary" href="<c:out value='${ctx}'/>/dashboard.do">ダッシュボードへ</a><a class="button" href="<c:out value='${ctx}'/>/login.do">ログイン画面へ</a></div>
</section>
<%@ include file="fragments/footer.jspf" %>
