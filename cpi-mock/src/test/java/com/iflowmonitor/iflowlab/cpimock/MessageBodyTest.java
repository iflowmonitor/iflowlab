package com.iflowmonitor.iflowlab.cpimock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.gateway.ip.core.customdev.util.Message;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Body coercion matrix — the fidelity core (R11). */
class MessageBodyTest {

    @Test
    void stringBody_roundTrips_raw_and_asString() {
        Message m = new Message();
        m.setBody("<a>hi</a>");

        assertThat(m.getBody()).isEqualTo("<a>hi</a>");
        assertThat(m.getBody(String.class)).isEqualTo("<a>hi</a>");
    }

    @Test
    void stringBody_coercesTo_bytes_and_stream() throws Exception {
        Message m = new Message();
        m.setBody("héllo");

        assertThat(m.getBody(byte[].class)).isEqualTo("héllo".getBytes(StandardCharsets.UTF_8));
        assertThat(readAll(m.getBody(InputStream.class))).isEqualTo("héllo");
    }

    @Test
    void bytesBody_coercesTo_string_and_bytes() {
        byte[] raw = "wörld".getBytes(StandardCharsets.UTF_8);
        Message m = new Message();
        m.setBody(raw);

        assertThat(m.getBody(String.class)).isEqualTo("wörld");
        assertThat(m.getBody(byte[].class)).isEqualTo(raw);
    }

    @Test
    void inputStreamBody_isOneShot_readOnceThenDrained() throws Exception {
        // The documented CPI gotcha: an InputStream body is consumed once.
        Message m = new Message();
        m.setBody(new ByteArrayInputStream("stream-payload".getBytes(StandardCharsets.UTF_8)));

        assertThat(m.getBody(String.class)).isEqualTo("stream-payload");
        // Second read sees a drained stream.
        assertThat(m.getBody(String.class)).isEmpty();
    }

    @Test
    void nullBody_coercesToNull() {
        Message m = new Message();
        assertThat(m.getBody(String.class)).isNull();
        assertThat(m.getBody(byte[].class)).isNull();
    }

    private static String readAll(InputStream in) throws Exception {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
