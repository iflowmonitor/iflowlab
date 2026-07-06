package javax.activation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal clean-room {@code javax.activation.DataHandler} for the CPI attachment
 * API. CPI runs on {@code javax.activation} (pre-Jakarta), and Quarkus strips the
 * real {@code com.sun.activation:javax.activation} artifact — so we provide our own
 * at this package/name (there is no {@code javax.activation} in the JDK to clash
 * with). Slice-1 scope: hold content + content-type for the in-memory round-trip (R9).
 */
public class DataHandler {

    private final Object content;
    private final String contentType;

    public DataHandler(Object content, String contentType) {
        this.content = content;
        this.contentType = contentType;
    }

    public Object getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getInputStream() {
        if (content instanceof byte[] b) {
            return new ByteArrayInputStream(b);
        }
        if (content instanceof InputStream in) {
            return in;
        }
        return new ByteArrayInputStream(String.valueOf(content).getBytes(StandardCharsets.UTF_8));
    }
}
