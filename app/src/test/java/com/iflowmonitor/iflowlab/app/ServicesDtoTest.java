package com.iflowmonitor.iflowlab.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.sap.it.api.mapping.ValueMappingApi;
import com.sap.it.api.securestore.SecureStoreService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Inline services payload for the stateless runner contract (SaaS R1). */
class ServicesDtoTest {

    @Test
    void toCpiServices_buildsResolvingValueMappingAndCredential() throws Exception {
        ServicesDto dto = new ServicesDto(
                List.of(new ServicesDto.ValueMappingDto("C4C", "Country", "yMKT", "Country", "Austria", "AT")),
                Map.of("MyAlias", new ServicesDto.CredentialDto("user1", "secret1")));

        CpiServices services = dto.toCpiServices();

        var vm = (ValueMappingApi) services.registry().get(ValueMappingApi.class);
        assertThat(vm.getMappedValue("C4C", "Country", "yMKT", "Country", "Austria")).isEqualTo("AT");
        var store = (SecureStoreService) services.registry().get(SecureStoreService.class);
        var cred = store.getUserCredential("MyAlias");
        assertThat(cred.getUsername()).isEqualTo("user1");
        assertThat(new String(cred.getPassword())).isEqualTo("secret1");
    }

    @Test
    void toCpiServices_nullFields_yieldEmptyServices() {
        CpiServices services = new ServicesDto(null, null).toCpiServices();
        var vm = (ValueMappingApi) services.registry().get(ValueMappingApi.class);
        assertThat(vm.getMappedValue("a", "b", "c", "d", "e")).isNull();
    }

    @Test
    void runRequestDto_prefersInlineServices_overWorkspaceFallback() {
        ServicesDto inline = new ServicesDto(
                List.of(new ServicesDto.ValueMappingDto("A", "B", "C", "D", "E", "inline-wins")),
                Map.of());
        CpiServices fallback = new CpiServices(List.of(), Map.of());

        RunRequestDto dto = new RunRequestDto("s", "b", null, Map.of(), Map.of(), null, null, null, inline);
        var vm = (ValueMappingApi) dto.toRunRequest(fallback).services().registry().get(ValueMappingApi.class);
        assertThat(vm.getMappedValue("A", "B", "C", "D", "E")).isEqualTo("inline-wins");
    }

    @Test
    void runRequestDto_withoutInlineServices_usesFallback() {
        CpiServices fallback = new CpiServices(
                List.of(new com.iflowmonitor.iflowlab.cpimock.services.ValueMappingEntry("A", "B", "C", "D", "E", "fb")),
                Map.of());
        RunRequestDto dto = new RunRequestDto("s", "b", null, Map.of(), Map.of(), null, null, null, null);
        var vm = (ValueMappingApi) dto.toRunRequest(fallback).services().registry().get(ValueMappingApi.class);
        assertThat(vm.getMappedValue("A", "B", "C", "D", "E")).isEqualTo("fb");
    }
}
