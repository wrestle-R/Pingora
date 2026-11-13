package org.kde.kdeconnect.pingora
import org.junit.Assert.*
import org.junit.Test
class TelemetryAgeTest {
    @Test fun offlineAgeDoesNotBecomeZeroOrWrapAfterHoursOrReboot() {
        val saved=1_700_000_000_000L
        assertEquals("Updated 23 hours ago",TelemetryAge.label(saved,saved+23*3600_000L))
        assertEquals("Updated 2 days ago",TelemetryAge.label(saved,saved+48*3600_000L))
        assertEquals("Not synced yet",TelemetryAge.label(0,saved))
        assertEquals("Saved reading · check phone clock",TelemetryAge.label(saved,saved-1))
    }
}
