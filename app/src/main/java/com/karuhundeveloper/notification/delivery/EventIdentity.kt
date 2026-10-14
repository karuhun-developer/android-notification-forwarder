package com.karuhundeveloper.notification.delivery

import java.nio.ByteBuffer
import java.security.MessageDigest

object EventIdentity {
    fun create(installationId: String, key: String, postedAt: Long, title: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(installationId, key, postedAt.toString(), title, text).forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
