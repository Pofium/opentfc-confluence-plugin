package org.opentfc.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentfc.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for resolving and parsing external data sources (CSV, JSON, URLs, Attachments).
 * Implements strict SSRF prevention for URL fetching.
 */
public class DataSourceResolver {
    private static final Logger log = LoggerFactory.getLogger(DataSourceResolver.class);
    private static final int MAX_FETCH_SIZE = 10 * 1024 * 1024; // 10 MB limit
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse raw CSV stream into TableModel.
     */
    public TableModel parseCsv(InputStream in, Charset charset, char delimiter) throws IOException {
        TableModel model = new TableModel(UUID.randomUUID().toString());

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(in, charset != null ? charset : StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {

            List<String> headers = parser.getHeaderNames();
            if (headers != null) {
                for (int i = 0; i < headers.size(); i++) {
                    model.addColumn(new ColumnModel(i, headers.get(i), ColumnType.TEXT));
                }
            }

            for (CSVRecord record : parser) {
                RowModel row = new RowModel();
                for (int i = 0; i < model.getColumns().size(); i++) {
                    String val = i < record.size() ? record.get(i) : "";
                    row.addCell(new CellModel(val, val, ColumnType.TEXT));
                }
                model.addRow(row);
            }
        }
        return model;
    }

    /**
     * Parse raw JSON stream into TableModel.
     */
    public TableModel parseJson(InputStream in) throws IOException {
        TableModel model = new TableModel(UUID.randomUUID().toString());
        JsonNode root = objectMapper.readTree(in);

        if (root.isArray() && root.size() > 0) {
            JsonNode first = root.get(0);
            if (first.isObject()) {
                // Array of objects
                List<String> keys = new ArrayList<>();
                first.fieldNames().forEachRemaining(keys::add);
                for (int i = 0; i < keys.size(); i++) {
                    model.addColumn(new ColumnModel(i, keys.get(i), ColumnType.TEXT));
                }

                for (JsonNode node : root) {
                    RowModel row = new RowModel();
                    for (String key : keys) {
                        JsonNode valNode = node.get(key);
                        String val = valNode != null && !valNode.isNull() ? valNode.asText() : "";
                        row.addCell(new CellModel(val, val, ColumnType.TEXT));
                    }
                    model.addRow(row);
                }
            } else if (first.isArray()) {
                // Array of arrays: treat each inner array as a row, columns are positional
                int colCount = 0;
                for (JsonNode node : root) {
                    if (node.isArray()) {
                        colCount = Math.max(colCount, node.size());
                    }
                }
                for (int i = 0; i < colCount; i++) {
                    model.addColumn(new ColumnModel(i, "Column " + (i + 1), ColumnType.TEXT));
                }
                for (JsonNode node : root) {
                    if (!node.isArray()) {
                        continue;
                    }
                    RowModel row = new RowModel();
                    for (int i = 0; i < colCount; i++) {
                        String val = i < node.size() && !node.get(i).isNull() ? node.get(i).asText() : "";
                        row.addCell(new CellModel(val, val, ColumnType.TEXT));
                    }
                    model.addRow(row);
                }
            }
        }
        return model;
    }

    /**
     * Safely fetch stream from external URL with strict SSRF protection.
     */
    public InputStream fetchSafeUrl(String urlString) throws IOException {
        URL url;
        try {
            url = new URI(urlString).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + urlString, e);
        }
        String protocol = url.getProtocol().toLowerCase();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new SecurityException("Forbidden URL protocol: " + protocol);
        }

        // SSRF IP Validation
        InetAddress address = InetAddress.getByName(url.getHost());
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
            throw new SecurityException("SSRF blocked: Requests to internal network addresses are prohibited.");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false); // Disable automatic redirect to prevent SSRF bypass
        conn.setRequestProperty("User-Agent", "OpenTFC-Confluence-Agent/1.0");

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Remote server returned HTTP " + status);
        }

        long contentLength = conn.getContentLengthLong();
        if (contentLength > MAX_FETCH_SIZE) {
            throw new IOException("Remote file exceeds maximum allowed size (" + MAX_FETCH_SIZE + " bytes)");
        }

        return conn.getInputStream();
    }
}
