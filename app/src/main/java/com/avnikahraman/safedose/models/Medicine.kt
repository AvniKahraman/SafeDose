package com.avnikahraman.safedose.models

/**
 * İlaç bilgilerini tutan model sınıfı
 * Firebase Firestore'da saklanacak
 */
data class Medicine(
    val id: String = "", // Firestore document ID
    val barcode: String = "", // Taranan barkod/QR kodu
    val name: String = "", // İlaç adı (örn: "Aspirin")
    val dosage: String = "", // Dozaj bilgisi (örn: "500mg")
    val imageUrl: String = "", // İlaç görseli URL'i
    val description: String = "", // İlaç açıklaması

    // Alarm bilgileri
    val timesPerDay: Int = 0, // Günde kaç defa kullanılacak
    val startTime: String = "", // Başlangıç saati (örn: "08:00")
    val intervalHours: Int = 0, // Kaç saat aralıkla (örn: 8 saat)
    val durationDays: Int = 0, // Kaç gün kullanılacak
    val startDate: Long = 0L, // Başlangıç tarihi (timestamp)

    // Kullanıcı bilgisi
    val userId: String = "", // Hangi kullanıcıya ait

    // Metadata
    val createdAt: Long = System.currentTimeMillis(), // Oluşturulma zamanı
    val isActive: Boolean = true // Hala kullanılıyor mu?
) {
    /**
     * Firebase için parametresiz constructor gerekli
     */
    constructor() : this(
        id = "",
        barcode = "",
        name = "",
        dosage = "",
        imageUrl = "",
        description = "",
        timesPerDay = 0,
        startTime = "",
        intervalHours = 0,
        durationDays = 0,
        startDate = 0L,
        userId = "",
        createdAt = System.currentTimeMillis(),
        isActive = true
    )

    /**
     * Bitiş tarihini hesapla
     */
    fun getEndDate(): Long {
        return startDate + (durationDays * 24 * 60 * 60 * 1000L)
    }

    /**
     * İlaç hala aktif mi kontrol et
     */
    fun isStillActive(): Boolean {
        return isActive && System.currentTimeMillis() < getEndDate()
    }
}