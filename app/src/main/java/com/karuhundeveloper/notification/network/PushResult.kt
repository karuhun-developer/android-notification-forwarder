package com.karuhundeveloper.notification.network

/**
 * Represents the outcome of a [NotificationPusher.push] call.
 *
 * Callers should pattern-match on this sealed class rather than catching raw exceptions,
 * keeping all error-handling logic in the caller rather than scattered through the pusher.
 */
sealed class PushResult {

    /**
     * The server accepted the request and replied with a 2xx status code.
     *
     * @property statusCode The exact HTTP status code returned (e.g. 200, 201, 204).
     * @property responseBody The raw response body, if any (may be empty string).
     */
    data class Success(
        val statusCode: Int,
        val responseBody: String
    ) : PushResult()

    /**
     * The request completed but the server replied with a non-2xx status code.
     *
     * @property statusCode The HTTP status code (e.g. 400, 401, 500).
     * @property errorBody  The raw error body returned by the server.
     */
    data class HttpError(
        val statusCode: Int,
        val errorBody: String
    ) : PushResult()

    /**
     * A network-level or I/O failure occurred before a response was received
     * (e.g. no connectivity, DNS failure, timeout, SSL handshake error).
     *
     * @property cause The underlying [Exception] for logging / crash reporting.
     */
    data class NetworkError(val cause: Exception) : PushResult()

    /**
     * The [InterceptRule] contains an invalid or unsupported HTTP method.
     *
     * @property method The method string that was rejected (e.g. "PATCH", "PURGE").
     */
    data class ConfigurationError(val message: String) : PushResult()

    data class UnsupportedMethod(val method: String) : PushResult()
}
