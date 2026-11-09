package org.opentfc.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentfc.model.TableModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Single JSON entry/exit point for the OpenTFC REST resources.
 *
 * WHY THIS CLASS EXISTS. The plugin bundles its own Jackson (jackson-databind is a
 * compile dependency, so the classes live inside the bundle). Jersey, however, runs
 * on the Jackson copy exported by the Confluence platform. Because the annotation
 * classes come from different classloaders, the platform Jackson cannot see
 * {@code @JsonProperty("val")} or {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * on our model classes. The observable result on a real server was:
 * <ul>
 *   <li>{@code POST /export/xlsx} failing with
 *       "Unrecognized field &quot;val&quot; (Class org.opentfc.model.CellModel), not marked as ignorable";</li>
 *   <li>{@code GET /source/...} answering with {@code rawValue} instead of the documented
 *       {@code val}, which the frontend then read as an empty cell.</li>
 * </ul>
 *
 * Reading and writing the JSON text ourselves with a mapper instantiated inside our bundle
 * keeps the annotation classes on one side of the classloader, so the wire format is exactly
 * what the model declares and what the frontend expects. Unknown fields are ignored, which also
 * makes the endpoints forward/backward compatible with older plugin builds.
 */
public final class OpenTfcJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private OpenTfcJson() {
        // utility class
    }

    /** Deserializes a request body into the given type, ignoring unknown fields. */
    public static <T> T read(String body, Class<T> type) throws IOException {
        if (body == null || body.trim().isEmpty()) {
            throw new IOException("Request body is empty");
        }
        return MAPPER.readValue(body, type);
    }

    /** Deserializes an exported table. Unknown fields are ignored, legacy cell names are accepted. */
    public static TableModel readTable(String body) throws IOException {
        return read(body, TableModel.class);
    }

    /** Serializes any model object into JSON text using our own annotations. */
    public static String write(Object value) throws IOException {
        return MAPPER.writeValueAsString(value);
    }

    /** Reads a request body stream as UTF-8 text (bounded, to avoid unbounded buffering). */
    public static String readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buf)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Request body exceeds " + maxBytes + " bytes");
            }
            out.write(buf, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
