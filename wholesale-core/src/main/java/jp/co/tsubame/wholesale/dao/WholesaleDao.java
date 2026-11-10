package jp.co.tsubame.wholesale.dao;

import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.BaseEntity;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class WholesaleDao {
    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Session session() {
        return sessionFactory.getCurrentSession();
    }

    public <T> T get(Class<T> type, Long id) {
        if (id == null) {
            throw new BusinessException("validation.id", "伝票またはマスタを指定してください。");
        }
        Object found = session().get(type, id);
        if (found == null) {
            throw new BusinessException("notFound", "対象データが見つかりません。");
        }
        return type.cast(found);
    }

    public <T> T lock(Class<T> type, Long id) {
        if (id == null) {
            throw new BusinessException("validation.id", "伝票またはマスタを指定してください。");
        }
        Object found = session().get(type, id, LockMode.UPGRADE);
        if (found == null) {
            throw new BusinessException("notFound", "対象データが見つかりません。");
        }
        return type.cast(found);
    }

    public void save(BaseEntity entity) {
        session().saveOrUpdate(entity);
    }

    public void delete(BaseEntity entity) {
        session().delete(entity);
    }

    public void flush() {
        session().flush();
    }

    public void lockKey(String namespace, String key) {
        session().createSQLQuery("select 1 as locked from pg_advisory_xact_lock(hashtext(:key))")
                .addScalar("locked", org.hibernate.Hibernate.INTEGER)
                .setString("key", namespace + ":" + key).uniqueResult();
    }

    public void lockReferences(Collection<Long> warehouseIds, Collection<Long> productIds) {
        Checks.state(warehouseIds != null && productIds != null, "validation.id", "参照先を指定してください。");
        for (Long id : warehouseIds) {
            Checks.state(id != null, "validation.id", "倉庫を指定してください。");
        }
        for (Long id : productIds) {
            Checks.state(id != null, "validation.id", "商品を指定してください。");
        }
        // Maintenance locks the same rows before checking for open operational documents.
        for (Long id : new TreeSet<Long>(warehouseIds)) {
            lock(Warehouse.class, id);
        }
        for (Long id : new TreeSet<Long>(productIds)) {
            lock(Product.class, id);
        }
    }

    public Query query(String hql, Map<String, ?> parameters) {
        Query query = session().createQuery(hql);
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            if (entry.getValue() instanceof java.util.Collection<?>) {
                query.setParameterList(entry.getKey(), (java.util.Collection<?>) entry.getValue());
            } else {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> list(String hql, Map<String, ?> parameters) {
        return (List<T>) query(hql, parameters).list();
    }

    public <T> List<T> list(String hql) {
        return list(hql, Collections.<String, Object>emptyMap());
    }

    public long count(String hql, Map<String, ?> parameters) {
        Number number = (Number) query(hql, parameters).uniqueResult();
        return number == null ? 0 : number.longValue();
    }

    @SuppressWarnings("unchecked")
    public <T> Page<T> page(String select, String count, Map<String, ?> parameters, Search search) {
        long total = count(count, parameters);
        List<T> items = (List<T>) query(select, parameters).setFirstResult(search.getOffset())
                .setMaxResults(search.getSize()).list();
        return new Page<T>(items, total, search.getPage(), search.getSize());
    }

    public static Map<String, Object> params(String key, Object value) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put(key, value);
        return parameters;
    }

    public static Map<String, Object> params(String first, Object firstValue, String second, Object secondValue) {
        Map<String, Object> parameters = params(first, firstValue);
        parameters.put(second, secondValue);
        return parameters;
    }
}
