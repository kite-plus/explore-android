package plus.kite.explore.data

import android.content.Context
import kotlin.coroutines.cancellation.CancellationException
import plus.kite.explore.R

/** What went wrong talking to Explore. Server errors carry the stable code to branch on. */
sealed class ApiError(detail: String) : Exception(detail) {
    /**
     * An answer with an error status. [serverMessage] is already in the interface language;
     * [messageRes] stands in for it when a proxy answered instead of Explore.
     */
    class Server(
        override val status: Int,
        override val code: String,
        val serverMessage: String?,
        val messageRes: Int? = null,
        val submissionId: String? = null,
        val report: CheckReport? = null,
    ) : ApiError("$status $code")

    class Offline : ApiError("offline")
    class TimedOut : ApiError("timed out")
    class Transport(detail: String) : ApiError(detail)
    class InvalidResponse(detail: String) : ApiError(detail)

    open val code: String? get() = null
    open val status: Int? get() = null
    val isUnauthorized: Boolean get() = status == 401

    companion object {
        /** Reads an error response. Proxies in front of Explore may answer with HTML. */
        fun from(status: Int, body: String): ApiError {
            val envelope = try {
                ExploreJson.decodeFromString(ErrorEnvelope.serializer(), body)
            } catch (_: Exception) {
                null
            }
            if (envelope != null) {
                return Server(
                    status, envelope.error.code, envelope.error.message.ifEmpty { null },
                    submissionId = envelope.submissionId, report = envelope.checkReport,
                )
            }
            return when {
                status == 429 -> Server(status, "rate_limited", null, R.string.error_rate_limited)
                status == 404 -> Server(status, "not_found", null, R.string.error_not_found)
                status >= 500 -> Server(status, "internal", null, R.string.error_internal)
                else -> Server(status, "invalid_request", null, R.string.error_invalid_request)
            }
        }
    }
}

/** A message fit for the interface. */
fun Throwable.readableMessage(context: Context): String = when (this) {
    is ApiError.Server -> serverMessage ?: context.getString(messageRes ?: R.string.error_invalid_request)
    is ApiError.Offline -> context.getString(R.string.error_offline)
    is ApiError.TimedOut -> context.getString(R.string.error_timed_out)
    is ApiError.Transport -> context.getString(R.string.error_transport)
    is ApiError.InvalidResponse -> context.getString(R.string.error_invalid_response)
    else -> localizedMessage ?: context.getString(R.string.error_transport)
}

val Throwable.isCancellation: Boolean get() = this is CancellationException
