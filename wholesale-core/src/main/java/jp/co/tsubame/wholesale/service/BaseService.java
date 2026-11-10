package jp.co.tsubame.wholesale.service;

import java.util.Date;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.AuditEvent;
import jp.co.tsubame.wholesale.entity.BaseEntity;

public abstract class BaseService {
    protected WholesaleDao dao;

    public void setDao(WholesaleDao dao) {
        this.dao = dao;
    }

    protected void require(Actor actor, String... roles) {
        if (actor == null) {
            throw new BusinessException("authentication.required", "ログインしてください。");
        }
        actor.require(roles);
    }

    protected void audit(Actor actor, String operation, BaseEntity entity, String detail) {
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(new Date());
        event.setActor(actor.getLogin());
        event.setOperation(Checks.text(operation, "operation", 60));
        event.setEntityType(entity.getClass().getSimpleName());
        event.setEntityId(entity.getId());
        event.setDetail(Checks.optionalText(detail, "detail", 1000));
        dao.save(event);
    }

    protected String documentNumber(String prefix, Long id) {
        return String.format(java.util.Locale.ROOT, "%s-%08d", prefix, id);
    }
}
