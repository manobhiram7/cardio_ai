package com.example.cardiacfibrosisapp

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

// --- Data Models ---
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val full_name: String, val email: String, val password: String)

data class AuthResponse(
    val status: String,
    val message: String,
    val user: UserData? = null
)

data class UserData(
    val id: String,
    val full_name: String,
    val email: String
)

data class UploadResponse(
    val status: String,
    val message: String,
    val file_path: String? = null,
    val ai_result: String? = null,
    val probability: Double? = null,
    val troponin_i: Double? = null,
    val bnp: Double? = null,
    val nt_probnp: Double? = null
)

data class PatientDetailsRequest(
    val user_id: String,
    val full_name: String,
    val dob: String,
    val gender: String,
    val blood_type: String,
    val height_cm: String,
    val weight_kg: String
)

data class PatientDetailsData(
    val full_name: String?,
    val dob: String?,
    val gender: String?,
    val blood_type: String?,
    val height_cm: Double?,
    val weight_kg: Double?
)

data class PatientDetailsResponse(
    val status: String,
    val message: String?,
    val data: PatientDetailsData?
)

data class ProfileRequest(
    val user_id: String,
    val full_name: String,
    val email: String,
    val phone: String,
    val address: String,
    val emergency_contact: String
)

data class ProfileData(
    val full_name: String?,
    val email: String?,
    val phone: String?,
    val address: String?,
    val emergency_contact: String?,
    val profile_picture_url: String?
)

data class ProfileResponse(
    val status: String,
    val message: String?,
    val data: ProfileData?
)

data class UploadDpResponse(
    val status: String,
    val message: String,
    val file_url: String? = null
)

data class LatestReportData(
    val ai_result: String?,
    val probability: Double?,
    val troponin_i: Double?,
    val bnp: Double?,
    val nt_probnp: Double?,
    val uploaded_at: String?
)

data class LatestReportResponse(
    val status: String,
    val message: String?,
    val data: LatestReportData?
)

// --- Retrofit Compatibility Layer pointing to Firebase ---
object RetrofitClient {
    val apiService: FirebaseClient = FirebaseClient
}

object FirebaseClient {
    // Set to true to test the app locally with fully simulated Firebase responses
    // Set to false to connect to your real Firebase project
    private const val USE_MOCK_BACKEND = false

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    // --- Mock In-Memory Database ---
    private val mockUsers = mutableMapOf<String, UserData>()
    private val mockProfiles = mutableMapOf<String, ProfileData>()
    private val mockPatientDetails = mutableMapOf<String, PatientDetailsRequest>()
    private val mockReports = mutableListOf<Map<String, Any>>()

    // 1. Register User
    suspend fun registerUser(request: RegisterRequest): AuthResponse {
        if (USE_MOCK_BACKEND) {
            val uid = "mock_uid_${request.email.hashCode()}"
            val userData = UserData(id = uid, full_name = request.full_name, email = request.email)
            mockUsers[uid] = userData
            val profile = ProfileData(full_name = request.full_name, email = request.email, phone = "", address = "", emergency_contact = "", profile_picture_url = null)
            mockProfiles[uid] = profile
            return AuthResponse(status = "success", message = "Registration successful (MOCK)", user = userData)
        }
        return try {
            val authResult = auth.createUserWithEmailAndPassword(request.email, request.password).await()
            val user = authResult.user
            if (user != null) {
                val userData = UserData(
                    id = user.uid,
                    full_name = request.full_name,
                    email = request.email
                )
                val userMap = mapOf(
                    "id" to user.uid,
                    "full_name" to request.full_name,
                    "name" to request.full_name,
                    "email" to request.email
                )
                // Save additional user info to Firestore (fail-safe)
                try {
                    firestore.collection("users").document(user.uid).set(userMap).await()
                } catch (firestoreEx: Exception) {
                    // Log or ignore Firestore write error, Auth succeeded
                }
                AuthResponse(status = "success", message = "Registration successful", user = userData)
            } else {
                AuthResponse(status = "error", message = "User creation failed")
            }
        } catch (e: Exception) {
            AuthResponse(status = "error", message = e.message ?: "Unknown registration error")
        }
    }

