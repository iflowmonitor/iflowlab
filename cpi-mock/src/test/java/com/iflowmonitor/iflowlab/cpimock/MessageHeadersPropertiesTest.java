package com.iflowmonitor.iflowlab.cpimock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Header/property storage + type preservation (R11 slice-1 fidelity). */
class MessageHeadersPropertiesTest {

    @Test
    void headers_preserveValueTypes() {
        Message m = new Message();
        m.setHeader("SapDocumentNumber", 4711);        // Integer, not "4711"
        m.setHeader("ContentType", "application/xml");

        assertThat(m.getHeaders().get("SapDocumentNumber")).isEqualTo(4711);
        assertThat(m.getHeader("SapDocumentNumber", Integer.class)).isEqualTo(4711);
        assertThat(m.getHeaders().get("ContentType")).isEqualTo("application/xml");
    }

    @Test
    void setHeaders_replacesWholeMap() {
        Message m = new Message();
        m.setHeader("a", "1");
        m.setHeaders(Map.of("b", "2"));

        assertThat(m.getHeaders()).containsOnlyKeys("b");
    }

    @Test
    void headers_neverNull_evenBeforeAnySet() {
        assertThat(new Message().getHeaders()).isEmpty();
    }

    @Test
    void properties_preserveValueTypes_andTypedGet() {
        Message m = new Message();
        m.setProperty("retryCount", 3);
        m.setProperty("flag", Boolean.TRUE);

        assertThat(m.getProperty("retryCount")).isEqualTo(3);
        assertThat(m.getProperties().get("flag")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void setProperties_replacesWholeMap_andNeverNull() {
        Message m = new Message();
        m.setProperty("x", "1");
        m.setProperties(Map.of("y", "2"));

        assertThat(m.getProperties()).containsOnlyKeys("y");
        assertThat(new Message().getProperties()).isEmpty();
    }
}
