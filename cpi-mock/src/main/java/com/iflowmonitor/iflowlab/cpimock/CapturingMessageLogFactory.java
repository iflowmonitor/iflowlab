package com.iflowmonitor.iflowlab.cpimock;

import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.gateway.ip.core.customdev.util.MessageLog;
import com.sap.gateway.ip.core.customdev.util.MessageLogFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The {@code messageLogFactory} the engine binds into a run. Every
 * {@link MessageLog} it hands out records into one shared list, which the engine
 * reads after the run to populate the log pane. Never returns {@code null} —
 * scripts that guard {@code if (messageLog != null)} still take the logging path.
 *
 * <p>An optional {@code listener} is notified of each entry the moment it is
 * recorded, so the engine can stream log lines live during a run (slice 10)
 * instead of only reading them back afterwards.
 */
public final class CapturingMessageLogFactory implements MessageLogFactory {

    private final List<LogEntry> entries = new ArrayList<>();
    private final Consumer<LogEntry> listener;

    public CapturingMessageLogFactory() {
        this(null);
    }

    public CapturingMessageLogFactory(Consumer<LogEntry> listener) {
        this.listener = listener;
    }

    @Override
    public MessageLog getMessageLog(Message message) {
        return new CapturingMessageLog(entries, listener);
    }

    /** Captured entries in call order (unmodifiable view). */
    public List<LogEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    private record CapturingMessageLog(List<LogEntry> sink, Consumer<LogEntry> listener) implements MessageLog {
        private void record(LogEntry entry) {
            sink.add(entry);
            if (listener != null) {
                listener.accept(entry);
            }
        }

        @Override
        public void setStringProperty(String name, String value) {
            record(LogEntry.stringProperty(name, value));
        }

        @Override
        public void addAttachmentAsString(String name, String payload, String mediaType) {
            record(LogEntry.attachment(name, payload, mediaType));
        }

        @Override
        public void addCustomHeaderProperty(String name, String value) {
            record(LogEntry.customHeader(name, value));
        }
    }
}
