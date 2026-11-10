package jp.co.tsubame.wholesale.common;

import java.io.InputStream;
import jp.co.tsubame.wholesale.entity.APCredit;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APPaymentVoucher;
import org.hibernate.cfg.Configuration;
import org.hibernate.mapping.PersistentClass;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.Assert.*;

public class APMappingTest {
    @Test
    public void nativeMappingsResolveAllApReferencesWithoutDatabaseOrImmutableNumberLoss() throws Exception {
        Configuration configuration = new Configuration();
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:hibernate/*.hbm.xml");
        for (Resource resource : resources) {
            try (InputStream input = resource.getInputStream()) {
                configuration.addInputStream(input);
            }
        }
        configuration.buildMappings();
        PersistentClass invoice = configuration.getClassMapping(APInvoice.class.getName());
        assertNotNull(invoice);
        assertEquals("ap_invoice", invoice.getTable().getName());
        assertTrue("Number assignment and balance projections require mutable HBM; SQL freezes posted facts", invoice.isMutable());
        assertNotNull(invoice.getProperty("createdById"));
        assertNotNull(invoice.getProperty("varianceApprovedById"));
        PersistentClass credit = configuration.getClassMapping(APCredit.class.getName());
        assertNotNull(credit.getProperty("createdById"));
        assertNotNull(credit.getProperty("approvedById"));
        assertTrue(configuration.getClassMapping(APPaymentVoucher.class.getName()).isMutable());
    }
}
