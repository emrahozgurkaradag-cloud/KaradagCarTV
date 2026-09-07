package com.karadaglabs.cartv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RegistrationUiStateTest {

    @Test
    fun initialScreenIsIdleAndShowsApplyButton() {
        val ui = RegistrationUiState.initial()

        assertFalse(ui.applicationSent)
        assertFalse(ui.loading)
        assertFalse(ui.autoPolling)
        assertFalse(ui.mayCallStatusApi)
        assertTrue(ui.fieldsEditable)
        assertTrue(ui.showApplyButton)
        assertFalse(ui.applyButtonEnabled)
        assertFalse(ui.showRetryButton)
        assertFalse(ui.shouldOpenLicensedSession)
        assertEquals("", ui.name)
        assertEquals("", ui.email)
    }

    @Test
    fun beginApplyOnEmptyFormDoesNothing() {
        val initial = RegistrationUiState.initial()

        assertEquals(initial, initial.beginApply())
        assertFalse(initial.beginApply().loading)
    }

    @Test
    fun submittedFormFieldsAreLockedUntilRetry() {
        val pending = filledForm()
            .beginApply()
            .completeApply(
                200,
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.PENDING,
                    code = "",
                    message = "Bekleniyor",
                    licenseKey = ""
                ),
                accepted = true
            )

        val renamed = pending.withName("Hacker")
        val reemailed = pending.withEmail("other@example.com")

        assertEquals(pending.name, renamed.name)
        assertEquals(pending.email, reemailed.email)
        assertFalse(pending.fieldsEditable)
    }

    @Test
    fun filledFormEnablesApplyWithoutCallingStatus() {
        val ui = RegistrationUiState.initial()
            .withName("Özgür Karadağ")
            .withEmail("ozgur@example.com")

        assertTrue(ui.fieldsEditable)
        assertTrue(ui.showApplyButton)
        assertTrue(ui.applyButtonEnabled)
        assertFalse(ui.loading)
        assertFalse(ui.autoPolling)
        assertFalse(ui.mayCallStatusApi)
    }

    @Test
    fun beginStatusCheckOnFreshScreenDoesNotLoadOrPoll() {
        val initial = RegistrationUiState.initial()
            .withName("Özgür Karadağ")
            .withEmail("ozgur@example.com")

        val next = initial.beginStatusCheck()

        assertEquals(initial, next)
        assertFalse(next.loading)
        assertFalse(next.autoPolling)
        assertFalse(next.applicationSent)
        assertFalse(next.mayCallStatusApi)
    }

    @Test
    fun beginApplySetsLoadingWithoutPolling() {
        val next = filledForm().beginApply()

        assertTrue(next.loading)
        assertFalse(next.autoPolling)
        assertFalse(next.applicationSent)
        assertTrue(next.showApplyButton)
        assertFalse(next.applyButtonEnabled)
        assertFalse(next.mayCallStatusApi)
    }

    @Test
    fun pendingApplyStartsPollingAndHidesForm() {
        val snapshot = CustomerStatus.parse(
            httpCode = 200,
            status = CustomerStatus.PENDING,
            code = "",
            message = "Başvurunuz alındı",
            licenseKey = ""
        )

        val next = filledForm()
            .beginApply()
            .completeApply(
                httpCode = 200,
                snapshot = snapshot,
                accepted = true
            )

        assertFalse(next.loading)
        assertTrue(next.applicationSent)
        assertTrue(next.autoPolling)
        assertTrue(next.mayCallStatusApi)
        assertFalse(next.showApplyButton)
        assertTrue(next.showRecheckButton)
        assertFalse(next.showRetryButton)
        assertFalse(next.shouldOpenLicensedSession)
        assertEquals("ozgur@example.com", next.email)
        assertEquals(CustomerStatus.PENDING, next.statusCode)
    }

    @Test
    fun existingApplicationIsTreatedAsAcceptedPending() {
        val snapshot = CustomerStatus.parse(
            httpCode = 409,
            status = CustomerStatus.PENDING,
            code = CustomerStatus.APPLICATION_EXISTS,
            message = "Başvuru zaten var",
            licenseKey = ""
        )

        val accepted = CustomerStatus.applicationAccepted(
            409,
            CustomerStatus.APPLICATION_EXISTS
        )

        val next = filledForm()
            .beginApply()
            .completeApply(409, snapshot, accepted)

        assertTrue(accepted)
        assertTrue(next.applicationSent)
        assertTrue(next.autoPolling)
        assertFalse(next.loading)
        assertFalse(next.shouldOpenLicensedSession)
    }

    @Test
    fun activeLicenseAfterApplyOpensSessionWithoutPolling() {
        val snapshot = CustomerStatus.parse(
            httpCode = 200,
            status = CustomerStatus.ACTIVE,
            code = "",
            message = "Lisans aktif",
            licenseKey = "KDG-REAL-KEY"
        )

        val next = filledForm()
            .beginApply()
            .completeApply(200, snapshot, accepted = true)

        assertFalse(next.loading)
        assertTrue(next.applicationSent)
        assertFalse(next.autoPolling)
        assertTrue(next.shouldOpenLicensedSession)
        assertEquals("KDG-REAL-KEY", next.licenseKey)
        assertTrue(snapshot.isActive)
    }

    @Test
    fun applyHttpErrorClearsLoadingAndKeepsForm() {
        val snapshot = CustomerStatus.parse(
            httpCode = 500,
            status = "",
            code = "API_ERROR",
            message = "Sunucu hatası",
            licenseKey = ""
        )

        val next = filledForm()
            .beginApply()
            .completeApply(
                httpCode = 500,
                snapshot = snapshot,
                accepted = CustomerStatus.applicationAccepted(500, "API_ERROR")
            )

        assertFalse(next.loading)
        assertFalse(next.applicationSent)
        assertFalse(next.autoPolling)
        assertTrue(next.showApplyButton)
        assertTrue(next.fieldsEditable)
        assertTrue(next.applyButtonEnabled)
        assertFalse(next.shouldOpenLicensedSession)
        assertTrue(next.lastError.isNotBlank())
    }

    @Test
    fun applyTimeoutOrExceptionClearsLoadingForRetry() {
        val next = filledForm()
            .beginApply()
            .failApply("timeout")

        assertFalse(next.loading)
        assertFalse(next.applicationSent)
        assertFalse(next.autoPolling)
        assertTrue(next.showApplyButton)
        assertTrue(next.fieldsEditable)
        assertEquals("timeout", next.lastError)
        assertEquals(
            "Başvuru gönderilemedi. İnternet bağlantınızı kontrol edin.",
            next.status
        )
    }

    @Test
    fun deviceNotFoundAfterStatusUnlocksRetry() {
        val pending = filledForm()
            .beginApply()
            .completeApply(
                200,
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.PENDING,
                    code = "",
                    message = "Bekleniyor",
                    licenseKey = ""
                ),
                accepted = true
            )

        val checking = pending.beginStatusCheck()
        assertTrue(checking.loading)
        assertTrue(checking.mayCallStatusApi)

        val notFound = checking.completeStatus(
            CustomerStatus.parse(
                httpCode = 404,
                status = "",
                code = CustomerStatus.DEVICE_NOT_FOUND,
                message = "Cihaz bulunamadı",
                licenseKey = ""
            )
        )

        assertFalse(notFound.loading)
        assertFalse(notFound.autoPolling)
        assertTrue(notFound.applicationSent)
        assertTrue(notFound.showRetryButton)
        assertFalse(notFound.shouldOpenLicensedSession)
        assertEquals(
            CustomerStatus.DEVICE_NOT_FOUND,
            notFound.statusCode
        )
        assertEquals(
            RegistrationUiState.DEVICE_NOT_FOUND_ERROR,
            notFound.lastError
        )

        val retried = notFound.retryApply()
        assertFalse(retried.applicationSent)
        assertFalse(retried.loading)
        assertFalse(retried.autoPolling)
        assertTrue(retried.showApplyButton)
        assertTrue(retried.fieldsEditable)
        assertTrue(retried.applyButtonEnabled)
        assertEquals("", retried.statusCode)
        assertEquals("", retried.lastError)
    }

    @Test
    fun statusApiErrorClearsLoadingAndAllowsRetryApply() {
        val pending = filledForm()
            .beginApply()
            .completeApply(
                200,
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.PENDING,
                    code = "",
                    message = "Bekleniyor",
                    licenseKey = ""
                ),
                accepted = true
            )
            .beginStatusCheck()
            .failStatus("failed to connect")

        assertFalse(pending.loading)
        assertTrue(pending.applicationSent)
        assertTrue(pending.showRetryButton)
        assertEquals("failed to connect", pending.lastError)
    }

    @Test
    fun rejectedStatusStopsPollingAndAllowsNewApplication() {
        val next = filledForm()
            .beginApply()
            .completeApply(
                200,
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.PENDING,
                    code = "",
                    message = "Bekleniyor",
                    licenseKey = ""
                ),
                accepted = true
            )
            .completeStatus(
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.REJECTED,
                    code = "",
                    message = "Başvuru reddedildi",
                    licenseKey = ""
                )
            )

        assertFalse(next.loading)
        assertFalse(next.autoPolling)
        assertTrue(next.showRetryButton)
        assertFalse(next.shouldOpenLicensedSession)
    }

    @Test
    fun activeStatusAfterPollingOpensLicensedSession() {
        val next = filledForm()
            .beginApply()
            .completeApply(
                200,
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.PENDING,
                    code = "",
                    message = "Bekleniyor",
                    licenseKey = ""
                ),
                accepted = true
            )
            .completeStatus(
                CustomerStatus.parse(
                    httpCode = 200,
                    status = CustomerStatus.ACTIVE,
                    code = "",
                    message = "Lisans aktif",
                    licenseKey = "KDG-REAL-KEY"
                )
            )

        assertFalse(next.loading)
        assertFalse(next.autoPolling)
        assertTrue(next.shouldOpenLicensedSession)
        assertEquals("KDG-REAL-KEY", next.licenseKey)
    }

    private fun filledForm(): RegistrationUiState {
        return RegistrationUiState.initial()
            .withName("Özgür Karadağ")
            .withEmail("ozgur@example.com")
    }
}

