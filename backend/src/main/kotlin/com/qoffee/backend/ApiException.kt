package com.qoffee.backend

import io.ktor.http.HttpStatusCode

enum class ApiErrorCode {
    BAD_REQUEST,
    INVALID_EMAIL,
    WEAK_PASSWORD,
    EMAIL_ALREADY_REGISTERED,
    INVALID_CREDENTIALS,
    AUTH_EXPIRED,
    INVALID_AUTH_STATE,
    INVALID_DEVICE,
    TOO_MANY_SYNC_ITEMS,
    UNSUPPORTED_SYNC_OPERATION,
    UNSUPPORTED_SYNC_TABLE,
    CONFLICT_NOT_FOUND,
    SNAPSHOT_NOT_FOUND,
    INTERNAL_ERROR,
}

class ApiException(
    val status: HttpStatusCode,
    val code: ApiErrorCode,
    override val message: String,
) : RuntimeException(message) {
    constructor(status: HttpStatusCode, message: String) : this(status, ApiErrorCode.BAD_REQUEST, message)
}
