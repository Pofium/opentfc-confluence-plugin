package org.opentfc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.opentfc.model.CellModel;
import org.opentfc.model.ColumnModel;
import org.opentfc.model.ColumnType;
import org.opentfc.model.RowModel;
import org.opentfc.model.TableModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CSV/JSON data into TableModel and fetches external URLs with SSRF protection.
 */
public class DataSourceResolver {
    public static final int MAX_FETCH_SIZE = 10 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final Pattern PATH_TOKEN = Pattern.compile("\\.([^.\\[\\]]+)|\\[(\\d+)]");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Decode bytes: explicit charset, or auto (UTF-8 BOM / UTF-16 BOM / strict UTF-8 / windows-1251). */
    public String decode(byte[] data, String encoding) {
        if (encoding != null && !encoding.isBlank() && !"auto".equalsIgnoreCase(encoding)) {
            String text = new String(data, Charset.forName(encoding.trim()));
            return stripBom(text);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            return stripBom(text);
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("windows-1251"));
        }
    }

    private String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }

    /** Pick the most frequent delimiter among , ; TAB | in the first line. */
    public char detectDelimiter(String text) {
        int nl = text.indexOf('\n');
        String first = nl >= 0 ? text.substring(0, nl) : text;
        char best = ',';
        long bestCount = 0;
        for (char c : new char[]{',', ';', '\t', '|'}) {
            long cnt = first.chars().filter(ch -> ch == c).count();
            if (cnt > bestCount) {
                best = c;
                bestCount = cnt;
            }
        }
        return best;
    }

    /** Parse CSV text (first row = header). Delimiter "auto"/empty triggers detection. */
    public TableModel parseCsv(String text, String delimiter) throws IOException {
        char delim;
        if (delimiter == null || delimiter.isEmpty() || "auto".equalsIgnoreCase(delimiter)) {
            delim = detectDelimiter(text);
        } else if ("tab".equalsIgnoreCase(delimiter) || "\\t".equals(delimiter)) {
            delim = '\t';
        } else {
            delim = delimiter.charAt(0);
        }

        TableModel model = new TableModel(UUID.randomUUID().toString());
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delim).setTrim(true)
                .setIgnoreEmptyLines(true).build();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            boolean header = true;
            for (CSVRecord record : parser) {
                if (header) {
                    for (int i = 0; i < record.size(); i++) {
                        String name = record.get(i).isEmpty() ? "Column " + (i + 1) : record.get(i);
                        model.addColumn(new ColumnModel(i, name, ColumnType.TEXT));
                    }
                    header = false;
                    continue;
                }
                RowModel row = new RowModel();
                for (int i = 0; i < model.getColumns().size(); i++) {
                    String val = i < record.size() ? record.get(i) : "";
                    row.addCell(new CellModel(val, null, ColumnType.TEXT));
                }
                model.addRow(row);
            }
        }
        return model;
    }

    /** Backward-compatible stream variant. */
    public TableModel parseCsv(InputStream in, Charset charset, char delimiter) throws IOException {
        byte[] data = readLimited(in, MAX_FETCH_SIZE);
        return parseCsv(decode(data, charset != null ? charset.name() : "auto"), String.valueOf(delimiter));
    }

    /** Parse JSON: array of objects or array of arrays (first = header), optional simple path ($.a.b[0]). */
    public TableModel parseJson(String text, String jsonPath) throws IOException {
        JsonNode node = select(objectMapper.readTree(text), jsonPath);
        TableModel model = new TableModel(UUID.randomUUID().toString());
        if (node == null || !node.isArray() || node.isEmpty()) {
            return model;
        }

        JsonNode first = node.get(0);
        if (first.isObject()) {
            List<String> keys = new ArrayList<>();
            for (JsonNode item : node) {
                Iterator<String> it = item.fieldNames();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!keys.contains(k)) {
                        keys.add(k);
                    }
                }
            }
            for (int i = 0; i < keys.size(); i++) {
                model.addColumn(new ColumnModel(i, keys.get(i), ColumnType.TEXT));
            }
            for (JsonNode item : node) {
                RowModel row = new RowModel();
                for (String key : keys) {
                    row.addCell(new CellModel(asText(item.get(key)), null, ColumnType.TEXT));
                }
                model.addRow(row);
            }
        } else if (first.isArray()) {
            for (int i = 0; i < first.size(); i++) {
                model.addColumn(new ColumnModel(i, "Column " + (i + 1), ColumnType.TEXT));
            }
            for (int r = 0; r < node.size(); r++) {
                JsonNode arr = node.get(r);
                RowModel row = new RowModel();
                for (int i = 0; i < model.getColumns().size(); i++) {
                    row.addCell(new CellModel(asText(arr.get(i)), null, ColumnType.TEXT));
                }
                model.addRow(row);
            }
        }
        return model;
    }

    public TableModel parseJson(InputStream in) throws IOException {
        return parseJson(decode(readLimited(in, MAX_FETCH_SIZE), "UTF-8"), "$");
    }

    private String asText(JsonNode n) {
        if (n == null || n.isNull()) {
            return "";
        }
        return n.isValueNode() ? n.asText() : n.toString();
    }

    private JsonNode select(JsonNode root, String path) {
        if (path == null || path.isBlank() || "$".equals(path.trim())) {
            return root;
        }
        String p = path.trim();
        if (p.startsWith("$")) {
            p = p.substring(1);
        }
        Matcher m = PATH_TOKEN.matcher(p);
        JsonNode cur = root;
        while (m.find() && cur != null) {
            cur = m.group(1) != null ? cur.get(m.group(1)) : cur.get(Integer.parseInt(m.group(2)));
        }
        return cur;
    }

    /** Fetch with default whitelist (any external host). */
    public byte[] fetchSafeUrl(String urlString) throws IOException {
        return fetchSafeUrl(urlString, List.of("*"));
    }

    /**
     * Fetch an external URL with SSRF protection: http/https only, host must be in whitelist,
     * all resolved IPs must be public, no redirects, size and time limits.
     */
    public byte[] fetchSafeUrl(String urlString, Collection<String> whitelist) throws IOException {
        URL url;
        try {
            url = new URI(urlString).toURL();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Invalid URL", e);
        }
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new SecurityException("Forbidden URL protocol: " + protocol);
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (whitelist == null || whitelist.stream().noneMatch(w -> hostMatches(host, w))) {
            throw new SecurityException("Host is not in the OpenTFC URL whitelist: " + host);
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                throw new SecurityException("SSRF blocked: internal address " + address.getHostAddress());
            }
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "OpenTFC-Confluence/1.0");
        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Remote server returned HTTP " + status);
        }
        if (conn.getContentLengthLong() > MAX_FETCH_SIZE) {
            throw new IOException("Remote file exceeds " + MAX_FETCH_SIZE + " bytes");
        }
        try (InputStream in = conn.getInputStream()) {
            return readLimited(in, MAX_FETCH_SIZE);
        }
    }

    private boolean hostMatches(String host, String rule) {
        String r = rule.trim().toLowerCase(Locale.ROOT);
        if (r.isEmpty()) {
            return false;
        }
        if ("*".equals(r)) {
            return true;
        }
        if (r.startsWith("*.")) {
            return host.endsWith(r.substring(1));
        }
        return host.equals(r);
    }

    /** Read a stream fully, failing if it exceeds maxBytes. */
    public static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("Data exceeds " + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
