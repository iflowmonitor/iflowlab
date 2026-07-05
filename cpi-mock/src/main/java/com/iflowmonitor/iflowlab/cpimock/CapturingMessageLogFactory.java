package com.iflowmonitor.iflowlab.cpimock;

import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.gateway.ip.core.customdev.util.MessageLog;
import com.sap.gateway.ip.core.customdev.util.MessageLogFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The {@code messageLogFactory} the engine binds into a run. Every
 * {@link MessageLog} it hands out records into one shared list, which the engine
 * reads after the run to populate the log pane. Never returns {@code null} —
 * scripts that guard {@code if (messageLog != null)} still take the logging path.
 */
public final class CapturingMessageLogFactory implements MessageLogFactory {

    private final List<LogEntry> entries = new ArrayList<>();

    @Override
    public MessageLog getMessageLog(Message message) {
        return new CapturingMessageLog(entries);
    }

    /** Captured entries in call order (unmodifiable view). */
    public List<LogEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    private record CapturingMessageLog(List<LogEntry> sink) implements MessageLog {
        @Override
        public void setStringProperty(String name, String value) {
            sink.add(LogEntry.stringProperty(name, value));
        }

        @Override
        public void addAttachmentAsString(String name, String payload, String mediaType) {
            sink.add(LogEntry.attachment(name, payload, mediaType));
        }

        @Override
        public void addCustomHeaderProperty(String name, String value) {
            sink.add(LogEntry.customHeader(name, value));
        }
    }
}