class RegistrationActivitySourceTest {

    @Test
    fun landscapeFormUsesVerticalScrollSoApplyButtonStaysReachable() {
        val source = registrationSource()

        assertTrue(source.contains("verticalScroll"))
        assertTrue(source.contains("rememberScrollState"))
        assertTrue(source.contains("Başvuru Yap"))
        assertTrue(source.contains("RegistrationUiState.initial()"))
    }

    @Test
    fun statusApiIsNotCalledUntilApplySucceeds() {
        val source = registrationSource()
        val onCreate = onCreateBlock(source)

        assertFalse(
            "onCreate must not call /api/customer/status",
            onCreate.contains("/api/customer/status")
        )
        assertTrue(source.contains("beginStatusCheck()"))
        assertTrue(source.contains("mayCallStatusApi"))
        assertTrue(source.contains("/api/customer/apply"))
        assertTrue(source.contains("/api/customer/status"))
    }

    @Test
    fun activeLicenseStillUsesExistingAppSessionFlow() {
        val source = registrationSource()
        val onCreate = onCreateBlock(source)

        assertTrue(onCreate.contains("AppSession.email"))
        assertTrue(onCreate.contains("AppSession.licenseKey"))
        assertTrue(onCreate.contains("requestMediaProjection()"))
        assertTrue(source.contains("AppSession.saveLicense"))
        assertTrue(source.contains("deviceUuid"))
        assertFalse(source.contains("CAR-TEST-001"))
        assertFalse(source.contains("KDG-TEST-2026-001"))
    }

    @Test
    fun applyAndStatusFailuresAlwaysGoThroughLoadingClearingReducers() {
        val source = registrationSource()

        assertTrue(source.contains("failApply("))
        assertTrue(source.contains("failStatus("))
        assertTrue(source.contains("completeApply("))
        assertTrue(source.contains("completeStatus("))
        assertTrue(source.contains("retryApply()"))
        assertTrue(source.contains("Yeniden Başvur"))
    }

    private fun registrationSource(): String {
        return sourceFile(
            "java/com/karadaglabs/cartv/RegistrationActivity.kt"
        ).readText()
    }

    private fun onCreateBlock(source: String): String {
        val start = source.indexOf("override fun onCreate")
        val end = source.indexOf("private fun requestMediaProjection")

        assertTrue(start >= 0)
        assertTrue(end > start)

        return source.substring(start, end)
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
