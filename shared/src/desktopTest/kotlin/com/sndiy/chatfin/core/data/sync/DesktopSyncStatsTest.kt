package com.sndiy.chatfin.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSyncStatsTest {

    @Test
    fun testSyncStatsCalculations() {
        val initialStats = SyncStats()
        assertEquals(0, initialStats.totalDownloaded)
        assertEquals(0, initialStats.totalUploaded)
        assertEquals(0, initialStats.skippedCorruptedRecords)
        assertEquals(0, initialStats.reconciledWallets)
        assertFalse(initialStats.hasChanges)

        val activeStats = SyncStats(
            downloadedAccounts = 1,
            downloadedWallets = 2,
            downloadedCategories = 5,
            downloadedTransactions = 12,
            downloadedBudgets = 3,
            uploadedAccounts = 0,
            uploadedWallets = 1,
            uploadedCategories = 0,
            uploadedTransactions = 4,
            uploadedBudgets = 0,
            skippedCorruptedRecords = 0,
            reconciledWallets = 2
        )

        assertEquals(23, activeStats.totalDownloaded, "Total download harus 1 + 2 + 5 + 12 + 3 = 23")
        assertEquals(5, activeStats.totalUploaded, "Total upload harus 1 + 4 = 5")
        assertEquals(2, activeStats.reconciledWallets)
        assertEquals(0, activeStats.skippedCorruptedRecords)
        assertTrue(activeStats.hasChanges)
    }

    @Test
    fun testStrictRupiahValidationLogic() {
        // Simulasi logika firestoreStrictLong:
        fun validateStrictLong(num: Number?): Long? {
            if (num == null) return null
            val doubleVal = num.toDouble()
            return if (doubleVal % 1.0 == 0.0 && doubleVal >= Long.MIN_VALUE.toDouble() && doubleVal <= Long.MAX_VALUE.toDouble()) {
                doubleVal.toLong()
            } else {
                null
            }
        }

        // 1. Bilangan bulat Long / Double bulat harus valid
        assertEquals(50000L, validateStrictLong(50000L))
        assertEquals(1500000L, validateStrictLong(1500000.0))
        assertEquals(0L, validateStrictLong(0.0))
        assertEquals(-25000L, validateStrictLong(-25000.0))

        // 2. Pecahan non-nol harus ditolak (return null)
        assertEquals(null, validateStrictLong(50000.75), "Pecahan non-nol .75 harus ditolak")
        assertEquals(null, validateStrictLong(100.0001), "Pecahan non-nol .0001 harus ditolak")
        assertEquals(null, validateStrictLong(99.99), "Pecahan non-nol .99 harus ditolak")
    }
}
