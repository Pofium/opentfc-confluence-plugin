package org.opentfc.admin;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AdminActionTest {

    private static class MapPluginSettings implements PluginSettings {
        private final Map<String, String> data = new HashMap<>();

        @Override
        public Object get(String key) {
            return data.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            return data.put(key, String.valueOf(value));
        }

        @Override
        public Object remove(String key) {
            return data.remove(key);
        }
    }

    private static class MapSettingsFactory implements PluginSettingsFactory {
        private final MapPluginSettings settings = new MapPluginSettings();

        @Override
        public PluginSettings createGlobalSettings() {
            return settings;
        }

        @Override
        public PluginSettings createSettingsForKey(String key) {
            return settings;
        }
    }

    private MapSettingsFactory factory;

    @Before
    public void setUp() {
        factory = new MapSettingsFactory();
    }

    @Test
    public void testDefaultValuesWhenNothingStored() throws Exception {
        OpenTfcAdminAction action = new OpenTfcAdminAction(factory);
        assertEquals("success", action.execute());
        assertEquals(10, action.getSqlTimeoutSeconds());
        assertEquals(50000, action.getMaxResultRows());
        assertEquals("", action.getAllowedUrlWhitelist());
    }

    @Test
    public void testSaveThenLoadRoundTrip() throws Exception {
        OpenTfcAdminAction saver = new OpenTfcAdminAction(factory);
        saver.setSqlTimeoutSeconds(15);
        saver.setMaxResultRows(1234);
        saver.setAllowedUrlWhitelist("example.com\nexample.org");
        saver.save();
        assertTrue(saver.isSaved());

        OpenTfcAdminAction loader = new OpenTfcAdminAction(factory);
        loader.execute();
        assertEquals(15, loader.getSqlTimeoutSeconds());
        assertEquals(1234, loader.getMaxResultRows());
        assertEquals("example.com\nexample.org", loader.getAllowedUrlWhitelist());
    }

    @Test
    public void testInvalidStoredNumberFallsBackToDefault() throws Exception {
        factory.createGlobalSettings().put("org.opentfc.settings.sqlTimeoutSeconds", "not-a-number");
        OpenTfcAdminAction action = new OpenTfcAdminAction(factory);
        action.execute();
        assertEquals(10, action.getSqlTimeoutSeconds());
    }
}
