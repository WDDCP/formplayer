package beans;

import session.FormSession;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Created by willpride on 1/20/16.
 */
public class GetInstanceResponseBean {
    private String output;
    private String xmlns;

    // Jackson requires the default constructor be present
    public GetInstanceResponseBean(){}

    public GetInstanceResponseBean(FormSession session) throws IOException {
        output = indentXml(session.getInstanceXml());
        xmlns = session.getXmlns();
    }

    /**
     * Given a String representation of a valid XML document, this returns an
     * indented version of it.
     * @param xml - A string XML document
     * @return A string XML document that is indented
     */
    private String indentXml(String xml) {
        Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");


        Source xmlInput = new StreamSource(new StringReader(xml));
        StreamResult result = new StreamResult(new StringWriter());

        try {
            transformer.transform(xmlInput, result);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
        return result.getWriter().toString();
    }

    public String getXmlns() {
        return xmlns;
    }

    public void setXmlns(String xmlns) {
        this.xmlns = xmlns;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    @Override
    public String toString(){
        return "GetInstanceResponseBean: [xmlns=" + xmlns + ", output=" + output + "]";
    }
}
