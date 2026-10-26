package org.opentfc.util;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Verifies i18n resources: every key referenced from the plugin descriptor or UI
 * must exist in the default (en) and Russian bundles, key sets must match, and
 * Russian translations must actually differ from English.
 */
public class I18nKeysTest {

    private static final Pattern DESCRIPTOR_KEY = Pattern.compile("key=\"(opentfc\\.[^\"]+)\"");

    private Properties load(String resource) throws IOException {
        Properties p = new Properties();
        try (InputStream in = I18nKeysTest.class.getResourceAsStream(resource)) {
            assertNotNull("Resource not found: " + resource, in);
            p.load(in);
        }
        return p;
    }

    private Set<String> descriptorKeys() throws IOException {
        String xml = new String(
                I18nKeysTest.class.getResourceAsStream("/atlassian-plugin.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        Set<String> keys = new TreeSet<>();
        Matcher m = DESCRIPTOR_KEY.matcher(xml);
        while (m.find()) {
            keys.add(m.group(1));
        }
        assertFalse("No opentfc.* keys found in plugin descriptor", keys.isEmpty());
        return keys;
    }

    @Test
    public void testAllDescriptorKeysExistInEveryLocale() throws Exception {
        Set<String> keys = descriptorKeys();
        Properties base = load("/i18n/opentfc.properties");
        Properties en = load("/i18n/opentfc_en.properties");
        Properties ru = load("/i18n/opentfc_ru.properties");

        for (String key : keys) {
            assertTrue("Default bundle missing key: " + key, base.containsKey(key));
            assertTrue("EN bundle missing key: " + key, en.containsKey(key));
            assertTrue("RU bundle missing key: " + key, ru.containsKey(key));
        }
    }

    @Test
    public void testKeySetsAreIdenticalAcrossLocales() throws Exception {
        Properties base = load("/i18n/opentfc.properties");
        Properties en = load("/i18n/opentfc_en.properties");
        Properties ru = load("/i18n/opentfc_ru.properties");

        assertEquals(base.keySet(), en.keySet());
        assertEquals(base.keySet(), ru.keySet());
    }

    @Test
    public void testNoEmptyOrWhitespaceOnlyValues() throws Exception {
        for (String res : new String[]{"/i18n/opentfc.properties", "/i18n/opentfc_en.properties", "/i18n/opentfc_ru.properties"}) {
            Properties p = load(res);
            for (String key : p.stringPropertyNames()) {
                String v = p.getProperty(key);
                assertFalse("Empty value for " + key + " in " + res, v == null || v.trim().isEmpty());
            }
        }
    }

    @Test
    public void testRussianTranslationsDifferFromEnglish() throws Exception {
        Properties en = load("/i18n/opentfc_en.properties");
        Properties ru = load("/i18n/opentfc_ru.properties");

        int differing = 0;
        for (String key : en.stringPropertyNames()) {
            if (!en.getProperty(key).equals(ru.getProperty(key))) {
                differing++;
            }
        }
        // The Russian bundle must be genuinely localized, not a copy of English.
        assertTrue("RU bundle looks like a copy of EN (differing=" + differing + ")", differing > 0);
    }
}
