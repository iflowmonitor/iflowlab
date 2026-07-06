package com.sap.it.api.securestore.exception;

/** Clean-room mock of SAP CPI's {@code SecureStoreException} — thrown for an unknown alias. */
public class SecureStoreException extends RuntimeException {

    public SecureStoreException(String message) {
        super(message);
    }
}
