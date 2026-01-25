package com.avnikahraman.safedose.models

data class Medicine(
    val id: String = "",
    val barcode: String = "",
    val name: String = "",
    val dosage: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val startTime: String = "",
    val intervalHours: Int = 2,
    val durationDays: Int = 0,
    val startDate: Long = 0L,
    val userId: String = "",
    val createdAt: Long = 0L,
    val active: Boolean = true
) {
    fun getEndDate(): Long {
        return startDate + (durationDays * 24 * 60 * 60 * 1000L)
    }

    fun isStillActive(): Boolean {
        return active && System.currentTimeMillis() < getEndDate()
    }
}
