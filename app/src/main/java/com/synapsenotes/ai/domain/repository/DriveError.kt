package com.synapsenotes.ai.domain.repository

sealed class DriveError : Throwable() {
    object NotSignedIn : DriveError()
    object AccountMissing : DriveError()
    data class ServiceDisabled(val details: String) : DriveError()
    data class QuotaExceeded(val details: String) : DriveError()
    data class NetworkError(val details: String) : DriveError()
    data class UnknownError(val details: String, val originalException: Throwable) : DriveError()
}
