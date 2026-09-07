package com.karadaglabs.cartv

/**
 * RegistrationActivity ekran durumunu Compose dışında tutar.
 *
 * İlk açılışta form boş, başvuru gönderilmemiş, polling kapalı ve
 * loading false olmalıdır. /api/customer/status yalnızca kullanıcı
 * başvuruyu gönderdikten sonra çağrılabilir.
 */
data class RegistrationUiState(
    val name: String = "",
    val email: String = "",
    val status: String = "",
    val statusCode: String = "",
    val loading: Boolean = false,
    val applicationSent: Boolean = false,
    val lastError: String = "",
    val autoPolling: Boolean = false,
    val licenseKey: String = "",
    val shouldOpenLicensedSession: Boolean = false
) {

    val fieldsEditable: Boolean
        get() = !applicationSent

    val showApplyButton: Boolean
        get() = !applicationSent

    val applyButtonEnabled: Boolean
        get() = !loading &&
            name.trim().length >= 2 &&
            email.contains("@")

    val showRecheckButton: Boolean
        get() = applicationSent

    val showRetryButton: Boolean
        get() = applicationSent &&
            (
                statusCode == CustomerStatus.DEVICE_NOT_FOUND ||
                    statusCode == CustomerStatus.REJECTED ||
                    statusCode == CustomerStatus.CANCELLED ||
                    lastError.isNotBlank()
                )

    /**
     * Kullanıcı "Başvuru Yap"a basmadan status API'si çağrılmaz.
     */
    val mayCallStatusApi: Boolean
        get() = applicationSent

    fun withName(value: String): RegistrationUiState {
        if (!fieldsEditable) {
            return this
        }

        return copy(name = value)
    }

    fun withEmail(value: String): RegistrationUiState {
        if (!fieldsEditable) {
            return this
        }

        return copy(email = value)
    }

    /**
     * Apply isteği başlamadan loading açılır.
     * Status polling bu adımda başlamaz.
     */
    fun beginApply(): RegistrationUiState {
        if (!applyButtonEnabled) {
            return this
        }

        return copy(
            loading = true,
            lastError = "",
            autoPolling = false,
            shouldOpenLicensedSession = false
        )
    }

    fun completeApply(
        httpCode: Int,
        snapshot: CustomerStatus.Snapshot,
        accepted: Boolean
    ): RegistrationUiState {

        val cleanEmail =
            email.trim().lowercase()

        if (!accepted) {
            return copy(
                email = cleanEmail.ifBlank { email },
                status = snapshot.message,
                statusCode = snapshot.displayStatus,
                loading = false,
                applicationSent = false,
                autoPolling = false,
                lastError = snapshot.message.ifBlank {
                    "Sunucu HTTP $httpCode döndürdü."
                },
                licenseKey = "",
                shouldOpenLicensedSession = false
            )
        }

        val displayStatus =
            snapshot.displayStatus.ifBlank {
                CustomerStatus.PENDING
            }

        val message =
            snapshot.message.ifBlank {
                "Başvurunuz alındı. Yönetici onayı bekleniyor."
            }

        val deviceMissing =
            snapshot.isDeviceNotFound

        return copy(
            email = cleanEmail,
            status = message,
            statusCode = displayStatus,
            loading = false,
            applicationSent = true,
            autoPolling = snapshot.shouldContinueAutoPolling,
            lastError = if (deviceMissing) {
                DEVICE_NOT_FOUND_ERROR
            } else {
                ""
            },
            licenseKey = snapshot.licenseKey,
            shouldOpenLicensedSession = snapshot.isActive
        )
    }

    fun failApply(message: String): RegistrationUiState {
        return copy(
            loading = false,
            autoPolling = false,
            shouldOpenLicensedSession = false,
            lastError = message.ifBlank {
                "Başvuru gönderilemedi."
            },
            status = "Başvuru gönderilemedi. İnternet bağlantınızı kontrol edin."
        )
    }

    /**
     * Status isteği yalnızca başvuru gönderildikten sonra yapılır.
     * İlk açılışta veya formdayken loading açılmaz.
     */
    fun beginStatusCheck(): RegistrationUiState {
        if (!mayCallStatusApi) {
            return this
        }

        val cleanEmail =
            email.trim().lowercase()

        if (cleanEmail.isBlank()) {
            return copy(
                status = "E-posta adresinizi girin.",
                loading = false
            )
        }

        return copy(
            loading = true,
            lastError = ""
        )
    }

    fun completeStatus(
        snapshot: CustomerStatus.Snapshot
    ): RegistrationUiState {

        val stopPolling =
            snapshot.isActive ||
                snapshot.isTerminalFailure ||
                snapshot.isDeviceNotFound

        return copy(
            statusCode = snapshot.displayStatus,
            status = snapshot.message,
            loading = false,
            autoPolling = if (stopPolling) {
                false
            } else {
                autoPolling
            },
            lastError = if (snapshot.isDeviceNotFound) {
                DEVICE_NOT_FOUND_ERROR
            } else {
                ""
            },
            licenseKey = snapshot.licenseKey,
            shouldOpenLicensedSession = snapshot.isActive
        )
    }

    fun failStatus(message: String): RegistrationUiState {
        return copy(
            loading = false,
            lastError = message.ifBlank {
                "Bilinmeyen bağlantı hatası."
            },
            status = "Başvuru durumu kontrol edilemedi."
        )
    }

    fun retryApply(): RegistrationUiState {
        return copy(
            autoPolling = false,
            applicationSent = false,
            statusCode = "",
            lastError = "",
            status = "",
            loading = false,
            licenseKey = "",
            shouldOpenLicensedSession = false
        )
    }

    companion object {

        const val DEVICE_NOT_FOUND_ERROR =
            "Cihaz UUID'si henüz bulunamadı. " +
                "Durumu tekrar kontrol edin veya " +
                "yeniden başvuru yapın."

        fun initial(): RegistrationUiState =
            RegistrationUiState()
    }
}
