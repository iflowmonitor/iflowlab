package com.sap.it.api.securestore;

/**
 * Clean-room mock of SAP CPI's {@code com.sap.it.api.securestore.UserCredential}.
 * {@link #getPassword()} returns a {@code char[]} as the real API does — scripts
 * typically wrap it with {@code new String(cred.getPassword())}.
 */
public interface UserCredential {

    String getUsername();

    char[] getPassword();
}
