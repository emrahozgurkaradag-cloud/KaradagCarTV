package com.karadaglabs.cartv

/**
 * Registration ekranının saf durum makinesi.
 *
 * Compose / Activity yan etkilerinden bağımsız olduğu için
 * ilk açılış, hata ve polling kuralları birim testiyle
 * kilitlenebilir.
 *
 * Başvuru gönderilmeden loading veya auto-polling açılamaz.
 */
data class RegistrationFormState(
    val name: String = "",
    val email: String = "",
    val status: String = "",
    val statusCode: String = "",
    val loading: Boolean = false,
    val applicationSent: Boolean = false,
    val lastError: String = "",
    val autoPolling: Boolean = false
) {

    val fieldsEnabled: Boolean
        get() = !applicationSent && !loading

    val fieldsReadOnly: Boolean
        get() = !fieldsEnabled

    val applyButtonVisible: Boolean
        get() = !applicationSent

    val applyButtonEnabled: Boolean
        get() = applyButtonVisible && !loading

    val showProgressIndicator: Boolean
        get() = loading

    val showRetryApplyButton: Boolean
        get() = applicationSent &&
            (
                statusCode == CustomerStatus.DEVICE_NOT_FOUND ||
                    statusCode == CustomerStatus.REJECTED ||
                    statusCode == CustomerStatus.CANCELLED
                )

    fun updateName(value: String): RegistrationFormState {
        if (!fieldsEnabled) {
            return this
        }

        return copy(name = value)
    }

    fun updateEmail(value: String): RegistrationFormState {
        if (!fieldsEnabled) {
            return this
        }

        return copy(email = value)
    }

    fun beginApply(): RegistrationApplyAttempt {
        if (applicationSent || loading) {
            return RegistrationApplyAttempt(
                state = this,
                start = RegistrationApplyStart.Ignored
            )
        }

        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()

        if (cleanName.length < 2) {
            return RegistrationApplyAttempt(
                state = copy(
                    status = "Ad Soyad en az 2 karakter olmalı.",
                    lastError = ""
                ),
                start = RegistrationApplyStart.Invalid
            )
        }

        if (!cleanEmail.contains("@")) {
            return RegistrationApplyAttempt(
                state = copy(
                    status = "Geçerli bir e-posta adresi girin.",
                    lastError = ""
                ),
                start = RegistrationApplyStart.Invalid
            )
        }

        return RegistrationApplyAttempt(
            state = copy(
                name = cleanName,
                email = cleanEmail,
                loading = true,
                lastError = "",
                autoPolling = false
            ),
            start = RegistrationApplyStart.Ready(
                name = cleanName,
                email = cleanEmail
            )
        )
    }

    fun beginStatusCheck(): RegistrationStatusAttempt {
        if (!applicationSent) {
            return RegistrationStatusAttempt(
                state = this,
                start = RegistrationStatusStart.Ignored
            )
        }

        if (loading) {
            return RegistrationStatusAttempt(
                state = this,
                start = RegistrationStatusStart.Ignored
            )
        }

        val cleanEmail = email.trim().lowercase()

        if (cleanEmail.isBlank()) {
            return RegistrationStatusAttempt(
                state = copy(
                    status = "E-posta adresinizi girin.",
                    autoPolling = false,
                    loading = false
                ),
                start = RegistrationStatusStart.Invalid
            )
        }

        return RegistrationStatusAttempt(
            state = copy(
                loading = true,
                lastError = ""
            ),
            start = RegistrationStatusStart.Ready(
                email = cleanEmail
            )
        )
    }

    fun onApplyAccepted(
        snapshot: CustomerStatus.Snapshot
    ): RegistrationStatusOutcome {
        val message = snapshot.message.ifBlank {
            "Başvurunuz alındı. Yönetici onayı bekleniyor."
        }

        val accepted = copy(
            status = message,
            statusCode = snapshot.displayStatus.ifBlank {
                CustomerStatus.PENDING
            },
            loading = false,
            applicationSent = true,
            lastError = "",
            autoPolling = snapshot.shouldContinueAutoPolling
        )

        return if (snapshot.isActive) {
            RegistrationStatusOutcome(
                state = accepted.copy(autoPolling = false),
                email = email,
                licenseKey = snapshot.licenseKey
            )
        } else {
            RegistrationStatusOutcome(
                state = accepted
            )
        }
    }

    fun onApplyRejected(
        httpCode: Int,
        snapshot: CustomerStatus.Snapshot
    ): RegistrationFormState {
        return copy(
            loading = false,
            applicationSent = false,
            autoPolling = false,
            status = snapshot.message,
            statusCode = snapshot.displayStatus,
            lastError = "Sunucu HTTP $httpCode döndürdü."
        )
    }

    fun onApplyError(
        error: String
    ): RegistrationFormState {
        return restoreEditableForm(
            status = "Başvuru gönderilemedi. İnternet bağlantınızı kontrol edin.",
            lastError = error.ifBlank {
                "Başvuru gönderilemedi."
            }
        )
    }

    fun onStatusSnapshot(
        snapshot: CustomerStatus.Snapshot
    ): RegistrationStatusOutcome {
        val next = copy(
            status = snapshot.message,
            statusCode = snapshot.displayStatus,
            loading = false
        )

        if (snapshot.isActive) {
            return RegistrationStatusOutcome(
                state = next.copy(autoPolling = false),
                email = email,
                licenseKey = snapshot.licenseKey
            )
        }

        if (snapshot.isDeviceNotFound) {
            return RegistrationStatusOutcome(
                state = restoreEditableForm(
                    status = snapshot.message,
                    statusCode = CustomerStatus.DEVICE_NOT_FOUND,
                    lastError = "Cihaz UUID'si henüz bulunamadı. " +
                        "Durumu tekrar kontrol edin veya " +
                        "yeniden başvuru yapın."
                )
            )
        }

        if (snapshot.isTerminalFailure) {
            return RegistrationStatusOutcome(
                state = next.copy(autoPolling = false)
            )
        }

        return RegistrationStatusOutcome(
            state = next.copy(
                autoPolling = applicationSent &&
                    snapshot.shouldContinueAutoPolling
            )
        )
    }

    fun onStatusError(
        error: String
    ): RegistrationFormState {
        return restoreEditableForm(
            status = "Başvuru durumu kontrol edilemedi.",
            lastError = error.ifBlank {
                "Bilinmeyen bağlantı hatası."
            }
        )
    }

    fun retryApply(): RegistrationFormState {
        return restoreEditableForm()
    }

    private fun restoreEditableForm(
        status: String = this.status,
        statusCode: String = "",
        lastError: String = ""
    ): RegistrationFormState {
        return copy(
            loading = false,
            applicationSent = false,
            autoPolling = false,
            status = status,
            statusCode = statusCode,
            lastError = lastError
        )
    }
}

sealed class RegistrationApplyStart {
    data object Ignored : RegistrationApplyStart()
    data object Invalid : RegistrationApplyStart()
    data class Ready(
        val name: String,
        val email: String
    ) : RegistrationApplyStart()
}

data class RegistrationApplyAttempt(
    val state: RegistrationFormState,
    val start: RegistrationApplyStart
)

sealed class RegistrationStatusStart {
    data object Ignored : RegistrationStatusStart()
    data object Invalid : RegistrationStatusStart()
    data class Ready(
        val email: String
    ) : RegistrationStatusStart()
}

data class RegistrationStatusAttempt(
    val state: RegistrationFormState,
    val start: RegistrationStatusStart
)

data class RegistrationStatusOutcome(
    val state: RegistrationFormState,
    val email: String? = null,
    val licenseKey: String? = null
) {

    val shouldActivateLicense: Boolean
        get() = !email.isNullOrBlank() &&
            !licenseKey.isNullOrBlank()
}
