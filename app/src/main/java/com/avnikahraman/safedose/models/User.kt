package com.avnikahraman.safedose.models

/**
 * Kullanıcı bilgilerini tutan model sınıfı
 * Firebase Authentication ve Firestore'da saklanacak
 */
data class User(
    val id: String = "", // Firebase Auth UID
    val email: String = "", // Email adresi
    val name: String = "", // Kullanıcı adı
    val profileImageUrl: String = "", // Profil resmi (Google ile giriş yapanlar için)

    // Metadata
    val createdAt: Long = System.currentTimeMillis(), // Kayıt tarihi
    val lastLoginAt: Long = System.currentTimeMillis(), // Son giriş

    // Ayarlar
    val notificationsEnabled: Boolean = true, // Bildirimler açık mı?
    val reminderMinutesBefore: Int = 5 // Kaç dakika önce hatırlatma
) {
    /**
     * Firebase için parametresiz constructor
     */
    constructor() : this(
        id = "",
        email = "",
        name = "",
        profileImageUrl = "",
        createdAt = System.currentTimeMillis(),
        lastLoginAt = System.currentTimeMillis(),
        notificationsEnabled = true,
        reminderMinutesBefore = 5
    )

    /**
     * Kullanıcının görünen ismini al
     * Email'den önce @ işaretini kullan
     */
    fun getDisplayName(): String {
        return if (name.isNotBlank()) {
            name
        } else {
            email.substringBefore("@")
        }
    }

    /**
     * Email doğrulanmış mı kontrol et (Firebase Auth'dan)
     */
    fun isEmailVerified(): Boolean {
        // Bu bilgi genelde Firebase Auth'dan gelir
        // Burada sadece placeholder
        return true
    }
}