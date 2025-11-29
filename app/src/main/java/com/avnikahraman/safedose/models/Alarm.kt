package com.avnikahraman.safedose.models

import java.util.Calendar

/**
 * Alarm bilgilerini tutan model sınıfı
 * Her ilaç için günde birden fazla alarm olabilir
 */
data class Alarm(
    val id: String = "", // Unique alarm ID
    val medicineId: String = "", // Hangi ilaca ait
    val medicineName: String = "", // İlaç adı (bildirimde göstermek için)
    val userId: String = "", // Hangi kullanıcıya ait

    // Zaman bilgileri
    val hour: Int = 0, // Saat (0-23)
    val minute: Int = 0, // Dakika (0-59)
    val timeString: String = "", // Gösterim için "08:00" formatında

    // Durum
    val isActive: Boolean = true, // Alarm aktif mi?
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // Hangi günler (1=Pazartesi, 7=Pazar)

    // Request Code (AlarmManager için unique olmalı)
    val requestCode: Int = 0,

    // Metadata
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Firebase için parametresiz constructor
     */
    constructor() : this(
        id = "",
        medicineId = "",
        medicineName = "",
        userId = "",
        hour = 0,
        minute = 0,
        timeString = "",
        isActive = true,
        daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
        requestCode = 0,
        createdAt = System.currentTimeMillis()
    )

    /**
     * Alarm zamanını string olarak döndür
     */
    fun getFormattedTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    /**
     * Sonraki alarm zamanını hesapla (milisaniye cinsinden)
     */
    fun getNextAlarmTimeMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Eğer alarm bugün için geçmişse yarına al
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return calendar.timeInMillis
    }
}