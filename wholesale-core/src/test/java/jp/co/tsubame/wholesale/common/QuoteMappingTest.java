package jp.co.tsubame.wholesale.common;

import java.io.InputStream;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.OrderAmendmentLine;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.Assert.*;

public class QuoteMappingTest {
    @Test
    public void nativeMappingsResolveReferencesAndProtectHistoricalSnapshotsWithoutADatabase() throws Exception {
        Configuration configuration = new Configuration();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:hibernate/*.hbm.xml")) {
            try (InputStream stream = resource.getInputStream()) { configuration.addInputStream(stream); }
        }
        configuration.buildMappings();
        assertTrue(configuration.getClassMapping(Quotation.class.getName()).isMutable());
        assertFalse(configuration.getClassMapping(QuotationRevision.class.getName()).isMutable());
        assertFalse(configuration.getClassMapping(QuotationLine.class.getName()).isMutable());
        assertTrue(configuration.getClassMapping(OrderAmendment.class.getName()).isMutable());
        assertFalse(configuration.getClassMapping(OrderAmendmentLine.class.getName()).isMutable());
        assertEquals("converted_order_id", ((org.hibernate.mapping.Column) configuration.getClassMapping(Quotation.class.getName())
                .getProperty("convertedOrder").getColumnIterator().next()).getName());
    }
}
