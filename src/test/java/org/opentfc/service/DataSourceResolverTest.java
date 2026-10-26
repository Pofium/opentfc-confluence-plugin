package org.opentfc.service;

import org.junit.Before;
import org.junit.Test;
import org.opentfc.model.TableModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class DataSourceResolverTest {

    private DataSourceResolver resolver;

    @Before
    public void setUp() {
        resolver = new DataSourceResolver();
    }

    private ByteArrayInputStream stream(String content, Charset charset) {
        return new ByteArrayInputStream(content.getBytes(charset));
    }

    @Test
    public void testParseCsvStream() throws IOException {
        String csv = "Product,Qty,Price\nServer,5,1000\nSwitch,10,200\n";
        TableModel model = resolver.parseCsv(stream(csv, StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',');

        assertNotNull(model);
        assertEquals(3, model.getColumns().size());
        assertEquals("Product", model.getColumns().get(0).getName());
        assertEquals("Qty", model.getColumns().get(1).getName());
        assertEquals("Price", model.getColumns().get(2).getName());
        assertEquals(2, model.getRows().size());
        assertEquals("Server", model.getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("5", model.getRows().get(0).getCells().get(1).getRawValue());
    }

    @Test
    public void testParseCsvWithSemicolonDelimiter() throws IOException {
        String csv = "Name;Age\nAlice;30\nBob;25\n";
        TableModel model = resolver.parseCsv(stream(csv, StandardCharsets.UTF_8), StandardCharsets.UTF_8, ';');
        assertEquals(2, model.getColumns().size());
        assertEquals("Name", model.getColumns().get(0).getName());
        assertEquals("Alice", model.getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("30", model.getRows().get(0).getCells().get(1).getRawValue());
    }

    @Test
    public void testParseCsvCp1251Encoding() throws IOException {
        // "Имя;Город" in cp1251
        String csv = "Имя;Город\nИван;Москва\n";
        Charset cp1251 = Charset.forName("windows-1251");
        TableModel model = resolver.parseCsv(stream(csv, cp1251), cp1251, ';');
        assertEquals(2, model.getColumns().size());
        assertEquals("Имя", model.getColumns().get(0).getName());
        assertEquals("Иван", model.getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("Москва", model.getRows().get(0).getCells().get(1).getRawValue());
    }

    @Test
    public void testParseCsvQuotedFieldsWithComma() throws IOException {
        String csv = "Name,Comment\nAlice,\"hello, world\"\n";
        TableModel model = resolver.parseCsv(stream(csv, StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',');
        assertEquals("hello, world", model.getRows().get(0).getCells().get(1).getRawValue());
    }

    @Test
    public void testParseCsvMissingCellsArePadded() throws IOException {
        String csv = "A,B,C\n1,2\n";
        TableModel model = resolver.parseCsv(stream(csv, StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',');
        assertEquals(1, model.getRows().size());
        assertEquals(3, model.getRows().get(0).getCells().size());
        assertEquals("", model.getRows().get(0).getCells().get(2).getRawValue());
    }

    @Test
    public void testParseJsonArrayOfObjects() throws IOException {
        String json = "[{\"id\":1,\"name\":\"Item 1\"},{\"id\":2,\"name\":\"Item 2\"}]";
        TableModel model = resolver.parseJson(stream(json, StandardCharsets.UTF_8));
        assertNotNull(model);
        assertEquals(2, model.getColumns().size());
        assertEquals(2, model.getRows().size());
        assertEquals("1", model.getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("Item 1", model.getRows().get(0).getCells().get(1).getRawValue());
    }

    @Test
    public void testParseJsonArrayOfArrays() throws IOException {
        String json = "[[\"a\",1],[\"b\",2]]";
        TableModel model = resolver.parseJson(stream(json, StandardCharsets.UTF_8));
        assertNotNull(model);
        assertEquals(2, model.getRows().size());
    }

    @Test
    public void testParseJsonMissingFieldsBecomeEmpty() throws IOException {
        String json = "[{\"id\":1,\"name\":\"A\"},{\"id\":2}]";
        TableModel model = resolver.parseJson(stream(json, StandardCharsets.UTF_8));
        assertEquals("", model.getRows().get(1).getCells().get(1).getRawValue());
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsLocalhost() throws IOException {
        resolver.fetchSafeUrl("http://localhost:8080/secret");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsLoopbackIp() throws IOException {
        resolver.fetchSafeUrl("http://127.0.0.1/admin");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsPrivateIp() throws IOException {
        resolver.fetchSafeUrl("http://192.168.1.1/admin");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsLinkLocalIp() throws IOException {
        resolver.fetchSafeUrl("http://169.254.1.1/x");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsAnyLocalIp() throws IOException {
        resolver.fetchSafeUrl("http://0.0.0.0/x");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsNonHttpProtocol() throws IOException {
        resolver.fetchSafeUrl("file:///etc/passwd");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsFtpProtocol() throws IOException {
        resolver.fetchSafeUrl("ftp://example.com/file");
    }
}
