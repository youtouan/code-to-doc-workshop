<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2>監査イベント #<c:out value="${event.id}"/></h2>
  <dl><dt>記録日時</dt><dd><fmt:formatDate value="${event.occurredAt}" pattern="yyyy-MM-dd HH:mm:ss"/></dd>
    <dt>操作者</dt><dd><c:out value="${event.actor}"/></dd><dt>操作区分</dt><dd class="mono"><c:out value="${event.operation}"/></dd>
    <dt>対象種別</dt><dd><c:out value="${event.entityType}"/></dd><dt>対象ID</dt><dd><c:out value="${event.entityId}"/></dd>
    <dt>記録内容</dt><dd class="prewrap"><c:out value="${event.detail}"/></dd>
  </dl>
  <c:url value="/audit.do" var="entityHistory"><c:param name="entityType" value="${event.entityType}"/><c:param name="entityId" value="${event.entityId}"/></c:url>
  <c:url value="/audit.do" var="actorHistory"><c:param name="actorLogin" value="${event.actor}"/></c:url>
  <div class="actions"><a class="button" href="<c:out value='${entityHistory}'/>">この対象の操作履歴</a><a class="button" href="<c:out value='${actorHistory}'/>">この操作者の記録</a><a class="button" href="<c:out value='${ctx}'/>/audit.do">一覧へ</a></div>
</section>
<%@ include file="../fragments/footer.jspf" %>
