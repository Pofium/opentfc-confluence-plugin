package org.opentfc.service;

import org.junit.Before;
import org.junit.Test;
import org.opentfc.model.TableModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class DataSourceResolverTest {

    private DataSourceResolver resolver;

    @Before
    public void setUp() {
        resolver = new DataSourceResolver();
    }

    @Test
    public void testParseCsvStream() throws IOException {
        String csv = "Product,Qty,Price\nServer,5,1000\nSwitch,10,200\n";
        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        TableModel model = resolver.parseCsv(in, StandardCharsets.UTF_8, ',');

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
    public void testParseJsonStream() throws IOException {
        String json = "[{\"id\":1,\"name\":\"Item 1\"},{\"id\":2,\"name\":\"Item 2\"}]";
        ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        TableModel model = resolver.parseJson(in);

        assertNotNull(model);
        assertEquals(2, model.getColumns().size());
        assertEquals(2, model.getRows().size());
        assertEquals("1", model.getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("Item 1", model.getRows().get(0).getCells().get(1).getRawValue());
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsLocalhost() throws IOException {
        resolver.fetchSafeUrl("http://localhost:8080/secret");
    }

    @Test(expected = SecurityException.class)
    public void testSsrfProtectionRejectsPrivateIp() throws IOException {
        resolver.fetchSafeUrl("http://192.168.1.1/admin");
    }
}
