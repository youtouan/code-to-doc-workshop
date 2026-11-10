package jp.co.tsubame.wholesale.service;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.AuditEvent;

public class AuditService extends BaseService {
    public Page<AuditEvent> searchEvents(String entityType, Long entityId, String actorLogin, Search search, Actor actor) {
        require(actor, "MANAGER");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String where = " from AuditEvent e where 1=1";
        if (entityType != null && entityType.trim().length() != 0) {
            where += " and e.entityType=:type";
            parameters.put("type", Checks.text(entityType, "伝票種別", 100));
        }
        if (entityId != null) {
            where += " and e.entityId=:id";
            parameters.put("id", entityId);
        }
        if (actorLogin != null && actorLogin.trim().length() != 0) {
            where += " and e.actor=:actor";
            parameters.put("actor", Checks.text(actorLogin, "利用者ID", 50));
        }
        if (search.getText().length() != 0) {
            where += " and (e.detail like :text escape '!' or e.operation like :text escape '!')";
            parameters.put("text", search.getLikeText());
        }
        if (search.getStatus().length() != 0) {
            where += " and e.operation=:operation";
            parameters.put("operation", search.getStatus());
        }
        if (search.getFrom() != null) {
            where += " and e.occurredAt>=:from";
            parameters.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and e.occurredAt<:to";
            parameters.put("to", Dates.addDays(search.getTo(), 1));
        }
        return dao.page("select e" + where + " order by e.id desc",
                "select count(e.id)" + where, parameters, search);
    }

    public AuditEvent getEvent(Long id, Actor actor) {
        require(actor, "MANAGER");
        return dao.get(AuditEvent.class, id);
    }
}
