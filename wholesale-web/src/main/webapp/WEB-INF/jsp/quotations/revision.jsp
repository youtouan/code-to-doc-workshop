<%@ include file="../fragments/tags.jspf" %>
<%@ include file="../fragments/header.jspf" %>
<section class="card"><h2><c:out value="${quote.number}"/> · 保存済み版の読取専用表示</h2><p class="help">現在は第<c:out value="${quote.revisionNumber}"/>版、状態<c:out value="${quote.status}"/>です。下記は選択した版のスナップショットであり、現在の提案・承認状態とは限りません。</p></section>
<%@ include file="../fragments/quotation-revision.jspf" %>
<div class="actions"><a class="button" href="<c:out value='${ctx}'/>/quotations.do?op=detail&amp;id=<c:out value='${quote.id}'/>">現在版・操作履歴へ</a></div>
<%@ include file="../fragments/footer.jspf" %>
