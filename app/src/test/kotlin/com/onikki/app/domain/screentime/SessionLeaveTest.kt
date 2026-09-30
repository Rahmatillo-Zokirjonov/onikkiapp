package com.onikki.app.domain.screentime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLeaveTest {
    private fun leaving(pkg: String, launcher: Boolean) = SessionLeave.isLeaving(
        foreground = pkg,
        ownPackage = "com.onikki.app",
        homePackages = setOf("com.miui.home"),
        keyboardPackages = setOf("com.google.android.inputmethod.latin"),
        hasLauncherEntry = launcher
    )

    @Test
    fun homeScreenAndOtherAppsEndTheUnlock() {
        assertTrue(leaving("com.miui.home", launcher = false))
        assertTrue(leaving("org.telegram.messenger", launcher = true))
    }

    @Test
    fun overlaysDoNotEndIt() {
        assertFalse(leaving("com.onikki.app", launcher = true))
        assertFalse(leaving("com.android.systemui", launcher = false))
        assertFalse(leaving("android", launcher = false))
        assertFalse(leaving("com.google.android.inputmethod.latin", launcher = true))
        assertFalse(leaving("com.google.android.permissioncontroller", launcher = false))
    }
}
