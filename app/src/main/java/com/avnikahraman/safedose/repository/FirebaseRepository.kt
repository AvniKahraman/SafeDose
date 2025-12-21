package com.avnikahraman.safedose.repository
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.avnikahraman.safedose.models.Alarm
import com.avnikahraman.safedose.models.Medicine
import com.avnikahraman.safedose.models.User
import kotlinx.coroutines.tasks.await
import com.avnikahraman.safedose.network.RetrofitClient


class FirebaseRepository private constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Firestore collection referansları
    private val usersCollection = firestore.collection("users")
    private val medicinesCollection = firestore.collection("medicines")
    private val alarmsCollection = firestore.collection("alarms")

    companion object {
        @Volatile
        private var instance: FirebaseRepository? = null

        fun getInstance(): FirebaseRepository {
            return instance ?: synchronized(this) {
                instance ?: FirebaseRepository().also { instance = it }
            }
        }
    }

    // ==================== AUTHENTICATION ====================

    /**
     * Email ve şifre ile giriş yap
     */
    /**
     * Email ve şifre ile giriş yap. Eğer kullanıcı Firestore'da yoksa oluştur.
     */
    suspend fun signInWithEmail(email: String, password: String, nameIfNew: String = "User"): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Kullanıcı bulunamadı"))

            // Firestore'da kullanıcı belgesi var mı kontrol et
            val userDoc = usersCollection.document(firebaseUser.uid).get().await()
            if (!userDoc.exists()) {
                // Yoksa yeni kullanıcı oluştur
                val user = User(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    name = nameIfNew
                )
                saveUser(user)
            }

            // Son giriş zamanını güncelle
            updateLastLogin(firebaseUser.uid)

            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Email ve şifre ile kayıt ol
     */
    suspend fun signUpWithEmail(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { firebaseUser ->
                // Firestore'a kullanıcı bilgilerini kaydet
                val user = User(
                    id = firebaseUser.uid,
                    email = email,
                    name = name
                )
                saveUser(user)
                Result.success(firebaseUser)
            } ?: Result.failure(Exception("Kayıt başarısız"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Google ile giriş yap
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { firebaseUser ->
                // Firestore'da kullanıcı yoksa oluştur
                val userDoc = usersCollection.document(firebaseUser.uid).get().await()
                if (!userDoc.exists()) {
                    val user = User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = firebaseUser.displayName ?: "",
                        profileImageUrl = firebaseUser.photoUrl?.toString() ?: ""
                    )
                    saveUser(user)
                } else {
                    updateLastLogin(firebaseUser.uid)
                }
                Result.success(firebaseUser)
            } ?: Result.failure(Exception("Google girişi başarısız"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun fetchMedicineNameAndImage(query: String): Pair<String?, String?> {
        return try {
            val apiKey = "GOOGLE_API_KEY"
            val cx = "CUSTOM_SEARCH_ENGINE_ID"

            // İLAÇ ADI (text search)
            val textResponse = RetrofitClient.googleSearchApi.search(
                apiKey = apiKey,
                searchEngineId = cx,
                query = query
            )

            val medicineName = textResponse.body()
                ?.items
                ?.firstOrNull()
                ?.title

            // GÖRSEL (image search)
            val imageResponse = RetrofitClient.googleSearchApi.searchImage(
                apiKey = apiKey,
                searchEngineId = cx,
                query = "$query ilaç"
            )

            val imageUrl = imageResponse.body()
                ?.items
                ?.firstOrNull()
                ?.link

            Pair(medicineName, imageUrl)

        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Google search error: ${e.message}")
            Pair(null, null)
        }
    }

    /**
     * Çıkış yap
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Şu anki kullanıcıyı al
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Kullanıcı giriş yapmış mı?
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // ==================== USER OPERATIONS ====================

    /**
     * Kullanıcıyı Firestore'a kaydet
     */
    private suspend fun saveUser(user: User) {
        usersCollection.document(user.id).set(user).await()
    }

    /**
     * Kullanıcı bilgilerini getir
     */
    suspend fun getUser(userId: String): Result<User> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("Kullanıcı bulunamadı"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Son giriş zamanını güncelle
     */
    private suspend fun updateLastLogin(userId: String) {
        usersCollection.document(userId)
            .update("lastLoginAt", System.currentTimeMillis())
            .await()
    }

    // ==================== MEDICINE OPERATIONS ====================

    /**
     * İlaç ekle
     */
    suspend fun addMedicine(medicine: Medicine): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Kullanıcı oturumu yok"))
            val docRef = medicinesCollection.document()

            val medicineWithFields = medicine.copy(
                id = docRef.id,
                userId = currentUser.uid,
                active = true,  // DEĞİŞTİ
                createdAt = System.currentTimeMillis()
            )

            docRef.set(medicineWithFields).await()

            val check = docRef.get().await()
            Log.d("FirebaseRepository", "Saved with active: ${check.getBoolean("active")}")  // DEĞİŞTİ

            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Kullanıcının ilaçlarını getir
     */
    suspend fun getUserMedicines(userId: String): Result<List<Medicine>> {
        return try {
            val snapshot = medicinesCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("active", true)  // DEĞİŞTİ
                .get()
                .await()

            val medicines = snapshot.toObjects(Medicine::class.java)
            Log.d("FirebaseRepository", "Found ${medicines.size} active medicines")

            Result.success(medicines.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * İlaç bilgilerini getir
     */
    suspend fun getMedicine(medicineId: String): Result<Medicine> {
        return try {
            val snapshot = medicinesCollection.document(medicineId).get().await()
            val medicine = snapshot.toObject(Medicine::class.java)
            medicine?.let {
                Result.success(it)
            } ?: Result.failure(Exception("İlaç bulunamadı"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * İlaç güncelle
     */
    suspend fun updateMedicine(medicine: Medicine): Result<Unit> {
        return try {
            medicinesCollection.document(medicine.id).set(medicine).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * İlaç sil (soft delete)
     */
    suspend fun deleteMedicine(medicineId: String): Result<Unit> {
        return try {
            medicinesCollection.document(medicineId)
                .update("active", false)  // DEĞİŞTİ
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Barkoda göre ilaç ara
     */
    suspend fun getMedicineByBarcode(barcode: String, userId: String): Result<Medicine?> {
        return try {
            val snapshot = medicinesCollection
                .whereEqualTo("barcode", barcode)
                .whereEqualTo("userId", userId)
                .whereEqualTo("active", true)  // DEĞİŞTİ
                .limit(1)
                .get()
                .await()
            val medicine = snapshot.toObjects(Medicine::class.java).firstOrNull()
            Result.success(medicine)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ALARM OPERATIONS ====================

    /**
     * Alarm ekle
     */
    suspend fun addAlarm(alarm: Alarm): Result<String> {
        return try {
            val docRef = alarmsCollection.document()
            val alarmWithId = alarm.copy(id = docRef.id)
            docRef.set(alarmWithId).await()
            Log.d("FirebaseRepository", "Alarm added successfully: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error adding alarm: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Kullanıcının alarmlarını getir
     */
    suspend fun getUserAlarms(userId: String): Result<List<Alarm>> {
        return try {
            val snapshot = alarmsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .orderBy("hour")
                .orderBy("minute")
                .get()
                .await()
            val alarms = snapshot.toObjects(Alarm::class.java)
            Result.success(alarms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * İlaca ait alarmları getir
     */
    suspend fun getMedicineAlarms(medicineId: String): Result<List<Alarm>> {
        return try {
            val snapshot = alarmsCollection
                .whereEqualTo("medicineId", medicineId)
                .whereEqualTo("isActive", true)
                .get()
                .await()
            val alarms = snapshot.toObjects(Alarm::class.java)
            Result.success(alarms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Alarm güncelle
     */
    suspend fun updateAlarm(alarm: Alarm): Result<Unit> {
        return try {
            alarmsCollection.document(alarm.id).set(alarm).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Alarm sil
     */
    suspend fun deleteAlarm(alarmId: String): Result<Unit> {
        return try {
            alarmsCollection.document(alarmId)
                .update("isActive", false)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * İlaca ait tüm alarmları sil
     */
    suspend fun deleteMedicineAlarms(medicineId: String): Result<Unit> {
        return try {
            val snapshot = alarmsCollection
                .whereEqualTo("medicineId", medicineId)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, "isActive", false)
            }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}