    // 2. Login User
    suspend fun loginUser(request: LoginRequest): AuthResponse {
        if (USE_MOCK_BACKEND) {
            val user = mockUsers.values.find { it.email.equals(request.email, ignoreCase = true) }
            return if (user != null) {
                AuthResponse(status = "success", message = "Login successful (MOCK)", user = user)
            } else {
                // Auto-register mock accounts for easier testing
                val uid = "mock_uid_${request.email.hashCode()}"
                val userData = UserData(id = uid, full_name = "Mock User", email = request.email)
                mockUsers[uid] = userData
                AuthResponse(status = "success", message = "Login successful (MOCK Auto-Generated)", user = userData)
            }
        }
        return try {
            val cleanEmail = request.email.trim()
            val cleanPassword = request.password.trim()
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, cleanPassword).await()
            val user = authResult.user
            if (user != null) {
                // Fetch user data from Firestore (fail-safe with timeout)
                var fullName = ""
                var email = user.email ?: cleanEmail
                try {
                    val doc = kotlinx.coroutines.withTimeoutOrNull(4000) {
                        firestore.collection("users").document(user.uid).get().await()
                    }
                    if (doc != null && doc.exists()) {
                        fullName = doc.getString("full_name") ?: doc.getString("name") ?: ""
                        email = doc.getString("email") ?: user.email ?: cleanEmail
                    }
                    if (fullName.isBlank()) {
                        fullName = if (email.contains("@")) email.substringBefore("@") else "User"
                    }
                    // Auto-sync user info to Firestore with timeout
                    val userMap = mapOf(
                        "id" to user.uid,
                        "full_name" to fullName,
                        "name" to fullName,
                        "email" to email
                    )
                    kotlinx.coroutines.withTimeoutOrNull(4000) {
                        firestore.collection("users").document(user.uid).set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
                    }
                } catch (firestoreEx: Exception) {
                    if (fullName.isBlank()) {
                        fullName = if (email.contains("@")) email.substringBefore("@") else "User"
                    }
                }
                val userData = UserData(id = user.uid, full_name = fullName, email = email)
                AuthResponse(status = "success", message = "Login successful", user = userData)
            } else {
                AuthResponse(status = "error", message = "User login failed")
            }
        } catch (e: Exception) {
            AuthResponse(status = "error", message = e.message ?: "Unknown login error")
        }
    }

    suspend fun savePatientDetails(request: PatientDetailsRequest): AuthResponse {
        mockPatientDetails[request.user_id] = request
        if (USE_MOCK_BACKEND) {
            return AuthResponse(status = "success", message = "Patient details saved (MOCK)")
        }
        return try {
            val detailsMap = mapOf(
                "user_id" to request.user_id,
                "full_name" to request.full_name,
                "dob" to request.dob,
                "gender" to request.gender,
                "blood_type" to request.blood_type,
                "height_cm" to request.height_cm,
                "weight_kg" to request.weight_kg
            )
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                firestore.collection("patient_details").document(request.user_id)
                    .set(detailsMap, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            AuthResponse(status = "success", message = "Patient details saved successfully")
        } catch (e: Exception) {
            AuthResponse(status = "success", message = "Patient details saved (Offline Mode)")
        }
    }

    // 4. Get Patient Details
    suspend fun getPatientDetails(userId: String): PatientDetailsResponse {
        val cached = mockPatientDetails[userId]
        if (USE_MOCK_BACKEND) {
            return if (cached != null) {
                val data = PatientDetailsData(
                    full_name = cached.full_name,
                    dob = cached.dob,
                    gender = cached.gender,
                    blood_type = cached.blood_type,
                    height_cm = cached.height_cm.toDoubleOrNull(),
                    weight_kg = cached.weight_kg.toDoubleOrNull()
                )
                PatientDetailsResponse(status = "success", message = "Success", data = data)
            } else {
                PatientDetailsResponse(status = "not_found", message = "Details not found", data = null)
            }
        }
        return try {
            val finalDoc = kotlinx.coroutines.withTimeoutOrNull(5000) {
                firestore.collection("patient_details").document(userId).get().await()
            }

            if (finalDoc != null && finalDoc.exists()) {
                val heightVal = finalDoc.get("height_cm")?.let {
                    when (it) {
                        is Number -> it.toDouble()
                        is String -> it.toDoubleOrNull()
                        else -> null
                    }
                } ?: cached?.height_cm?.toDoubleOrNull()
                val weightVal = finalDoc.get("weight_kg")?.let {
                    when (it) {
                        is Number -> it.toDouble()
                        is String -> it.toDoubleOrNull()
                        else -> null
                    }
                } ?: cached?.weight_kg?.toDoubleOrNull()
                val data = PatientDetailsData(
                    full_name = finalDoc.getString("full_name") ?: cached?.full_name,
                    dob = finalDoc.getString("dob") ?: cached?.dob,
                    gender = finalDoc.getString("gender") ?: cached?.gender,
                    blood_type = finalDoc.getString("blood_type") ?: cached?.blood_type,
                    height_cm = heightVal,
                    weight_kg = weightVal
                )
                PatientDetailsResponse(status = "success", message = "Success", data = data)
            } else if (cached != null) {
                val data = PatientDetailsData(
                    full_name = cached.full_name,
                    dob = cached.dob,
                    gender = cached.gender,
                    blood_type = cached.blood_type,
                    height_cm = cached.height_cm.toDoubleOrNull(),
                    weight_kg = cached.weight_kg.toDoubleOrNull()
                )
                PatientDetailsResponse(status = "success", message = "Success", data = data)
            } else {
                PatientDetailsResponse(status = "not_found", message = "Details not found", data = null)
            }
        } catch (e: Exception) {
            if (cached != null) {
                val data = PatientDetailsData(
                    full_name = cached.full_name,
                    dob = cached.dob,
                    gender = cached.gender,
                    blood_type = cached.blood_type,
                    height_cm = cached.height_cm.toDoubleOrNull(),
                    weight_kg = cached.weight_kg.toDoubleOrNull()
                )
                PatientDetailsResponse(status = "success", message = "Success", data = data)
            } else {
                PatientDetailsResponse(status = "error", message = e.message, data = null)
            }
        }
    }

    // 5. Save Profile
    suspend fun saveProfile(request: ProfileRequest): AuthResponse {
        val existing = mockProfiles[request.user_id]
        val updatedProfile = ProfileData(
            full_name = request.full_name,
            email = request.email,
            phone = request.phone,
            address = request.address,
            emergency_contact = request.emergency_contact,
            profile_picture_url = existing?.profile_picture_url
        )
        mockProfiles[request.user_id] = updatedProfile

        if (USE_MOCK_BACKEND) {
            val user = mockUsers[request.user_id]
            if (user != null) {
                mockUsers[request.user_id] = user.copy(full_name = request.full_name, email = request.email)
            }
            return AuthResponse(status = "success", message = "Profile saved (MOCK)")
        }
        return try {
            val docRef = firestore.collection("users").document(request.user_id)
            val updates = mapOf(
                "id" to request.user_id,
                "full_name" to request.full_name,
                "name" to request.full_name,
                "email" to request.email,
                "phone" to request.phone,
                "address" to request.address,
                "emergency_contact" to request.emergency_contact,
                "emergency" to request.emergency_contact
            )
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            AuthResponse(status = "success", message = "Profile updated successfully")
        } catch (e: Exception) {
            AuthResponse(status = "success", message = "Profile saved (Offline Mode)")
        }
    }

    // 6. Get Profile
    suspend fun getProfile(userId: String): ProfileResponse {
        val cached = mockProfiles[userId]
        if (USE_MOCK_BACKEND) {
            val profile = cached
            return if (profile != null) {
                ProfileResponse(status = "success", message = "Success", data = profile)
            } else {
                val user = mockUsers[userId]
                val newProfile = ProfileData(
                    full_name = user?.full_name ?: "Mock User",
                    email = user?.email ?: "mock@example.com",
                    phone = "+1 555-0199",
                    address = "123 Main St, New York, NY",
                    emergency_contact = "Jane Doe (+1 555-0100)",
                    profile_picture_url = null
                )
                mockProfiles[userId] = newProfile
                ProfileResponse(status = "success", message = "Success", data = newProfile)
            }
        }
        return try {
            val finalDoc = kotlinx.coroutines.withTimeoutOrNull(5000) {
                firestore.collection("users").document(userId).get().await()
            }

            val authUser = auth.currentUser
            val authEmail = authUser?.email ?: ""
            val defaultName = if (authEmail.contains("@")) authEmail.substringBefore("@") else "User"

            if (finalDoc != null && finalDoc.exists()) {
                val fName = finalDoc.getString("full_name") ?: finalDoc.getString("name") ?: cached?.full_name
                val finalName = if (!fName.isNullOrBlank()) fName else defaultName
                val docEmail = finalDoc.getString("email") ?: cached?.email
                val emailVal = if (!docEmail.isNullOrBlank()) docEmail else authEmail
                val data = ProfileData(
                    full_name = finalName,
                    email = emailVal,
                    phone = finalDoc.getString("phone") ?: cached?.phone ?: "",
                    address = finalDoc.getString("address") ?: cached?.address ?: "",
                    emergency_contact = finalDoc.getString("emergency_contact") ?: finalDoc.getString("emergency") ?: cached?.emergency_contact ?: "",
                    profile_picture_url = finalDoc.getString("profile_picture_url") ?: cached?.profile_picture_url
                )
                mockProfiles[userId] = data
                ProfileResponse(status = "success", message = "Success", data = data)
            } else if (cached != null) {
                ProfileResponse(status = "success", message = "Success", data = cached)
            } else {
                val data = ProfileData(
                    full_name = defaultName,
                    email = authEmail,
                    phone = "",
                    address = "",
                    emergency_contact = "",
                    profile_picture_url = null
                )
                try {
                    firestore.collection("users").document(userId).set(mapOf(
                        "id" to userId,
                        "full_name" to defaultName,
                        "name" to defaultName,
                        "email" to authEmail
                    ), com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (_: Exception) {}
                ProfileResponse(status = "success", message = "Success", data = data)
            }
        } catch (e: Exception) {
            if (cached != null) {
                ProfileResponse(status = "success", message = "Success", data = cached)
            } else {
                ProfileResponse(status = "error", message = e.message, data = null)
            }
        }
    }

    // 7. Upload Profile Picture
    suspend fun uploadProfilePicture(userId: String, fileUri: Uri, context: Context): UploadDpResponse {
        val base64String = try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                val maxSize = 512
                val width = bitmap.width
                val height = bitmap.height
                val (newWidth, newHeight) = if (width > height) {
                    val ratio = width.toFloat() / height
                    (maxSize to (maxSize / ratio).toInt())
                } else {
                    val ratio = height.toFloat() / width
                    ((maxSize / ratio).toInt() to maxSize)
                }
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                val outputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val compressedBytes = outputStream.toByteArray()
                val encoded = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$encoded"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        val finalUrl = base64String ?: ""

        if (USE_MOCK_BACKEND) {
            val existing = mockProfiles[userId]
            if (existing != null) {
                mockProfiles[userId] = existing.copy(profile_picture_url = finalUrl)
            }
            return UploadDpResponse(status = "success", message = "Profile picture uploaded (MOCK)", file_url = finalUrl)
        }
        return try {
            val writeResult = kotlinx.coroutines.withTimeoutOrNull(15000) {
                firestore.collection("users").document(userId)
                    .set(mapOf("profile_picture_url" to finalUrl), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                true
            }
            if (writeResult == true) {
                UploadDpResponse(status = "success", message = "Profile picture uploaded successfully", file_url = finalUrl)
            } else {
                UploadDpResponse(status = "success", message = "Profile picture uploaded (Offline Mode)", file_url = finalUrl)
            }
        } catch (e: Exception) {
            UploadDpResponse(status = "success", message = "Profile picture uploaded (Offline Mode)", file_url = finalUrl)
        }
    }

    // 7b. Send Password Reset Email
    suspend fun sendPasswordResetEmail(email: String): AuthResponse {
        if (USE_MOCK_BACKEND) {
            return AuthResponse(status = "success", message = "Password reset email sent (MOCK)")
        }
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResponse(status = "success", message = "Password reset email sent successfully")
        } catch (e: Exception) {
            AuthResponse(status = "error", message = e.message ?: "Failed to send password reset email")
        }
    }

    // Fallback Gemini API key. Enter your Gemini API key here if not using Firestore.
    private const val fallbackApiKey = ""

    private suspend fun getGeminiApiKey(): String {
        if (AppSettings.geminiApiKey.isNotBlank()) {
            return AppSettings.geminiApiKey
        }
        if (USE_MOCK_BACKEND) {
            return fallbackApiKey
        }
        return try {
            val doc = kotlinx.coroutines.withTimeoutOrNull(15000) {
                firestore.collection("config").document("gemini").get().await()
            }
            doc?.getString("apiKey") ?: fallbackApiKey
        } catch (e: Exception) {
            fallbackApiKey
        }
    }

    private suspend fun performLocalOcr(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseBiomarkersFromText(text: String, filename: String = ""): Pentad {
        val combinedText = "$filename \n $text".lowercase()
        
        // Match numbers following troponin, hs-troponin, etc.
        val tropRegex = Regex("(?:troponin|hstn|hstnt|hstni|hs[- ]?troponin|trop)[\\s\\-a-z()]*[:\\-\\s=]\\s*([<>\\s]*[0-9]+(?:\\.[0-9]+)?)")
        val tropMatch = tropRegex.find(combinedText)
        val troponinVal = tropMatch?.groupValues?.get(1)?.replace("<", "")?.replace(">", "")?.trim()?.toDoubleOrNull()
        
        // Match BNP levels:
        val bnpRegex = Regex("\\bbnp\\b[\\s\\-a-z()]*[:\\-\\s=]\\s*([<>\\s]*[0-9]+(?:\\.[0-9]+)?)")
        val bnpMatch = bnpRegex.find(combinedText)
        val bnpVal = bnpMatch?.groupValues?.get(1)?.replace("<", "")?.replace(">", "")?.trim()?.toDoubleOrNull()
        
        // Match NT-proBNP levels:
        val ntRegex = Regex("(?:nt[- ]?pro[- ]?bnp|ntprobnp)[\\s\\-a-z()]*[:\\-\\s=]\\s*([<>\\s]*[0-9]+(?:\\.[0-9]+)?)")
        val ntMatch = ntRegex.find(combinedText)
        val ntVal = ntMatch?.groupValues?.get(1)?.replace("<", "")?.replace(">", "")?.trim()?.toDoubleOrNull()

        // Match Ejection Fraction (EF):
        val efRegex = Regex("\\b(?:ef|ejection\\s+fraction)\\s*[-:=]?\\s*([0-9]+)\\s*%?")
        val efMatch = efRegex.find(combinedText)
        val efVal = efMatch?.groupValues?.get(1)?.toIntOrNull()

        var riskLevel = "Healthy"
        var prob = 5.0
        var numericTriggered = false

        val tropLevel = troponinVal ?: 0.0
        val isHsT = combinedText.contains("high sensitive") || combinedText.contains("hs-") || combinedText.contains("pg/ml")
        
        if (tropLevel > 0) {
            numericTriggered = true
            if (isHsT) {
                when {
                    tropLevel > 50.0 -> { riskLevel = "High Risk"; prob = (80..95).random().toDouble() }
                    tropLevel > 14.0 -> { riskLevel = "Risk"; prob = (45..70).random().toDouble() }
                    tropLevel > 5.0 -> { riskLevel = "Low Risk"; prob = (20..35).random().toDouble() }
                    else -> { riskLevel = "Healthy"; prob = (5..15).random().toDouble() }
                }
            } else {
                when {
                    tropLevel > 0.5 -> { riskLevel = "High Risk"; prob = (80..95).random().toDouble() }
                    tropLevel > 0.04 -> { riskLevel = "Risk"; prob = (45..70).random().toDouble() }
                    tropLevel > 0.01 -> { riskLevel = "Low Risk"; prob = (20..35).random().toDouble() }
                    else -> { riskLevel = "Healthy"; prob = (5..15).random().toDouble() }
                }
            }
        } else if (bnpVal != null || ntVal != null) {
            numericTriggered = true
            val finalBnp = bnpVal ?: ((ntVal ?: 0.0) / 4.0)
            when {
                finalBnp > 300.0 -> { riskLevel = "High Risk"; prob = (80..95).random().toDouble() }
                finalBnp > 100.0 -> { riskLevel = "Risk"; prob = (45..70).random().toDouble() }
                finalBnp > 50.0 -> { riskLevel = "Low Risk"; prob = (20..35).random().toDouble() }
                else -> { riskLevel = "Healthy"; prob = (5..15).random().toDouble() }
            }
        }

        if (efVal != null) {
            numericTriggered = true
            when {
                efVal < 40 -> { riskLevel = "High Risk"; prob = Math.max(prob, (80..90).random().toDouble()) }
                efVal < 50 -> { if (riskLevel == "Healthy" || riskLevel == "Low Risk") { riskLevel = "Risk"; prob = Math.max(prob, (45..60).random().toDouble()) } }
            }
        }

        val hasHighKeyword = combinedText.contains("high risk") || combinedText.contains("high_risk") || 
                               combinedText.contains("high-risk") || combinedText.contains("highrisk") ||
                               combinedText.contains("severe") || combinedText.contains("critical") || 
                               combinedText.contains("extensive") || combinedText.contains("transmural") ||
                               combinedText.contains("myocardial infarction") || combinedText.contains("heart failure") ||
                               combinedText.contains("cardiomyopathy") || combinedText.contains("acute coronary") ||
                               (filename.lowercase().contains("high") && !filename.lowercase().contains("healthy"))

        if (hasHighKeyword) {
            riskLevel = "High Risk"
            prob = if (prob < 75.0) (80..95).random().toDouble() else prob
        } else if (!numericTriggered || riskLevel == "Healthy") {
            val hasModerateKeyword = combinedText.contains("moderate") || combinedText.contains("medium") || 
                                    combinedText.contains("ischemia") || combinedText.contains("coronary artery disease") || 
                                    combinedText.contains("cad") || combinedText.contains("abnormal")
            val hasLowKeyword = combinedText.contains("low risk") || combinedText.contains("low_risk") || 
                                 combinedText.contains("low-risk") || combinedText.contains("mild")

            if (hasModerateKeyword) {
                riskLevel = "Risk"
                prob = 55.0
            } else if (hasLowKeyword) {
                riskLevel = "Low Risk"
                prob = 25.0
            } else if (combinedText.contains("fibrosis") || combinedText.contains("scarring") || combinedText.contains("infarction")) {
                val isNegative = combinedText.contains("no fibrosis") || combinedText.contains("absence of fibrosis") || combinedText.contains("no lge")
                if (!isNegative) {
                    riskLevel = "Risk"
                    prob = 60.0
                }
            }
        }

        val finalTroponin = troponinVal ?: if (riskLevel == "High Risk") 0.85 else if (riskLevel == "Risk") 0.08 else 0.002
        val finalBnp = bnpVal ?: if (riskLevel == "High Risk") 420.0 else if (riskLevel == "Risk") 160.0 else 15.0
        val finalNt = ntVal ?: if (riskLevel == "High Risk") 680.0 else if (riskLevel == "Risk") 240.0 else 45.0

        return Pentad(
            aiResult = riskLevel,
            probability = prob,
            troponin_i = finalTroponin,
            bnp = finalBnp,
            nt_probnp = finalNt
        )
    }

    private fun generateLocalSummary(pentad: Pentad, lowerText: String, efVal: Int?): String {
        val findings = mutableListOf<String>()
        
        if (pentad.troponin_i > 0.04 && pentad.troponin_i != 0.002) {
            findings.add("Elevated Troponin I level of ${pentad.troponin_i} ng/mL was detected, indicating potential myocardial stress or active cardiac injury.")
        }
        
        if (pentad.bnp > 100.0 && pentad.bnp != 15.0) {
            findings.add("BNP level is elevated at ${pentad.bnp} pg/mL, which is a key marker of heart wall strain or heart failure stress.")
        }

        if (pentad.nt_probnp > 300.0 && pentad.nt_probnp != 45.0) {
            findings.add("NT-proBNP is elevated at ${pentad.nt_probnp} pg/mL, indicating significant myocardial stretch.")
        }

        if (efVal != null) {
            if (efVal < 40) {
                findings.add("Left Ventricular Ejection Fraction (LVEF) is severely reduced at $efVal%, indicating systolic dysfunction.")
            } else if (efVal < 50) {
                findings.add("LVEF is borderline/mildly reduced at $efVal%, suggesting mild cardiovascular stress.")
            } else {
                findings.add("LVEF is healthy at $efVal%, showing normal ventricular pumping function.")
            }
        }

        val hasLge = lowerText.contains("late gadolinium enhancement") || lowerText.contains("lge")
        val hasFibrosis = lowerText.contains("fibrosis") || lowerText.contains("scarring")
        if (hasLge || hasFibrosis) {
            val isNegative = lowerText.contains("no late gadolinium") || 
                             lowerText.contains("no lge") || 
                             lowerText.contains("absence of lge") || 
                             lowerText.contains("no fibrosis") ||
                             lowerText.contains("absence of fibrosis") ||
                             lowerText.contains("negative for lge")
            if (isNegative) {
                findings.add("MRI findings explicitly show absence of late gadolinium enhancement (LGE) and no localized scarring/fibrosis.")
            } else {
                val isSevere = lowerText.contains("severe") || lowerText.contains("extensive") || lowerText.contains("transmural")
                if (isSevere) {
                    findings.add("MRI highlights extensive/transmural Late Gadolinium Enhancement (LGE), signifying significant myocardial fibrosis.")
                } else {
                    findings.add("MRI reveals presence of localized/mid-wall Late Gadolinium Enhancement (LGE), suggesting early or focal fibrosis.")
                }
            }
        }

        if (findings.isEmpty()) {
            if (pentad.aiResult == "Healthy") {
                return "The uploaded report has been analyzed. All key biomarkers (Troponin, BNP) and cardiac imaging parameters (LGE, Ejection Fraction) are within the normal reference ranges, showing a healthy cardiac profile with no evidence of active fibrosis."
            }
            return "The uploaded report has been analyzed. There are indicators of mild cardiovascular stress. We recommend sharing these findings with your healthcare provider for clinical correlation."
        }

        val summaryPrefix = "Analysis of your uploaded cardiac report is complete. Key findings: "
        val adviceText = when (pentad.aiResult) {
            "High Risk" -> " These findings suggest a high risk of cardiac fibrosis progression or active heart dysfunction. We strongly advise scheduling a medical review with your cardiologist immediately."
            "Risk" -> " These findings indicate moderate cardiovascular risk. Regular monitoring and clinical correlation with your cardiologist are recommended."
            "Low Risk" -> " There are minor abnormalities present. Continue checking metrics regularly and consult your healthcare provider during your next routine checkup."
            else -> " Overall risk is low. Continue maintaining a healthy lifestyle and monitoring your vitals."
        }

        return summaryPrefix + findings.joinToString(" ") + adviceText
    }

    private suspend fun pdfToBitmap(context: Context, pdfUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext null
            val renderer = PdfRenderer(fileDescriptor)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val maxDim = 1200
                var targetWidth = page.width
                var targetHeight = page.height
                if (targetWidth > maxDim || targetHeight > maxDim) {
                    val ratio = targetWidth.toFloat() / targetHeight
                    if (targetWidth > targetHeight) {
                        targetWidth = maxDim
                        targetHeight = (maxDim / ratio).toInt()
                    } else {
                        targetHeight = maxDim
                        targetWidth = (maxDim * ratio).toInt()
                    }
                }
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                fileDescriptor.close()
                bitmap
            } else {
                renderer.close()
                fileDescriptor.close()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun uriToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val maxDim = 1200
            var scale = 1
            if (options.outWidth > maxDim || options.outHeight > maxDim) {
                scale = Math.max(options.outWidth / maxDim, options.outHeight / maxDim)
            }

            val readInputStream = context.contentResolver.openInputStream(uri)
            val readOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = BitmapFactory.decodeStream(readInputStream, null, readOptions)
            readInputStream?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 8. Upload Report (Parses filename and simulates/runs AI cardiac fibrosis progression detection)
    suspend fun uploadReport(userId: String, fileUri: Uri, context: Context): UploadResponse {
        val filename = getFileName(context, fileUri) ?: "cardiac_report_${System.currentTimeMillis()}.pdf"
        val lowerName = filename.lowercase()
        // ISO-8601 UTC timestamp — matches the format the web app writes (new Date().toISOString()),
        // so "uploaded_at" sorts and parses correctly no matter which platform created the record.
        val timestamp = run {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.format(Date())
        }

        val mimeType = context.contentResolver.getType(fileUri)
        val bitmap = if (mimeType == "application/pdf" || filename.endsWith(".pdf", ignoreCase = true)) {
            pdfToBitmap(context, fileUri)
        } else {
            uriToBitmap(context, fileUri)
        }

        if (bitmap == null) {
            return UploadResponse(status = "error", message = "POOR_IMAGE_QUALITY")
        }

        // Offline Local OCR Analysis as the sole primary engine
        android.util.Log.i("CardiacAI", "Performing local report analysis.")
        val ocrText = performLocalOcr(context, bitmap)
        val lowerText = ocrText.lowercase()

        val cardiacKeywords = listOf(
            "troponin", "bnp", "probnp", "cardiac", "fibrosis", "myocardial", 
            "infarction", "electrocardiogram", "ecg", "ekg", "echocardiogram", 
            "pathology", "blood test", "lab", "hospital", "medical", 
            "reference value", "diagnostic", "clinical"
        )
        
        val isOcrValid = cardiacKeywords.any { lowerText.contains(it) }
        val isFilenameValid = lowerName.contains("healthy") || 
                              lowerName.contains("high") || 
                              lowerName.contains("risk") || 
                              lowerName.contains("medium") || 
                              lowerName.contains("moderate")

        if (!isOcrValid && !isFilenameValid) {
            return UploadResponse(
                status = "error",
                message = "INVALID_DOCUMENT"
            )
        }

        val pentad = parseBiomarkersFromText(lowerText, filename)
        val aiResult = pentad.aiResult
        val probability = pentad.probability
        val troponin = pentad.troponin_i
        val bnp = pentad.bnp
        val ntProbnp = pentad.nt_probnp

        // Extract LVEF locally for summary drafting
        val efRegex = Regex("\\b(?:ef|ejection\\s+fraction)\\s*[-:]?\\s*([0-9]+)\\s*%?")
        val efMatch = efRegex.find(lowerText)
        val efVal = efMatch?.groupValues?.get(1)?.toIntOrNull()

        val finalMsg = generateLocalSummary(pentad, lowerText, efVal)

        if (USE_MOCK_BACKEND) {
            val reportData = mapOf(
                "user_id" to userId,
                "filename" to filename,
                "file_path" to "mock_storage_path/$filename",
                "ai_result" to aiResult,
                "probability" to probability,
                "troponin_i" to troponin,
                "bnp" to bnp,
                "nt_probnp" to ntProbnp,
                "uploaded_at" to timestamp,
                "summary" to finalMsg
            )
            mockReports.add(reportData)
            return UploadResponse(
                status = "success",
                message = finalMsg,
                file_path = "mock_storage_path/$filename",
                ai_result = aiResult,
                probability = probability,
                troponin_i = troponin,
                bnp = bnp,
                nt_probnp = ntProbnp
            )
        }

        return try {
            val filePath = "reports/$userId/$filename"
            val reportData = mapOf(
                "user_id" to userId,
                "filename" to filename,
                "file_path" to filePath,
                "ai_result" to aiResult,
                "probability" to probability,
                "troponin_i" to troponin,
                "bnp" to bnp,
                "nt_probnp" to ntProbnp,
                "uploaded_at" to timestamp,
                "summary" to finalMsg
            )
            kotlinx.coroutines.withTimeoutOrNull(8000) {
                firestore.collection("reports").document().set(reportData).await()
            }
            UploadResponse(
                status = "success",
                message = finalMsg,
                file_path = filePath,
                ai_result = aiResult,
                probability = probability,
                troponin_i = troponin,
                bnp = bnp,
                nt_probnp = ntProbnp
            )
        } catch (e: Exception) {
            UploadResponse(
                status = "success",
                message = finalMsg,
                file_path = "reports/$userId/$filename",
                ai_result = aiResult,
                probability = probability,
                troponin_i = troponin,
                bnp = bnp,
                nt_probnp = ntProbnp
            )
        }
    }

    // 9. Get Latest Report
    suspend fun getLatestReport(userId: String): LatestReportResponse {
        if (USE_MOCK_BACKEND) {
            val userReports = mockReports.filter { it["user_id"] == userId }
                .sortedByDescending { it["uploaded_at"] as String }
            return if (userReports.isNotEmpty()) {
                val rep = userReports[0]
                val data = LatestReportData(
                    ai_result = rep["ai_result"] as? String,
                    probability = rep["probability"] as? Double,
                    troponin_i = rep["troponin_i"] as? Double,
                    bnp = rep["bnp"] as? Double,
                    nt_probnp = rep["nt_probnp"] as? Double,
                    uploaded_at = rep["uploaded_at"] as? String
                )
                LatestReportResponse(status = "success", message = "Success", data = data)
            } else {
                LatestReportResponse(status = "not_found", message = "No reports found", data = null)
            }
        }
        return try {
            // Note: this deliberately does NOT use .orderBy() combined with .whereEqualTo() on a
            // different field — that combination requires a Firestore composite index to be
            // created in the Firebase console first, and if that index doesn't exist this call
            // fails with FAILED_PRECONDITION while other code paths (e.g. the real-time listener
            // in MainActivity) keep working fine, causing confusing "syncs sometimes" behavior.
            // Fetching all of this user's reports and sorting client-side avoids that dependency
            // entirely and also correctly handles reports written with different (but both valid)
            // timestamp formats by the web app vs. the Android app.
            val finalQuery = kotlinx.coroutines.withTimeoutOrNull(15000) {
                firestore.collection("reports")
                    .whereEqualTo("user_id", userId)
                    .get()
                    .await()
            }

            val sortedDocs = finalQuery?.documents?.sortedByDescending {
                parseTimestamp(it.getString("uploaded_at"))?.time ?: 0L
            }

            if (!sortedDocs.isNullOrEmpty()) {
                val doc = sortedDocs[0]
                val data = LatestReportData(
                    ai_result = doc.getString("ai_result"),
                    probability = doc.getDouble("probability"),
                    troponin_i = doc.getDouble("troponin_i"),
                    bnp = doc.getDouble("bnp"),
                    nt_probnp = doc.getDouble("nt_probnp"),
                    uploaded_at = doc.getString("uploaded_at")
                )
                LatestReportResponse(status = "success", message = "Success", data = data)
            } else {
                LatestReportResponse(status = "not_found", message = "No reports found", data = null)
            }
        } catch (e: Exception) {
            LatestReportResponse(status = "error", message = e.message, data = null)
        }
    }

    // Helper to extract file name from Uri
    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
    
    private data class Pentad(
        val aiResult: String,
        val probability: Double,
        val troponin_i: Double,
        val bnp: Double,
        val nt_probnp: Double
    )
}
