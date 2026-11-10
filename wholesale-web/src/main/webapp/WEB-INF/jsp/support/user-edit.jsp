<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card">
  <p class="help">権限を1つ以上選択してください。自分自身の利用停止やADMIN権限削除はできません。利用者IDは登録後に変更できません。</p>
  <html:form action="/users" method="post">
    <%@ include file="../fragments/identity.jspf" %>
    <input type="hidden" name="op" value="<c:out value='${creatingUser ? "create" : "update"}'/>">
    <div class="field-grid">
      <c:choose><c:when test="${creatingUser}">
        <label>利用者ID<input type="text" name="login" maxlength="50" autocomplete="off" value="<c:out value='${form.login}'/>" required></label>
      </c:when><c:otherwise>
        <div><span class="field">利用者ID（変更不可）</span><p class="mono"><c:out value="${form.login}"/></p><html:hidden property="login"/></div>
      </c:otherwise></c:choose>
      <label>表示名<html:text property="displayName" maxlength="100"/></label>
      <c:if test="${not creatingUser}"><label>利用状態<html:select property="active"><html:option value="true">利用中</html:option><html:option value="false">停止中</html:option></html:select></label></c:if>
      <fieldset class="wide"><legend>業務権限</legend>
        <c:forEach items="${availableRoles}" var="role"><label class="check"><html:multibox property="selectedRole" value="${role}"/><c:out value="${role}"/>
          <c:choose><c:when test="${role eq 'SALES'}"> — 営業</c:when><c:when test="${role eq 'WAREHOUSE'}"> — 倉庫</c:when><c:when test="${role eq 'BILLING'}"> — 請求・入金</c:when><c:when test="${role eq 'MANAGER'}"> — 業務管理・承認</c:when><c:when test="${role eq 'BATCH'}"> — バッチ業務</c:when><c:when test="${role eq 'ADMIN'}"> — システム管理（自己承認は禁止）</c:when></c:choose>
        </label></c:forEach>
      </fieldset>
      <c:if test="${creatingUser}">
        <label>初期パスワード<input type="password" name="newPassword" minlength="12" maxlength="128" autocomplete="new-password" required></label>
        <label>初期パスワード（確認）<input type="password" name="confirmation" minlength="12" maxlength="128" autocomplete="new-password" required></label>
        <p class="help wide">12～128文字で大文字・小文字・数字を含めてください。エラー時もパスワードは再表示しません。再入力してください。</p>
      </c:if>
    </div>
    <div class="actions"><button class="primary">利用者情報を保存</button><a class="button" href="<c:out value='${ctx}'/>/users.do">一覧へ</a></div>
  </html:form>
</section>
<%@ include file="../fragments/footer.jspf" %>
