package com.sap.gateway.ip.core.customdev.util;

/**
 * Clean-room mock of the CPI {@code MessageLog} obtained via
 * {@code messageLogFactory.getMessageLog(message)}. Slice-1 surface: the
 * methods scripts commonly call to attach diagnostics to the MPL.
 */
public interface MessageLog {

    void setStringProperty(String name, String value);

    void addAttachmentAsString(String name, String payload, String mediaType);

    void addCustomHeaderProperty(String name, String value);
}
