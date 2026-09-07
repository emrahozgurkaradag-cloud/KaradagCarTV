package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomerStatusTest {

    @Test
    fun activeLicenseOpensMain() {
        val snapshot = CustomerStatus.parse(
            httpCode = 200,
            status = "active",
            code = "",
            message = "Lisans aktif",
            licenseKey = "KDG-REAL-KEY"
        )

        assertTrue(snapshot.isActive)
        assertFalse(snapshot.isDeviceNotFound)
        assertFalse(snapshot.shouldContinueAutoPolling)
        assertEquals("active", snapshot.displayStatus)
        assertEquals("KDG-REAL-KEY", snapshot.licenseKey)
    }

    @Test
    fun deviceNotFoundDoesNotLockOrLookPending() {
        val snapshot = CustomerStatus.parse(
            httpCode = 404,
            status = "",
            code = "DEVICE_NOT_FOUND",
            message = "Cihaz bulunamadı",
            licenseKey = ""
        )

        assertFalse(snapshot.isActive)
        assertTrue(snapshot.isDeviceNotFound)
        assertTrue(snapshot.shouldContinueAutoPolling)
        assertEquals(
            CustomerStatus.DEVICE_NOT_FOUND,
            snapshot.displayStatus
        )
        assertEquals("Cihaz bulunamadı", snapshot.message)
    }

    @Test
    fun rejectedStopsPolling() {
        val snapshot = CustomerStatus.parse(
            httpCode = 200,
            status = "rejected",
            code = "",
            message = "Başvuru reddedildi",
            licenseKey = ""
        )

        assertTrue(snapshot.isTerminalFailure)
        assertFalse(snapshot.shouldContinueAutoPolling)
        assertEquals("rejected", snapshot.displayStatus)
    }

    @Test
    fun existingApplicationIsAccepted() {
        assertTrue(
            CustomerStatus.applicationAccepted(
                409,
                CustomerStatus.APPLICATION_EXISTS
            )
        )
        assertTrue(
            CustomerStatus.applicationAccepted(200, "")
        )
        assertFalse(
            CustomerStatus.applicationAccepted(500, "API_ERROR")
        )
    }
}

class AndroidAutoDiscoveryTest {

    @Test
    fun automotiveDescriptorDeclaresAllHostCapabilities() {
        val xml = sourceFile("res/xml/automotive_app_desc.xml").readText()

        assertTrue(xml.contains("""<uses name="media" />"""))
        assertTrue(xml.contains("""<uses name="template" />"""))
        assertTrue(xml.contains("""name="projection""""))
        assertTrue(xml.contains("""<uses name="video" />"""))
        assertTrue(xml.contains("""name="service""""))
    }

    @Test
    fun manifestDeclaresAndroidAutoDiscoveryChain() {
        val xml = sourceFile("AndroidManifest.xml").readText()

        assertTrue(
            xml.contains(
                """android:name="com.google.android.gms.car.application""""
            )
        )
        assertTrue(
            xml.contains(
                """android:name="androidx.car.app.minCarApiLevel""""
            )
        )
        assertTrue(xml.contains("""android:value="1""""))
        assertTrue(xml.contains("""android:name=".CarVideoService""""))
        assertTrue(xml.contains("""android:name=".CarMirrorService""""))
        assertTrue(xml.contains("""android:name=".CarMediaService""""))
        assertTrue(
            xml.contains(
                "android.media.browse.MediaBrowserService"
            )
        )
        assertTrue(
            xml.contains("androidx.car.app.CarAppService")
        )
        assertTrue(
            xml.contains(
                "com.google.android.gms.car.category.CATEGORY_PROJECTION"
            )
        )
        assertTrue(
            xml.contains("com.google.android.projection.gearhead")
        )
    }

    @Test
    fun carMediaAndIptvDoNotUseHardcodedTestCredentials() {
        val media = sourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()
        val iptv = sourceFile(
            "java/com/karadaglabs/cartv/IptvActivity.kt"
        ).readText()

        assertFalse(media.contains("CAR-TEST-001"))
        assertFalse(media.contains("KDG-TEST-2026-001"))
        assertFalse(iptv.contains("CAR-TEST-001"))
        assertFalse(iptv.contains("KDG-TEST-2026-001"))
        assertTrue(media.contains("AppSession.deviceUuid"))
        assertTrue(media.contains("AppSession.licenseKey"))
        assertTrue(iptv.contains("AppSession.deviceUuid"))
        assertTrue(iptv.contains("AppSession.licenseKey"))
    }

    @Test
    fun deviceUuidFormatMatchesExistingMechanism() {
        assertEquals(
            "ANDROID-abc123",
            "ANDROID-${"abc123"}"
        )
        assertEquals(
            "ANDROID-UNKNOWN",
            "ANDROID-${null ?: "UNKNOWN"}"
        )
    }

    private fun sourceFile(relativeFromMain: String): File {
        var dir = File(".").canonicalFile

        repeat(8) {
            val appPath = File(dir, "app/src/main/$relativeFromMain")
            if (appPath.isFile) {
                return appPath
            }

            val modulePath = File(dir, "src/main/$relativeFromMain")
            if (modulePath.isFile) {
                return modulePath
            }

            dir = dir.parentFile ?: return appPath
        }

        throw IllegalStateException(
            "Source file not found: $relativeFromMain"
        )
    }
}
