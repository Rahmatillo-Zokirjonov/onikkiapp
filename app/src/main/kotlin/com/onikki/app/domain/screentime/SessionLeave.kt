package com.onikki.app.domain.screentime

/**
 * "Opened until you leave it" unlocks end when the user really switches away. Not every window
 * change is leaving: the keyboard, the notification shade, share sheets and permission dialogs
 * (packages with no launcher entry) show up on top of the app for a moment.
 */
object SessionLeave {
    private val TRANSIENT = setOf("com.android.systemui", "android")

    /**
     * @param hasLauncherEntry whether [foreground] is a normal app you can open from the home screen.
     * @return true when [foreground] coming up means the user left the unlocked app.
     */
    fun isLeaving(
        foreground: String,
        ownPackage: String,
        homePackages: Set<String>,
        keyboardPackages: Set<String>,
        hasLauncherEntry: Boolean
    ): Boolean = when {
        foreground == ownPackage -> false
        foreground in TRANSIENT -> false
        foreground in keyboardPackages -> false
        foreground in homePackages -> true
        else -> hasLauncherEntry
    }
}
