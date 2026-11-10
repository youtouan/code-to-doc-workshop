package jp.co.tsubame.wholesale.web;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class JspEncodingContractTest {
    private final Path webapp = Paths.get("src", "main", "webapp");

    @Test public void everyFragmentDeclaresItsOwnUtf8SourceEncoding() throws Exception {
        DirectoryStream<Path> fragments = Files.newDirectoryStream(
                webapp.resolve(Paths.get("WEB-INF", "jsp", "fragments")), "*.jspf");
        int count = 0;
        try {
            for (Path fragment : fragments) {
                String source = utf8(fragment);
                assertTrue(fragment.toString(), source.matches(
                        "(?s).*<%@\\s*page\\b[^%]*pageEncoding\\s*=\\s*\"UTF-8\"[^%]*%>.*"));
                count++;
            }
        } finally { fragments.close(); }
        assertTrue("Shared fragments must be checked", count >= 8);
    }

    @Test public void webXmlCoversBothJspAndJspfWithUtf8Translation() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(webapp.resolve(Paths.get("WEB-INF", "web.xml")).toFile());
        NodeList groups = document.getElementsByTagNameNS("*", "jsp-property-group");
        boolean jsp = false, fragment = false;
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            NodeList encodings = group.getElementsByTagNameNS("*", "page-encoding");
            if (encodings.getLength() != 1 || !"UTF-8".equals(encodings.item(0).getTextContent())) { continue; }
            NodeList patterns = group.getElementsByTagNameNS("*", "url-pattern");
            for (int j = 0; j < patterns.getLength(); j++) {
                String pattern = patterns.item(j).getTextContent();
                jsp |= "*.jsp".equals(pattern); fragment |= "*.jspf".equals(pattern);
            }
        }
        assertTrue("JSP source encoding is required", jsp);
        assertTrue("Static JSP includes need their own matching property group", fragment);
    }

    @Test public void allPasswordInputsUseTheCore128CharacterLimit() throws Exception {
        Pattern input = Pattern.compile("<input\\b[^>]*\\btype=\"password\"[^>]*>");
        String[] pages = {"login.jsp", "password.jsp", "support/user-edit.jsp", "support/user-detail.jsp"};
        int fields = 0;
        for (String page : pages) {
            String source = utf8(webapp.resolve(Paths.get("WEB-INF", "jsp")).resolve(page.replace('/', java.io.File.separatorChar)));
            Matcher matcher = input.matcher(source);
            while (matcher.find()) {
                assertTrue(page + ": " + matcher.group(), matcher.group().contains("maxlength=\"128\""));
                fields++;
            }
        }
        assertEquals(8, fields);
        assertEquals(128, Inputs.MAX_PASSWORD_LENGTH);
    }
    @Test public void historicalApProductIdsRemainEnterableOutsideActiveSuggestions() throws Exception {
        String invoice = utf8(webapp.resolve(Paths.get("WEB-INF", "jsp", "ap", "invoice-edit.jsp")));
        assertTrue(invoice.contains("name=\"productId\""));
        assertTrue(invoice.contains("list=\"ap-invoice-products\""));
        assertFalse(invoice.contains("<select name=\"productId\""));
        String supplier = utf8(webapp.resolve(Paths.get("WEB-INF", "jsp", "purchasing", "supplier-detail.jsp")));
        assertTrue(supplier.contains("/apInvoices.do?op=new&amp;supplierId="));
    }

    private String utf8(Path path) throws Exception {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
    }
}
