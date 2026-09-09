package com.cachens.nexusdashboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ForecaApiKeyTest {
    @Test
    public void savedKeyOverridesBundledDefault() {
        assertEquals("saved-key", MainActivity.selectForecaApiKey(" saved-key ", "default-key"));
    }

    @Test
    public void blankSavedKeyUsesBundledDefault() {
        assertEquals("default-key", MainActivity.selectForecaApiKey("", " default-key "));
        assertEquals("default-key", MainActivity.selectForecaApiKey("  ", "default-key"));
        assertEquals("default-key", MainActivity.selectForecaApiKey(null, "default-key"));
    }

    @Test
    public void noConfiguredKeysPreservesMissingKeyFlow() {
        assertEquals("", MainActivity.selectForecaApiKey("", ""));
        assertEquals("", MainActivity.selectForecaApiKey(null, null));
    }

    @Test
    public void savedKeyWorksWithoutBundledDefault() {
        assertEquals("saved-key", MainActivity.selectForecaApiKey("saved-key", ""));
    }

    @Test
    public void buildTimeEnvironmentKeyIsEncodedWithoutChanges() {
        String environmentKey = System.getenv("FORECA_API_KEY");
        if (environmentKey != null && environmentKey.trim().length() > 0) {
            assertTrue("Build-time key must match its configured value",
                    environmentKey.trim().equals(BuildConfig.FORECA_API_KEY));
        }
    }
}