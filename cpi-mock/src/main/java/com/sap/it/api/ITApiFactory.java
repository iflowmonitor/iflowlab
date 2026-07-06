package com.sap.it.api;

import java.util.Map;

/**
 * Clean-room mock of SAP CPI's {@code com.sap.it.api.ITApiFactory}. Scripts call the
 * static {@link #getService} / {@link #getApi} (both are used in the wild) to obtain
 * a platform service. The workbench binds a per-run registry on the executing thread
 * — a {@code ThreadLocal}, mirroring the debug runtime — so concurrent runs stay
 * isolated; an unbound thread resolves every service to {@code null}, matching a
 * tenant where the service is unavailable.
 */
public final class ITApiFactory {

    private static final ThreadLocal<Map<Class<?>, Object>> REGISTRY = new ThreadLocal<>();

    private ITApiFactory() {}

    /** Bind the service registry for the current thread (called by the engine before a run). */
    public static void bind(Map<Class<?>, Object> services) {
        REGISTRY.set(services);
    }

    public static void unbind() {
        REGISTRY.remove();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> type, Object context) {
        Map<Class<?>, Object> registry = REGISTRY.get();
        return registry == null ? null : (T) registry.get(type);
    }

    /** Alias for {@link #getService} — CPI scripts use both names interchangeably. */
    public static <T> T getApi(Class<T> type, Object context) {
        return getService(type, context);
    }
}
