package com.iflowmonitor.iflowlab.cpimock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.gateway.ip.core.customdev.util.MessageLog;
import com.sap.gateway.ip.core.customdev.util.MessageLogFactory;
import org.junit.jupiter.api.Test;

/**
 * MessageLog capture — what a script logs via {@code messageLogFactory} is
 * recorded so the engine can surface it in the log pane (D6).
 */
class MessageLogCaptureTest {

    @Test
    void factoryReturnsLog_andCapturesStringPropertiesAndAttachments() {
        CapturingMessageLogFactory factory = new CapturingMessageLogFactory();
        Message m = new Message();

        MessageLog log = factory.getMessageLog(m);
        log.setStringProperty("Logging#1", "before");
        log.addAttachmentAsString("Payload", "<a/>", "text/xml");

        assertThat(factory.entries())
                .extracting(LogEntry::kind, LogEntry::name, LogEntry::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LogEntry.Kind.STRING_PROPERTY, "Logging#1", "before"),
                        org.assertj.core.groups.Tuple.tuple(LogEntry.Kind.ATTACHMENT, "Payload", "<a/>"));
    }

    @Test
    void factoryNeverReturnsNull_soScriptsGuardingOnNullStillRun() {
        assertThat(new CapturingMessageLogFactory().getMessageLog(new Message())).isNotNull();
    }
}
