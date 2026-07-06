package com.iflowmonitor.iflowlab.cpimock.services;

import com.sap.it.api.mapping.ValueMappingApi;
import com.sap.it.api.securestore.SecureStoreService;
import com.sap.it.api.securestore.UserCredential;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bundle of CPI platform services seeded for a run (value mappings + secure-store
 * credentials). {@link #registry()} produces the class→instance map the engine binds
 * into {@link com.sap.it.api.ITApiFactory} on the worker thread.
 */
public final class CpiServices {

    public static final CpiServices EMPTY = new CpiServices(List.of(), Map.of());

    private final ValueMappingApi valueMapping;
    private final SecureStoreService secureStore;

    /** @param credentials alias → (username, password) pairs. */
    public CpiServices(List<ValueMappingEntry> valueMappings, Map<String, String[]> credentials) {
        this.valueMapping = new MapValueMappingApi(valueMappings);
        Map<String, UserCredential> byAlias = new LinkedHashMap<>();
        credentials.forEach((alias, up) ->
                byAlias.put(alias, new SimpleUserCredential(up.length > 0 ? up[0] : "", up.length > 1 ? up[1] : "")));
        this.secureStore = new MapSecureStoreService(byAlias);
    }

    /** The registry the engine binds into {@link com.sap.it.api.ITApiFactory#bind}. */
    public Map<Class<?>, Object> registry() {
        Map<Class<?>, Object> registry = new LinkedHashMap<>();
        registry.put(ValueMappingApi.class, valueMapping);
        registry.put(SecureStoreService.class, secureStore);
        return registry;
    }
}
