package com.karuhundeveloper.notification.delivery

import org.junit.Assert.*
import org.junit.Test

class EventIdentityTest {
    @Test fun reconnectOfSameNotificationIsStableButEqualAmountPaymentsRemainDistinct() {
        val first = EventIdentity.create("install", "key1", 100L, "Paid", "Rp 1000")
        assertEquals(first, EventIdentity.create("install", "key1", 100L, "Paid", "Rp 1000"))
        assertNotEquals(first, EventIdentity.create("install", "key2", 100L, "Paid", "Rp 1000"))
        assertNotEquals(first, EventIdentity.create("install", "key1", 101L, "Paid", "Rp 1000"))
        assertNotEquals(first, EventIdentity.create("another install", "key1", 100L, "Paid", "Rp 1000"))
        assertNotEquals(first, EventIdentity.create("install", "key1", 100L, "Paid", "Rp 2000"))
    }
    @Test fun fieldBoundariesDoNotCollide() {
        assertNotEquals(EventIdentity.create("a", "bc", 1, "", ""), EventIdentity.create("ab", "c", 1, "", ""))
    }
}
