package com.sap.it.api.securestore;

/**
 * Clean-room mock of SAP CPI's {@code com.sap.it.api.securestore.SecureStoreService}.
 * Resolved via {@code ITApiFactory.getService(SecureStoreService.class, null)}.
 */
public interface SecureStoreService {

    /**
     * The credential stored under {@code alias}.
     *
     * @throws com.sap.it.api.securestore.exception.SecureStoreException if no credential
     *     is registered for the alias.
     */
    UserCredential getUserCredential(String alias);
}
