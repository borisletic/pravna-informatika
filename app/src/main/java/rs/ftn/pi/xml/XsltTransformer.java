package rs.ftn.pi.xml;

import lombok.extern.slf4j.Slf4j;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;
import java.io.StringWriter;

/**
 * Konvertuje Akoma Ntoso XML u HTML za prikaz korisniku.
 *
 * VLASNIK: Član 3 (Application).
 * CELINA: 7.
 *
 * XSLT fajlovi su u src/main/resources/xslt/.
 */
@Slf4j
@Component
public class XsltTransformer {

    private final Processor processor = new Processor(false);

    public String lawToHtml(String akomaNtosoXml) {
        return transform(akomaNtosoXml, "xslt/law_to_html.xsl");
    }

    public String judgmentToHtml(String akomaNtosoXml) {
        return transform(akomaNtosoXml, "xslt/judgment_to_html.xsl");
    }

    private String transform(String inputXml, String xsltClasspath) {
        try {
            XsltCompiler compiler = processor.newXsltCompiler();
            ClassPathResource xsltResource = new ClassPathResource(xsltClasspath);
            XsltExecutable executable = compiler.compile(
                    new StreamSource(xsltResource.getInputStream()));

            net.sf.saxon.s9api.XsltTransformer transformer = executable.load();
            transformer.setSource(new StreamSource(new java.io.StringReader(inputXml)));

            StringWriter output = new StringWriter();
            Serializer serializer = processor.newSerializer(output);
            serializer.setOutputProperty(Serializer.Property.METHOD, "html");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            transformer.setDestination(serializer);
            transformer.transform();

            return output.toString();
        } catch (Exception e) {
            log.error("XSLT transformacija nije uspela: {}", e.getMessage(), e);
            return "<div class='error'>Greška prilikom prikaza dokumenta.</div>";
        }
    }
}
