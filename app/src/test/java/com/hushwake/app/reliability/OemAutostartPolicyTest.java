package com.hushwake.app.reliability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OemAutostartPolicyTest {
    @Test
    public void physicalVendorBuildsNeedManualAutostartConfirmation() {
        assertTrue(OemAutostartPolicy.requiresManualConfirmation("Xiaomi"));
        assertTrue(OemAutostartPolicy.requiresManualConfirmation("OPPO"));
        assertFalse(OemAutostartPolicy.requiresManualConfirmation("Google"));
        assertFalse(OemAutostartPolicy.requiresManualConfirmation("unknown"));
    }

    @Test
    public void manufacturerConfirmationIsNormalized() {
        assertEquals("xiaomi", OemAutostartPolicy.confirmationKey(" Xiaomi "));
    }

    @Test
    public void vendorSettingsCandidatesPreferTheDedicatedAutostartPage() {
        OemAutostartPolicy.SettingsTarget first =
                OemAutostartPolicy.settingsTargets("Xiaomi").get(0);

        assertEquals("com.miui.securitycenter", first.packageName());
        assertEquals(
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
                first.className());
    }
}
