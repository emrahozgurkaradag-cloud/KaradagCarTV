package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RegistrationFormStateTest {

    @Test
    fun firstOpenIsIdleAndEditable() {
        val state = RegistrationFormState()

        assertFalse(state.applicationSent)
        assertFalse(state.loading)
        assertFalse(state.autoPolling)
        assertTrue(state.fieldsEnabled)
        assertFalse(state.fieldsReadOnly)
        assertTrue(state.applyButtonVisible)
        assertTrue(state.applyButtonEnabled)
        assertFalse(state.showProgressIndicator)
        assertEquals("", state.status)
        assertEquals("", state.statusCode)
        assertEquals("", state.lastError)
    }

    @Test
    fun statusCheckBeforeApplyDoesNotStartLoadingOrPolling() {
        val filled = RegistrationFormState()
            .updateName("Özgür Karadağ")
            .updateEmail("ozgur@example.com")

        val attempt = filled.beginStatusCheck()

        assertTrue(attempt.start is RegistrationStatusStart.Ignored)
        assertFalse(attempt.state.loading)
        assertFalse(attempt.state.applicationSent)
        assertFalse(attempt.state.autoPolling)
        assertTrue(attempt.state.fieldsEnabled)
        assertTrue(attempt.state.applyButtonVisible)
        assertTrue(attempt.state.applyButtonEnabled)
    }

    @Test
    fun applyButtonStaysActiveUntilUserSubmits() {
        val state = RegistrationFormState()
            .updateName("A")
            .updateEmail("not-an-email")

        assertTrue(state.applyButtonEnabled)
        assertFalse(state.loading)

        val invalidName = state.beginApply()
        assertTrue(invalidName.start is RegistrationApplyStart.Invalid)
        assertFalse(invalidName.state.loading)
        assertTrue(invalidName.state.applyButtonEnabled)

        val invalidEmail = RegistrationFormState()
            .updateName("Özgür Karadağ")
            .updateEmail("invalid")
            .beginApply()

        assertTrue(invalidEmail.start is RegistrationApplyStart.Invalid)
        assertFalse(invalidEmail.state.loading)
        assertTrue(invalidEmail.state.fieldsEnabled)
    }

    @Test
    fun successfulApplyStartsPollingWithoutActivatingLicense() {
        val submitting = readyApply()

        assertTrue(submitting.start is RegistrationApplyStart.Ready)
        assertTrue(submitting.state.loading)
        assertFalse(submitting.state.applicationSent)
        assertFalse(submitting.state.autoPolling)
        assertFalse(submitting.state.fieldsEnabled)

        val pending = CustomerStatus.parse(
            httpCode = 200,
            status = "pending",
            code = "",
            message = "Başvurunuz alındı.",
            licenseKey = ""
        )

        val outcome = submitting.state.onApplyAccepted(pending)

        assertTrue(outcome.state.applicationSent)
        assertFalse(outcome.state.loading)
        assertTrue(outcome.state.autoPolling)
        assertFalse(outcome.shouldActivateLicense)
        assertFalse(outcome.state.applyButtonVisible)
        assertTrue(pending.shouldContinueAutoPolling)
    }

    @Test
    fun activeLicenseAfterApplyStopsPollingAndExposesSessionKeys() {
        val submitting = readyApply()

        val active = CustomerStatus.parse(
            httpCode = 200,
            status = "active",
            code = "",
            message = "Lisans aktif",
            licenseKey = "KDG-REAL-KEY"
        )

        val outcome = submitting.state.onApplyAccepted(active)

        assertTrue(outcome.state.applicationSent)
        assertFalse(outcome.state.loading)
        assertFalse(outcome.state.autoPolling)
        assertTrue(outcome.shouldActivateLicense)
        assertEquals("ozgur@example.com", outcome.email)
        assertEquals("KDG-REAL-KEY", outcome.licenseKey)
    }

    @Test
    fun deviceNotFoundReturnsEditableIdleForm() {
        val waiting = readyApply().state.onApplyAccepted(
            CustomerStatus.parse(
                httpCode = 200,
                status = "pending",
                code = "",
                message = "Bekleniyor",
                licenseKey = ""
            )
        ).state

        assertTrue(waiting.applicationSent)
        assertTrue(waiting.autoPolling)

        val checking = waiting.beginStatusCheck()
        assertTrue(checking.start is RegistrationStatusStart.Ready)
        assertTrue(checking.state.loading)

        val outcome = checking.state.onStatusSnapshot(
            CustomerStatus.parse(
                httpCode = 404,
                status = "",
                code = "DEVICE_NOT_FOUND",
                message = "Cihaz bulunamadı",
                licenseKey = ""
            )
        )

        val recovered = outcome.state
        assertFalse(recovered.loading)
        assertFalse(recovered.applicationSent)
        assertFalse(recovered.autoPolling)
        assertTrue(recovered.fieldsEnabled)
        assertTrue(recovered.applyButtonVisible)
        assertTrue(recovered.applyButtonEnabled)
        assertEquals(
            CustomerStatus.DEVICE_NOT_FOUND,
            recovered.statusCode
        )
        assertTrue(recovered.lastError.contains("Cihaz UUID"))
    }

    @Test
    fun apiErrorOnApplyKeepsFormEditable() {
        val recovered = readyApply().state.onApplyError(
            "timeout"
        )

        assertFalse(recovered.loading)
        assertFalse(recovered.applicationSent)
        assertFalse(recovered.autoPolling)
        assertTrue(recovered.fieldsEnabled)
        assertTrue(recovered.applyButtonEnabled)
        assertEquals("timeout", recovered.lastError)

        val retry = recovered.beginApply()
        assertTrue(retry.start is RegistrationApplyStart.Ready)
        assertTrue(retry.state.loading)
    }

    @Test
    fun apiErrorOnStatusCheckAllowsNewApplication() {
        val waiting = readyApply().state.onApplyAccepted(
            CustomerStatus.parse(
                httpCode = 200,
                status = "pending",
                code = "",
                message = "Bekleniyor",
                licenseKey = ""
            )
        ).state.beginStatusCheck().state

        val recovered = waiting.onStatusError("connection reset")

        assertFalse(recovered.loading)
        assertFalse(recovered.applicationSent)
        assertFalse(recovered.autoPolling)
        assertTrue(recovered.applyButtonVisible)
        assertTrue(recovered.fieldsEnabled)
        assertEquals("connection reset", recovered.lastError)

        val retry = recovered.beginApply()
        assertTrue(retry.start is RegistrationApplyStart.Ready)
    }

    @Test
    fun rejectedApplyDoesNotStartPolling() {
        val recovered = readyApply().state.onApplyRejected(
            500,
            CustomerStatus.parse(
                httpCode = 500,
                status = "",
                code = "API_ERROR",
                message = "Sunucu hatası",
                licenseKey = ""
            )
        )

        assertFalse(recovered.loading)
        assertFalse(recovered.applicationSent)
        assertFalse(recovered.autoPolling)
        assertTrue(recovered.fieldsEnabled)
        assertTrue(recovered.applyButtonEnabled)
        assertTrue(recovered.lastError.contains("HTTP 500"))
    }

    @Test
    fun pendingStatusKeepsPollingUntilTerminal() {
        val waiting = readyApply().state.onApplyAccepted(
            CustomerStatus.parse(
                httpCode = 200,
                status = "pending",
                code = "",
                message = "Bekleniyor",
                licenseKey = ""
            )
        ).state.beginStatusCheck().state

        val stillPending = waiting.onStatusSnapshot(
            CustomerStatus.parse(
                httpCode = 200,
                status = "pending",
                code = "",
                message = "Hâlâ bekleniyor",
                licenseKey = ""
            )
        ).state

        assertTrue(stillPending.applicationSent)
        assertFalse(stillPending.loading)
        assertTrue(stillPending.autoPolling)

        val rejected = stillPending.beginStatusCheck().state.onStatusSnapshot(
            CustomerStatus.parse(
                httpCode = 200,
                status = "rejected",
                code = "",
                message = "Reddedildi",
                licenseKey = ""
            )
        ).state

        assertTrue(rejected.applicationSent)
        assertFalse(rejected.loading)
        assertFalse(rejected.autoPolling)
        assertTrue(rejected.showRetryApplyButton)

        val retry = rejected.retryApply()
        assertFalse(retry.applicationSent)
        assertFalse(retry.loading)
        assertTrue(retry.fieldsEnabled)
        assertTrue(retry.applyButtonEnabled)
    }

    @Test
    fun registrationScreenDoesNotPollOnFirstComposition() {
        val source = sourceFile(
            "java/com/karadaglabs/cartv/RegistrationActivity.kt"
        ).readText()

        assertTrue(
            source.contains("mutableStateOf(\n                RegistrationFormState()") ||
                source.contains("RegistrationFormState()")
        )
        assertFalse(source.contains("LaunchedEffect(Unit)"))
        assertTrue(source.contains("state.autoPolling"))
        assertTrue(source.contains("state.applicationSent"))
        assertTrue(source.contains("beginStatusCheck()"))
        assertTrue(source.contains("enabled = state.fieldsEnabled"))
        assertTrue(source.contains("readOnly = state.fieldsReadOnly"))
        assertTrue(source.contains("enabled = state.applyButtonEnabled"))
        assertTrue(source.contains("\"Başvuru Yap\""))
        assertFalse(
            source.contains("CircularProgressIndicator(\n                            modifier")
        )
    }

    private fun readyApply(): RegistrationApplyAttempt {
        return RegistrationFormState()
            .updateName("Özgür Karadağ")
            .updateEmail("ozgur@example.com")
            .beginApply()
    }

    private fun sourceFile(relativeFromMain: String): File {
        var dir = File(".").canonicalFile

        repeat(8) {
            val appPath = File(dir, "app/src/main/$relativeFromMain")
            if (appPath.isFile) {
                return appPath
            }

            dir = dir.parentFile ?: return appPath
        }

        throw IllegalStateException(
            "Source file not found: $relativeFromMain"
        )
    }
}
