package com.iflowmonitor.iflowlab.cpimock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.gateway.ip.core.customdev.util.Message;
import javax.activation.DataHandler;
import org.junit.jupiter.api.Test;

/**
 * Attachments — slice-1 scope is API-present + in-memory round-trip only (R9).
 * Not fixture-seeded, not rendered. Uses javax.activation.DataHandler (CPI is javax).
 */
class MessageAttachmentsTest {

    @Test
    void attachments_neverNull_andIntraRunRoundTrip() {
        Message m = new Message();
        assertThat(m.getAttachments()).isEmpty();

        DataHandler dh = new DataHandler("payload", "text/plain");
        m.getAttachments().put("file1", dh);

        assertThat(m.getAttachments()).containsEntry("file1", dh);
    }
}
