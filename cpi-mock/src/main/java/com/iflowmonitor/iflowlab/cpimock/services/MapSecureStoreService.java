package com.iflowmonitor.iflowlab.cpimock.services;

import com.sap.it.api.securestore.SecureStoreService;
import com.sap.it.api.securestore.UserCredential;
import com.sap.it.api.securestore.exception.SecureStoreException;
import java.util.Map;

/** A {@link SecureStoreService} backed by a fixed map of alias → credential. */
public final class MapSecureStoreService implements SecureStoreService {

    private final Map<String, UserCredential> byAlias;

    public MapSecureStoreService(Map<String, UserCredential> byAlias) {
        this.byAlias = byAlias;
    }

    @Override
    public UserCredential getUserCredential(String alias) {
        UserCredential credential = byAlias.get(alias);
        if (credential == null) {
            throw new SecureStoreException("No credential deployed for alias: " + alias);
        }
        return credential;
    }
}
