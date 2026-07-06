package com.iflowmonitor.iflowlab.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflowmonitor.iflowlab.cpimock.services.CpiServices;
import com.iflowmonitor.iflowlab.cpimock.services.ValueMappingEntry;
import com.sap.it.api.ITApiFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A script using the CPI service mocks runs end-to-end through the engine (slice 4). */
class ServiceMockRunTest {

    private final GroovyRunEngine engine = new GroovyRunEngine();

    private static RunRequest request(String script, CpiServices services) {
        return new RunRequest(script, "in".getBytes(StandardCharsets.UTF_8), "text/plain",
                Map.of(), Map.of(), List.of(), 5_000L, services);
    }

    @Test
    void scriptResolvesValueMappingAndCredential_throughITApiFactory() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "import com.sap.it.api.ITApiFactory\n"
                        + "import com.sap.it.api.mapping.ValueMappingApi\n"
                        + "import com.sap.it.api.securestore.SecureStoreService\n"
                        + "Message processData(Message message) {\n"
                        + "    def vm = ITApiFactory.getApi(ValueMappingApi.class, null)\n"
                        + "    def country = vm.getMappedValue('C4C','Country','yMKT','Country','Austria')\n"
                        + "    def store = ITApiFactory.getService(SecureStoreService.class, null)\n"
                        + "    def cred = store.getUserCredential('MyAlias')\n"
                        + "    message.setBody(country + ':' + cred.getUsername() + ':' + new String(cred.getPassword()))\n"
                        + "    return message\n"
                        + "}\n";
        CpiServices services = new CpiServices(
                List.of(new ValueMappingEntry("C4C", "Country", "yMKT", "Country", "Austria", "AT")),
                Map.of("MyAlias", new String[] {"user1", "secret1"}));

        RunResult result = engine.run(request(script, services));

        assertThat(result.status()).isEqualTo(RunResult.Status.OK);
        assertThat(result.body().inline()).isEqualTo("AT:user1:secret1");
    }

    @Test
    void servicesAreUnboundAfterRun_noLeakToOtherThreads() throws Exception {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) { return message }\n";
        engine.run(request(script, CpiServices.EMPTY));

        // The calling thread was never bound; and the worker unbinds in finally.
        assertThat(ITApiFactory.getApi(com.sap.it.api.mapping.ValueMappingApi.class, null)).isNull();
    }
}
