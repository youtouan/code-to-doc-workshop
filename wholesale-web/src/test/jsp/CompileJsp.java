import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.jsp.JspFactory;
import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.TldCache;
import org.apache.jasper.servlet.JspCServletContext;
import org.apache.jasper.servlet.TldScanner;
import org.apache.jasper.runtime.JspFactoryImpl;

/**
 * Offline Tomcat 9 JSP compilation using the existing Tomcat distribution.
 * Compile/run with Tomcat lib/*, bin/tomcat-juli.jar and the exploded WAR on the classpath.
 * Arguments: exploded WAR directory, generated-class directory. No server or database is started.
 */
public final class CompileJsp {
    private CompileJsp() { }
    public static void main(String[] args) throws Exception {
        JspFactory.setDefaultFactory(new JspFactoryImpl());
        File root = new File(args[0]).getCanonicalFile();
        final File output = new File(args[1]).getCanonicalFile();
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IllegalStateException("Cannot create JSP compiler output directory");
        }
        final ServletContext context = new JspCServletContext(new PrintWriter(System.out),
                root.toURI().toURL(), CompileJsp.class.getClassLoader(), false, true);
        context.setAttribute(ServletContext.TEMPDIR, output);
        TldScanner scanner = new TldScanner(context, true, false, true);
        scanner.setClassLoader(CompileJsp.class.getClassLoader());
        scanner.scan();
        TldCache cache = new TldCache(context, scanner.getUriTldResourcePathMap(), scanner.getTldResourcePathTaglibXmlMap());
        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME, cache);
        final Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("scratchdir", output.getPath());
        parameters.put("compilerSourceVM", "1.7");
        parameters.put("compilerTargetVM", "1.7");
        parameters.put("javaEncoding", "UTF-8");
        parameters.put("keepgenerated", "true");
        parameters.put("development", "false");
        EmbeddedServletOptions options = new EmbeddedServletOptions(new ServletConfig() {
            public String getServletName() { return "offline-jsp-validation"; }
            public ServletContext getServletContext() { return context; }
            public String getInitParameter(String name) { return parameters.get(name); }
            public Enumeration<String> getInitParameterNames() { return Collections.enumeration(parameters.keySet()); }
        }, context);
        options.setTldCache(cache);
        JspRuntimeContext runtime = new JspRuntimeContext(context, options);
        List<File> pages = new ArrayList<File>();
        collect(root, pages);
        List<String> failures = new ArrayList<String>();
        for (File page : pages) {
            String uri = "/" + root.toURI().relativize(page.toURI()).getPath();
            JspCompilationContext compilation = new JspCompilationContext(uri, options, context, null, runtime);
            compilation.setClassLoader(CompileJsp.class.getClassLoader());
            try {
                compilation.createCompiler().compile(true, false);
                if (uri.startsWith("/WEB-INF/jsp/")) {
                    verifySharedJapanese(compilation);
                }
            } catch (Exception ex) {
                failures.add(uri);
                System.err.println("JSP FAILED: " + uri);
                ex.printStackTrace(System.err);
            }
        }
        runtime.destroy();
        if (!failures.isEmpty()) {
            throw new IllegalStateException(failures.size() + " JSPs failed: " + failures);
        }
        System.out.println("Compiled all " + pages.size() + " JSPs with Tomcat Jasper, Java 7 source/target; "
                + "verified Japanese header, navigation and footer literals in generated servlets.");
    }
    private static void verifySharedJapanese(JspCompilationContext compilation) throws Exception {
        String generated = new String(Files.readAllBytes(new File(compilation.getServletJavaFileName()).toPath()),
                StandardCharsets.UTF_8);
        for (String expected : new String[] {"つばめ卸", "本文へ移動", "業務ナビゲーション", "問い合わせ番号"}) {
            if (!generated.contains(expected)) {
                throw new IllegalStateException("Fragment encoding corrupted Japanese literal: " + expected);
            }
        }
    }
    private static void collect(File directory, List<File> pages) {
        File[] files = directory.listFiles();
        if (files == null) { return; }
        for (File file : files) {
            if (file.isDirectory()) { collect(file, pages); }
            else if (file.getName().endsWith(".jsp")) { pages.add(file); }
        }
    }
}
