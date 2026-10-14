package com.karuhundeveloper.notification.delivery

object DeliveryState {
    const val QUEUED = "QUEUED"
    const val SENDING = "SENDING"
    const val RETRYING = "RETRYING"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}
