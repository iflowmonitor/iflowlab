package com.sap.it.api.mapping;

/**
 * Clean-room mock of SAP CPI's {@code com.sap.it.api.mapping.ValueMappingApi}.
 * Groovy scripts resolve it via {@code ITApiFactory.getApi(ValueMappingApi.class, null)}
 * and call {@link #getMappedValue} to translate a value between agencies/identifiers.
 */
public interface ValueMappingApi {

    /**
     * The target value mapped from the source coordinates, or {@code null} if no
     * mapping is defined (CPI returns null for an unknown mapping).
     */
    String getMappedValue(
            String sourceAgency,
            String sourceIdentifier,
            String sourceValue,
            String targetAgency,
            String targetIdentifier);
}
