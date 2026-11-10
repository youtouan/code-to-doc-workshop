package jp.co.tsubame.wholesale.web;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class CsrfTest {
    @Test public void tokensAreRandom256BitHex() {
        Set<String> tokens = new HashSet<String>();
        for (int i = 0; i < 300; i++) {
            String token = Web.randomToken();
            assertTrue(token.matches("[a-f0-9]{64}"));
            assertTrue(tokens.add(token));
        }
    }
    @Test public void everyCharacterParticipatesInComparison() {
        String token = Web.randomToken();
        assertTrue(Web.sameToken(token, token));
        assertFalse(Web.sameToken(null, token));
        assertFalse(Web.sameToken(token, null));
        assertFalse(Web.sameToken(token, token + "0"));
        assertFalse(Web.sameToken("", ""));
        for (int i = 0; i < token.length(); i++) {
            char[] changed = token.toCharArray(); changed[i] = changed[i] == '0' ? '1' : '0';
            assertFalse(Web.sameToken(token, new String(changed)));
        }
    }
    @Test public void tokenIsBoundToSessionAndStableAcrossSafeRequests() {
        HttpFixture first = new HttpFixture(), second = new HttpFixture();
        String token = Web.token(first.createSession());
        assertEquals(token, Web.token(first.request.getSession()));
        assertFalse(Web.sameToken(token, Web.token(second.createSession())));
        first.request.getSession().invalidate();
        assertFalse(Web.sameToken(token, Web.token(first.request.getSession(true))));
    }
}
