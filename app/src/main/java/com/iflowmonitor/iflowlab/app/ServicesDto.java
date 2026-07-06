package com.iflowmonitor.iflowlab.app;

import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.cpimock.services.ValueMappingEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CPI platform services supplied inline with a request instead of read from the
 * workspace's services.yaml — the stateless-runner contract (SaaS R1). A control
 * plane loads the tenant's service config and sends it here; the local product
 * keeps using the workspace fallback.
 */
public record ServicesDto(List<ValueMappingDto> valueMappings, Map<String, CredentialDto> credentials) {

    public record ValueMappingDto(
            String sourceAgency,
            String sourceIdentifier,
            String sourceValue,
            String targetAgency,
            String targetIdentifier,
            String value) {}

    public record CredentialDto(String username, String password) {}

    public CpiServices toCpiServices() {
        List<ValueMappingEntry> entries = valueMappings == null ? List.of() : valueMappings.stream()
                .map(v -> new ValueMappingEntry(
                        v.sourceAgency(), v.sourceIdentifier(), v.sourceValue(),
                        v.targetAgency(), v.targetIdentifier(), v.value()))
                .toList();
        Map<String, String[]> creds = new LinkedHashMap<>();
        if (credentials != null) {
            credentials.forEach((alias, c) -> creds.put(alias, new String[] {
                    c.username() == null ? "" : c.username(),
                    c.password() == null ? "" : c.password()}));
        }
        return new CpiServices(entries, creds);
    }
}
