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
        assertFalse(snapshot.shouldContinueAutoPolling)
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
    fun automotiveDescriptorMatchesFermataAuto() {
        val xml = mainSourceFile("res/xml/automotive_app_desc.xml").readText()
        assertTrue(xml.lineSequence().any { it.trim() == """<uses name="media" />""" })
        assertTrue(xml.contains("""name="service""""))
        assertTrue(xml.contains("""name="projection""""))
        assertTrue(xml.contains("""<!-- <uses name="template" /> -->"""))
        assertFalse(xml.lineSequence().any { it.trim() == """<uses name="video" />""" })
        assertFalse(xml.lineSequence().any { it.trim() == """<uses name="template" />""" })
    }

    @Test
    fun manifestDeclaresFermataStyleProjectionAndMedia() {
        val manifest = mainSourceFile("AndroidManifest.xml").readText()
        val video = serviceBlock(manifest, ".CarVideoService")
        val media = serviceBlock(manifest, ".CarMediaService")
        val phone = activityBlock(manifest, ".PhoneActivity")

        assertTrue(manifest.contains("""android:name="com.google.android.gms.car.application""""))
        assertTrue(manifest.contains("""android:name="com.google.android.gms.car.application.theme""""))
        assertTrue(manifest.contains("""android:name="com.google.android.gms.car.notification.SmallIcon""""))
        assertTrue(manifest.contains("android.hardware.type.automotive"))
        assertTrue(manifest.contains("com.google.android.projection.gearhead"))
        assertTrue(manifest.contains("""android:label="@string/app_name""""))

        assertTrue(video.contains("""android:exported="true""""))
        assertTrue(video.contains("android.intent.action.MAIN"))
        assertTrue(video.contains("com.google.android.gms.car.category.CATEGORY_PROJECTION"))
        assertTrue(video.contains("com.google.android.gms.car.category.CATEGORY_PROJECTION_OEM"))
        assertTrue(video.contains("""android:label="@string/app_name""""))
        assertFalse(video.contains("android:permission"))

        assertTrue(media.contains("""android:exported="true""""))
        assertTrue(media.contains("""android:enabled="true""""))
        assertTrue(media.contains("""android:label="@string/media_service_name""""))
        assertTrue(media.contains("""android:icon="@drawable/ic_media_service""""))
        assertTrue(media.contains("android.media.browse.MediaBrowserService"))
        assertFalse(media.contains("CATEGORY_PROJECTION"))

        assertTrue(phone.contains("android.intent.category.LAUNCHER"))
        assertFalse(phone.contains("android.intent.category.CAR_LAUNCHER"))
        assertFalse(manifest.contains(".CarMirrorService"))
        assertFalse(manifest.contains("androidx.car.app.CarAppService"))
    }

    @Test
    fun carVideoServiceMatchesFermataCarServiceShape() {
        val service = mainSourceFile(
            "java/com/karadaglabs/cartv/CarVideoService.kt"
        ).readText()
        val activity = mainSourceFile(
            "java/com/karadaglabs/cartv/CarVideoActivity.kt"
        ).readText()

        assertTrue(service.contains("class CarVideoService : CarActivityService()"))
        assertTrue(service.contains("override fun getCarActivity()"))
        assertTrue(service.contains("return CarVideoActivity::class.java"))
        assertTrue(service.contains("super.onCreate()"))
        assertFalse(service.contains("override fun onBind"))
        assertFalse(service.contains("startCarActivity"))
        assertFalse(service.contains("Car.createCar"))

        assertTrue(activity.contains("class CarVideoActivity : CarActivity()"))
        assertTrue(activity.contains("CAR VIDEO ACTIVITY AÇILDI"))
        assertTrue(activity.contains("KARADAG CAR TV"))
        assertTrue(activity.contains("PROJECTION OK"))
        assertTrue(activity.contains("setContentView(text)"))
        assertTrue(activity.contains("setIgnoreConfigChanges(0xFFFFFFFF"))
        assertFalse(activity.contains("ExoPlayer"))
        assertFalse(activity.contains("WebView"))
        assertFalse(activity.contains("IptvRepository"))
        assertFalse(activity.contains("SurfaceView"))
    }

    @Test
    fun carMediaServiceIsRadioOnly() {
        val media = mainSourceFile(
            "java/com/karadaglabs/cartv/CarMediaService.kt"
        ).readText()
        assertTrue(media.contains("class CarMediaService : MediaLibraryService()"))
        assertTrue(media.contains("RadioStations.all"))
        assertTrue(media.contains("Karadag Radyo"))
        assertFalse(media.contains("LIVE_TV"))
        assertFalse(media.contains("YouTube"))
        assertFalse(media.contains("startCarActivity"))
        assertFalse(media.contains("CarProjectionRouter.openFrom"))
    }

    @Test
    fun routerDoesNotForceHost() {
        val router = mainSourceFile(
            "java/com/karadaglabs/cartv/CarProjectionRouter.kt"
        ).readText()
        assertFalse(router.contains("startCarActivity"))
        assertFalse(router.contains("CarFirstPartyManager"))
        assertFalse(router.contains("Car.createCar"))
        assertTrue(router.contains("CAR_VIDEO_ON_CREATE"))
        assertTrue(router.contains("CAR_VIDEO_SERVICE_GET_ACTIVITY"))
    }
}

private fun serviceBlock(manifest: String, className: String): String {
    return componentBlock(manifest, "service", className)
}

private fun activityBlock(manifest: String, className: String): String {
    return componentBlock(manifest, "activity", className)
}

private fun componentBlock(
    manifest: String,
    tag: String,
    className: String
): String {
    val nameAttr = """android:name="$className""""
    val nameIndex = manifest.indexOf(nameAttr)
    assertTrue("$className missing from manifest", nameIndex >= 0)

    val open = manifest.lastIndexOf("<$tag", nameIndex)
    assertTrue("$tag block for $className missing", open >= 0)

    val gt = manifest.indexOf('>', open)
    assertTrue("$tag $className has no opening '>'", gt > open)
    val isSelfClosing = manifest[gt - 1] == '/'
    if (isSelfClosing) {
        return manifest.substring(open, gt + 1)
    }

    val endTag = manifest.indexOf("</$tag>", nameIndex)
    assertTrue("$tag $className is not closed", endTag > gt)
    return manifest.substring(open, endTag + "</$tag>".length)
}

private fun mainSourceFile(relativeFromMain: String): File {
    var dir = File(".").canonicalFile

    repeat(8) {
        val appPath = File(dir, "app/src/main/$relativeFromMain")
        if (appPath.isFile) {
            return appPath
        }
        dir = dir.parentFile ?: return appPath
    }

    throw IllegalStateException("Source file not found: $relativeFromMain")
}
