package org.opentfc.service;

/**
 * Single source of truth for the OpenTFC plugin version.
 * All other classes reference this constant instead of hardcoding the version string.
 */
public final class OpenTfcVersion {
    public static final String VERSION = "1.0.6";

    private OpenTfcVersion() {
        // utility class
    }
}
