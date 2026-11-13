package org.kde.kdeconnect.pingora

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,application=Application::class,sdk=[23])
class LaptopTelemetryTest {
    @Test fun cachedReadingRestoresWithoutLiveBridgeAndSlumberDoesNotFalsifySyncTime() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        LaptopTelemetry.save(app,"laptop",JSONObject().put("battery",JSONObject().put("level",87)).put("cpu_percent",12))
        val first=LaptopTelemetry.read(app,"laptop")
        assertTrue(first.receivedAt>0)
        val reopened=ContextWrapper(app)
        assertEquals(87,LaptopTelemetry.read(reopened,"laptop").data.getJSONObject("battery").getInt("level"))
        LaptopTelemetry.updateSlumber(reopened,"laptop",JSONObject().put("lid_action","sleep"))
        assertEquals(first.receivedAt,LaptopTelemetry.read(app,"laptop").receivedAt)
        assertEquals("Updated 23 hours ago",LaptopTelemetry.read(app,"laptop").age(first.receivedAt+23*3600_000L))
        LaptopTelemetry.forget(app,"laptop")
        assertEquals(0,LaptopTelemetry.read(app,"laptop").receivedAt)
    }
    @Test fun corruptAndOversizeSnapshotsDoNotReplaceValidLastReading() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        LaptopTelemetry.save(app,"laptop",JSONObject().put("cpu_percent",9))
        LaptopTelemetry.save(app,"laptop",JSONObject().put("oversize","x".repeat(25000)))
        assertEquals(9,LaptopTelemetry.read(app,"laptop").data.getInt("cpu_percent"))
        app.getSharedPreferences("pingora_laptop_snapshots",0).edit().putString("broken","not json").commit()
        assertEquals(0,LaptopTelemetry.read(app,"broken").receivedAt)
    }
}
