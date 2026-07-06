package com.iflowmonitor.iflowlab.cpimock.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sap.it.api.ITApiFactory;
import com.sap.it.api.mapping.ValueMappingApi;
import com.sap.it.api.securestore.SecureStoreService;
import com.sap.it.api.securestore.UserCredential;
import com.sap.it.api.securestore.exception.SecureStoreException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Clean-room CPI service mocks + the ITApiFactory registry (slice 4). */
class CpiServicesTest {

    @AfterEach
    void unbind() {
        ITApiFactory.unbind();
    }

    private CpiServices sample() {
        return new CpiServices(
                List.of(new ValueMappingEntry("C4C", "Country", "yMKT", "Country", "Austria", "AT")),
                Map.of("MyAlias", new String[] {"user1", "secret1"}));
    }

    @Test
    void unboundThread_resolvesEveryServiceToNull() {
        assertThat(ITApiFactory.getService(ValueMappingApi.class, null)).isNull();
        assertThat(ITApiFactory.getApi(SecureStoreService.class, null)).isNull();
    }

    @Test
    void valueMapping_returnsMappedValue_orNullWhenAbsent() {
        ITApiFactory.bind(sample().registry());
        ValueMappingApi vm = ITApiFactory.getApi(ValueMappingApi.class, null);

        assertThat(vm.getMappedValue("C4C", "Country", "yMKT", "Country", "Austria")).isEqualTo("AT");
        assertThat(vm.getMappedValue("C4C", "Country", "unknown", "Country", "Austria")).isNull();
    }

    @Test
    void secureStore_returnsCredential_orThrowsForUnknownAlias() {
        ITApiFactory.bind(sample().registry());
        SecureStoreService store = ITApiFactory.getService(SecureStoreService.class, null);

        UserCredential cred = store.getUserCredential("MyAlias");
        assertThat(cred.getUsername()).isEqualTo("user1");
        assertThat(new String(cred.getPassword())).isEqualTo("secret1");

        assertThatThrownBy(() -> store.getUserCredential("Nope"))
                .isInstanceOf(SecureStoreException.class)
                .hasMessageContaining("Nope");
    }

    @Test
    void bothStaticNames_getServiceAndGetApi_resolveTheSameInstance() {
        ITApiFactory.bind(sample().registry());
        assertThat(ITApiFactory.getService(ValueMappingApi.class, null))
                .isSameAs(ITApiFactory.getApi(ValueMappingApi.class, null));
    }

    @Test
    void emptyServices_bindCleanly_andResolveInstancesThatFindNothing() {
        ITApiFactory.bind(CpiServices.EMPTY.registry());
        assertThat(ITApiFactory.getApi(ValueMappingApi.class, null)
                .getMappedValue("a", "b", "c", "d", "e")).isNull();
    }
}
