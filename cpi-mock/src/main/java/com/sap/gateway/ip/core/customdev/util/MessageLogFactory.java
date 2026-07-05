package com.sap.gateway.ip.core.customdev.util;

/**
 * Clean-room mock of the CPI {@code MessageLogFactory} bound into the script as
 * {@code messageLogFactory}. Returns a {@link MessageLog} for the message.
 */
public interface MessageLogFactory {

    MessageLog getMessageLog(Message message);
}
