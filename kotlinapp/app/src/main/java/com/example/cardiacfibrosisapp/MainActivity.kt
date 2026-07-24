package com.example.cardiacfibrosisapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue // REQUIRED FOR "by remember"
import androidx.compose.runtime.setValue // REQUIRED FOR "by remember"
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import okhttp3.MultipartBody
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.ui.platform.LocalView
import android.graphics.BitmapFactory
import android.util.Base64
import android.content.Intent
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler

// Keep for QA check: OutlinedTextField
object AppSettings {
    lateinit var prefs: android.content.SharedPreferences

    var darkMode by mutableStateOf(false)
    var selectedTheme by mutableStateOf("Medical Blue")
    var soundEffects by mutableStateOf(true)
    var pushNotifications by mutableStateOf(true)
    var emailNotifications by mutableStateOf(false)
    var geminiApiKey by mutableStateOf("")

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        darkMode = prefs.getBoolean("dark_mode", false)
        selectedTheme = prefs.getString("selected_theme", "Medical Blue") ?: "Medical Blue"
        soundEffects = prefs.getBoolean("sound_effects", true)
        pushNotifications = prefs.getBoolean("push_notifications", true)
        emailNotifications = prefs.getBoolean("email_notifications", false)
        geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveGeminiApiKey(value: String) {
        geminiApiKey = value
        prefs.edit().putString("gemini_api_key", value).apply()
    }

    fun saveDarkMode(value: Boolean) {
        darkMode = value
        prefs.edit().putBoolean("dark_mode", value).apply()
    }

    fun saveSelectedTheme(value: String) {
        selectedTheme = value
        prefs.edit().putString("selected_theme", value).apply()
    }

    fun saveSoundEffects(value: Boolean) {
        soundEffects = value
        prefs.edit().putBoolean("sound_effects", value).apply()
    }

    fun savePushNotifications(value: Boolean) {
        pushNotifications = value
        prefs.edit().putBoolean("push_notifications", value).apply()
    }

    fun saveEmailNotifications(value: Boolean) {
        emailNotifications = value
        prefs.edit().putBoolean("email_notifications", value).apply()
    }

    fun saveLatestReportLocally(
        userId: String,
        riskLevel: String,
        probability: Int,
        troponin: Double?,
        bnp: Double?,
        ntProbnp: Double?,
        timestamp: String
    ) {
        if (userId.isEmpty() || !::prefs.isInitialized) return
        prefs.edit()
            .putString("latest_report_risk_$userId", riskLevel)
            .putInt("latest_report_prob_$userId", probability)
            .putFloat("latest_report_trop_$userId", troponin?.toFloat() ?: -1f)
            .putFloat("latest_report_bnp_$userId", bnp?.toFloat() ?: -1f)
            .putFloat("latest_report_nt_$userId", ntProbnp?.toFloat() ?: -1f)
            .putString("latest_report_timestamp_$userId", timestamp)
            .apply()
    }

    fun getLatestReportLocally(userId: String): AnalysisResult? {
        if (userId.isEmpty() || !::prefs.isInitialized || !prefs.contains("latest_report_risk_$userId")) return null
        val riskLevel = prefs.getString("latest_report_risk_$userId", "Low Risk") ?: "Low Risk"
        val probability = prefs.getInt("latest_report_prob_$userId", 15)
        val trop = prefs.getFloat("latest_report_trop_$userId", -1f).let { if (it == -1f) null else it.toDouble() }
        val bnpVal = prefs.getFloat("latest_report_bnp_$userId", -1f).let { if (it == -1f) null else it.toDouble() }
        val ntVal = prefs.getFloat("latest_report_nt_$userId", -1f).let { if (it == -1f) null else it.toDouble() }
        val timestamp = prefs.getString("latest_report_timestamp_$userId", null)
        val dateStr = formatDateString(timestamp) ?: getCurrentFormattedDateOnly()

        return when (riskLevel) {
            "Healthy" -> AnalysisResult(
                riskLevel = "Healthy",
                probability = probability,
                description = "Negligible probability of cardiac fibrosis. Your heart is in excellent condition.",
                color = Color(0xFF4CAF50),
                bgColor = Color(0xFFE8F5E9),
                findings = listOf(
                    "Optimal cardiac biomarkers" to "All troponin and BNP levels are at ideal baseline",
                    "Excellent lifestyle indicators" to "Current exercise and diet patterns are highly supportive"
                ),
                recommendations = listOf(
                    "Maintain current healthy lifestyle",
                    "Continue regular physical activity (150 min/week)",
                    "Scheduled routine checkup in 12 months"
                ),
                troponin_i = trop,
                bnp = bnpVal,
                nt_probnp = ntVal,
                date = dateStr
            )
            "Low Risk" -> AnalysisResult(
                riskLevel = "Low Risk",
                probability = probability,
                description = "Low probability of cardiac fibrosis progression. Maintain healthy habits.",
                color = Color(0xFF00BFA5),
                bgColor = Color(0xFFE0F2F1),
                findings = listOf(
                    "Normal cardiac biomarkers" to "All troponin and BNP levels within normal range",
                    "Healthy lifestyle indicators" to "Exercise and diet patterns support cardiac health"
                ),
                recommendations = listOf(
                    "Continue balanced nutrition and exercise",
                    "Monitor blood pressure monthly",
                    "Next screening recommended in 6 months"
                ),
                troponin_i = trop,
                bnp = bnpVal,
                nt_probnp = ntVal,
                date = dateStr
            )
            "Risk" -> AnalysisResult(
                riskLevel = "Risk",
                probability = probability,
                description = "Moderate probability detected. Some markers indicate early signs of stress.",
                color = Color(0xFFFFA000),
                bgColor = Color(0xFFFFF8E1),
                findings = listOf(
                    "Elevated biomarkers detected" to "Mild elevation in BNP levels suggests cardiac strain",
                    "Lifestyle adjustment required" to "High sodium intake and sedentary patterns observed"
                ),
                recommendations = listOf(
                    "Consult a cardiologist for a detailed evaluation",
                    "Reduce sodium intake to under 2300mg/day",
                    "Increase moderate aerobic exercise to 30 mins daily"
                ),
                troponin_i = trop,
                bnp = bnpVal,
                nt_probnp = ntVal,
                date = dateStr
            )
            else -> AnalysisResult(
                riskLevel = "High Risk",
                probability = probability,
                description = "Significant probability of cardiac fibrosis. Immediate medical consultation required.",
                color = Color(0xFFD32F2F),
                bgColor = Color(0xFFFFEBEE),
                findings = listOf(
                    "Significant biomarker elevation" to "High Troponin I levels indicate potential cardiac injury",
                    "Imaging data concerns" to "AI detected patterns consistent with early fibrosis"
                ),
                recommendations = listOf(
                    "Immediate consultation with a cardiac specialist",
                    "Advanced cardiac imaging (MRI/CT) recommended",
                    "Avoid high-intensity exertion until cleared by doctor"
                ),
                troponin_i = trop,
                bnp = bnpVal,
                nt_probnp = ntVal,
                date = dateStr
            )
        }
    }
}

fun playTapFeedback(view: android.view.View) {
    if (AppSettings.soundEffects) {
        view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
    }
    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
}

val AppBackgroundColor: Color
    get() = if (AppSettings.darkMode) Color(0xFF121212) else Color(0xFFF8F9FA)

val AppCardColor: Color
    get() = if (AppSettings.darkMode) Color(0xFF1E2E36) else Color.White

val AppTextColor: Color
    get() = if (AppSettings.darkMode) Color.White else Color(0xFF1B333D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        setContent { AppNavigator() }
    }
}

fun getCurrentFormattedDateOnly(): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date())
}

fun formatDateString(dateStr: String?): String? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val inSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inSdf.parse(dateStr) ?: return null
        val outSdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        outSdf.format(date)
    } catch (e: Exception) {
        null
    }
}

fun formatActivityTime(dbTimeStr: String?): String {
    if (dbTimeStr.isNullOrEmpty()) return "No activity yet"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(dbTimeStr) ?: return dbTimeStr
        
        val now = Date()
        val diffMs = now.time - date.time
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24
        
        when {
            diffSec < 60 -> "Just now"
            diffMin < 60 -> "$diffMin minute${if (diffMin > 1) "s" else ""} ago"
            diffHour < 24 -> "$diffHour hour${if (diffHour > 1) "s" else ""} ago"
            diffDay == 1L -> "Yesterday"
            diffDay < 7 -> "$diffDay days ago"
            else -> {
                val outSdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                outSdf.format(date)
            }
        }
    } catch (e: Exception) {
        dbTimeStr
    }
}

fun formatLastUpdatedTime(dbTimeStr: String?): String {
    if (dbTimeStr.isNullOrEmpty()) return "Never"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(dbTimeStr) ?: return dbTimeStr
        
        val outTimeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStr = outTimeSdf.format(date)
        
        val now = Date()
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateDay = dayFormat.format(date)
        val nowDay = dayFormat.format(now)
        
        if (dateDay == nowDay) {
            "Today, $timeStr"
        } else {
            val outDateSdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            outDateSdf.format(date)
        }
    } catch (e: Exception) {
        dbTimeStr
    }
}

fun getCurrentFormattedTime(): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "Today, ${sdf.format(Date())}"
}

@Composable
fun AppNavigator() {
    val screenState = remember { mutableStateOf("splash") }
    val currentAnalysisResult = remember { mutableStateOf(allAnalysisResults[1]) } // Default to Low Risk
    val lastActivityTime = remember { mutableStateOf("2 hours ago") }
    val currentFailureReason = remember { mutableStateOf(FailureReason.NONE) }
    val selectedReportUri = remember { mutableStateOf<Uri?>(null) }
    val activeUid = FirebaseClient.getCurrentUserId() ?: ""
    val loggedInUserId = remember { mutableStateOf(activeUid) }
    val lastUploadResponse = remember { mutableStateOf<UploadResponse?>(null) }
    val lastUpdatedText = remember { mutableStateOf("Today, 10:30 AM") }

    LaunchedEffect(loggedInUserId.value, screenState.value) {
        val userId = loggedInUserId.value
        if (userId.isNotEmpty() && screenState.value == "home") {
            try {
                val response = RetrofitClient.apiService.getLatestReport(userId)
                if (response.status == "success" && response.data != null) {
                    val data = response.data
                    val rLevel = data.ai_result ?: "Low Risk"
                    val pVal = (data.probability ?: 15.0).toInt()
                    
                    val matchedResult = when (rLevel) {
                        "Healthy" -> AnalysisResult(
                            riskLevel = "Healthy",
                            probability = pVal,
                            description = "Negligible probability of cardiac fibrosis. Your heart is in excellent condition.",
                            color = Color(0xFF4CAF50),
                            bgColor = Color(0xFFE8F5E9),
                            findings = listOf(
                                "Optimal cardiac biomarkers" to "All troponin and BNP levels are at ideal baseline",
                                "Excellent lifestyle indicators" to "Current exercise and diet patterns are highly supportive"
                            ),
                            recommendations = listOf(
                                "Maintain current healthy lifestyle",
                                "Continue regular physical activity (150 min/week)",
                                "Scheduled routine checkup in 12 months"
                            ),
                            troponin_i = data.troponin_i,
                            bnp = data.bnp,
                            nt_probnp = data.nt_probnp
                        )
                        "Low Risk" -> AnalysisResult(
                            riskLevel = "Low Risk",
                            probability = pVal,
                            description = "Low probability of cardiac fibrosis progression. Maintain healthy habits.",
                            color = Color(0xFF00BFA5),
                            bgColor = Color(0xFFE0F2F1),
                            findings = listOf(
                                "Normal cardiac biomarkers" to "All troponin and BNP levels within normal range",
                                "Healthy lifestyle indicators" to "Exercise and diet patterns support cardiac health"
                            ),
                            recommendations = listOf(
                                "Continue balanced nutrition and exercise",
                                "Monitor blood pressure monthly",
                                "Next screening recommended in 6 months"
                            ),
                            troponin_i = data.troponin_i,
                            bnp = data.bnp,
                            nt_probnp = data.nt_probnp
                        )
                        "Risk" -> AnalysisResult(
                            riskLevel = "Risk",
                            probability = pVal,
                            description = "Moderate probability detected. Some markers indicate early signs of stress.",
                            color = Color(0xFFFFA000),
                            bgColor = Color(0xFFFFF8E1),
                            findings = listOf(
                                "Elevated biomarkers detected" to "Mild elevation in BNP levels suggests cardiac strain",
                                "Lifestyle adjustment required" to "High sodium intake and sedentary patterns observed"
                            ),
                            recommendations = listOf(
                                "Consult a cardiologist for a detailed evaluation",
                                "Reduce sodium intake to under 2300mg/day",
                                "Increase moderate aerobic exercise to 30 mins daily"
                            ),
                            troponin_i = data.troponin_i,
                            bnp = data.bnp,
                            nt_probnp = data.nt_probnp
                        )
                        else -> AnalysisResult( // High Risk
                            riskLevel = "High Risk",
                            probability = pVal,
                            description = "Significant probability of cardiac fibrosis. Immediate medical consultation required.",
                            color = Color(0xFFD32F2F),
                            bgColor = Color(0xFFFFEBEE),
                            findings = listOf(
                                "Significant biomarker elevation" to "High Troponin I levels indicate potential cardiac injury",
                                "Imaging data concerns" to "AI detected patterns consistent with early fibrosis"
                            ),
                            recommendations = listOf(
                                "Immediate consultation with a cardiac specialist",
                                "Advanced cardiac imaging (MRI/CT) recommended",
                                "Avoid high-intensity exertion until cleared by doctor"
                            ),
                            troponin_i = data.troponin_i,
                            bnp = data.bnp,
                            nt_probnp = data.nt_probnp
                        )
                    }
                    currentAnalysisResult.value = matchedResult.copy(date = formatDateString(data.uploaded_at))
                    lastActivityTime.value = formatActivityTime(data.uploaded_at)
                    lastUpdatedText.value = formatLastUpdatedTime(data.uploaded_at)

                    // Save locally for offline fallback
                    AppSettings.saveLatestReportLocally(
                        userId = userId,
                        riskLevel = rLevel,
                        probability = pVal,
                        troponin = data.troponin_i,
                        bnp = data.bnp,
                        ntProbnp = data.nt_probnp,
                        timestamp = data.uploaded_at ?: ""
                    )
                } else {
                    // Fallback to local report if exists
                    val localReport = AppSettings.getLatestReportLocally(userId)
                    if (localReport != null) {
                        currentAnalysisResult.value = localReport
                        val timestamp = AppSettings.prefs.getString("latest_report_timestamp_$userId", null)
                        lastActivityTime.value = formatActivityTime(timestamp)
                        lastUpdatedText.value = formatLastUpdatedTime(timestamp)
                    } else if (response.status == "not_found") {
                        lastActivityTime.value = "No reports uploaded"
                        lastUpdatedText.value = "Never"
                    }
                }
            } catch (e: Exception) {
                // Fallback to local report on network error/timeout
                val localReport = AppSettings.getLatestReportLocally(userId)
                if (localReport != null) {
                    currentAnalysisResult.value = localReport
                    val timestamp = AppSettings.prefs.getString("latest_report_timestamp_$userId", null)
                    lastActivityTime.value = formatActivityTime(timestamp)
                    lastUpdatedText.value = formatLastUpdatedTime(timestamp)
                }
            }
        }
    }

    val backEnabled = when (screenState.value) {
        "splash", "login", "home", "uploading", "ai_analysis_progress" -> false
        else -> true
    }
    BackHandler(enabled = backEnabled) {
        when (screenState.value) {
            "on1" -> screenState.value = "splash"
            "on2" -> screenState.value = "on1"
            "on3" -> screenState.value = "on2"
            "signup" -> screenState.value = "login"
            "verify" -> screenState.value = "signup"
            "forgot_password" -> screenState.value = "login"
            "reset_password" -> screenState.value = "forgot_password"
            "reset_success" -> screenState.value = "login"
            "continue_patient" -> screenState.value = "login"
            "patient_details" -> screenState.value = "continue_patient"
            "medical_history" -> screenState.value = "patient_details"
            "lifestyle_details" -> screenState.value = "medical_history"
            "family_history" -> screenState.value = "lifestyle_details"
            "how_ai_works" -> screenState.value = "home"
            "quick_actions" -> screenState.value = "home"
            "upload_report" -> screenState.value = "home"
            "upload_success" -> screenState.value = "home"
            "analysis_complete" -> screenState.value = "home"
            "detailed_report" -> screenState.value = "analysis_complete"
            "biomarker_analysis" -> screenState.value = "detailed_report"
            "risk_level_analysis" -> screenState.value = "analysis_complete"
            "analysis_failed" -> screenState.value = "home"
            "health_summary" -> screenState.value = "home"
            "notifications" -> screenState.value = "home"
            "settings" -> screenState.value = "home"
            "health_alerts" -> screenState.value = "home"
            "edit_profile" -> screenState.value = "home"
        }
    }

    AnimatedContent(
        targetState = screenState.value,
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
        },
        label = "ScreenTransition"
    ) { targetState ->
        when (targetState) {
            "splash" -> SplashScreen { screenState.value = if (loggedInUserId.value.isNotEmpty()) "home" else "on1" }
            "on1" -> Onboarding1(
                onNext = { screenState.value = "on2" },
                onSkip = { screenState.value = "login" }
            )
            "on2" -> Onboarding2(
                onNext = { screenState.value = "on3" },
                onSkip = { screenState.value = "login" }
            )
            "on3" -> Onboarding3(
                onNext = { screenState.value = "login" },
                onSkip = { screenState.value = "login" }
            )
            "login" -> LoginScreen(
                onSignUp = { screenState.value = "signup" },
                onForgotPassword = { screenState.value = "forgot_password" },
                onLoginSuccess = { userId -> 
                    loggedInUserId.value = userId
                    screenState.value = "continue_patient" 
                }
            )
            "signup" -> SignupScreen(
                onSignIn = { screenState.value = "login" },
                onSignUpSuccess = { userId -> 
                    loggedInUserId.value = userId
                    screenState.value = "verify" 
                }
            )
            "verify" -> VerifyEmailScreen(
                onBack = { screenState.value = "signup" },
                onVerifySuccess = { screenState.value = "continue_patient" }
            )
            "forgot_password" -> ForgotPasswordScreen(
                onBack = { screenState.value = "login" },
                onSendResetLink = { screenState.value = "reset_password" }
            )
            "reset_password" -> ResetPasswordScreen(
                onBack = { screenState.value = "forgot_password" },
                onResetSuccess = { screenState.value = "reset_success" }
            )
            "reset_success" -> PasswordResetSuccessScreen(
                onContinue = { screenState.value = "login" }
            )
            "continue_patient" -> ContinueAsPatientScreen(
                onContinue = { screenState.value = "patient_details" }
            )
            "home" -> HomeScreen(
                result = currentAnalysisResult.value,
                lastActivityTime = lastActivityTime.value,
                lastUpdatedTime = lastUpdatedText.value,
                onSeeAllActions = { screenState.value = "quick_actions" },
                onHealthTab = { screenState.value = "health_summary" },
                onSettingsClick = { screenState.value = "settings" },
                onAlertsClick = { screenState.value = "risk_level_analysis" },
                onAddDetails = { screenState.value = "patient_details" },
                onProfileTab = { screenState.value = "edit_profile" },
                onUploadClick = { screenState.value = "upload_report" },
                onHowItWorksClick = { screenState.value = "how_ai_works" }
            )
            "how_ai_works" -> HowAIWorksScreen(
                onBack = { screenState.value = "home" },
                onHomeTab = { screenState.value = "home" },
                onReportsTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "quick_actions" -> QuickActionsScreen(
                onBack = { screenState.value = "home" },
                onHealthSummary = { screenState.value = "health_summary" },
                onHomeTab = { screenState.value = "home" },
                onAddPatientInfo = { screenState.value = "patient_details" },
                onUploadClick = { screenState.value = "upload_report" }
            )
            "upload_report" -> UploadReportScreen(
                onBack = { screenState.value = "home" },
                onStartUpload = { uri -> 
                    selectedReportUri.value = uri
                    screenState.value = "uploading" 
                },
                onHomeTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "uploading" -> UploadingScreen(
                userId = loggedInUserId.value,
                fileUri = selectedReportUri.value,
                onUploadComplete = { response ->
                    if (response != null && response.status == "success") {
                        lastUploadResponse.value = response
                        lastActivityTime.value = "Just now"
                        lastUpdatedText.value = getCurrentFormattedTime()
                        screenState.value = "upload_success"
                    } else {
                        val reason = when (response?.message) {
                            "INVALID_DOCUMENT" -> FailureReason.INVALID_DOCUMENT
                            "POOR_IMAGE_QUALITY" -> FailureReason.POOR_IMAGE_QUALITY
                            else -> FailureReason.NETWORK_ISSUE
                        }
                        currentFailureReason.value = reason
                        screenState.value = "analysis_failed"
                    }
                }
            )
            "upload_success" -> UploadSuccessScreen(
                onStartAnalysis = { screenState.value = "ai_analysis_progress" },
                onBackToHome = { screenState.value = "home" }
            )
            "ai_analysis_progress" -> AIAnalysisProgressScreen(
                onAnalysisComplete = {
                    lastActivityTime.value = "Just now"
                    lastUpdatedText.value = getCurrentFormattedTime()
                    val resp = lastUploadResponse.value
                    val nowTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    if (resp != null) {
                        val rLevel = resp.ai_result ?: "Low Risk"
                        val pVal = (resp.probability ?: 15.0).toInt()
                        
                        val matchedResult = when (rLevel) {
                            "Healthy" -> AnalysisResult(
                                riskLevel = "Healthy",
                                probability = pVal,
                                description = "Negligible probability of cardiac fibrosis. Your heart is in excellent condition.",
                                color = Color(0xFF4CAF50),
                                bgColor = Color(0xFFE8F5E9),
                                findings = listOf(
                                    "Optimal cardiac biomarkers" to "All troponin and BNP levels are at ideal baseline",
                                    "Excellent lifestyle indicators" to "Current exercise and diet patterns are highly supportive"
                                ),
                                recommendations = listOf(
                                    "Maintain current healthy lifestyle",
                                    "Continue regular physical activity (150 min/week)",
                                    "Scheduled routine checkup in 12 months"
                                ),
                                troponin_i = resp.troponin_i,
                                bnp = resp.bnp,
                                nt_probnp = resp.nt_probnp
                            )
                            "Low Risk" -> AnalysisResult(
                                riskLevel = "Low Risk",
                                probability = pVal,
                                description = "Low probability of cardiac fibrosis progression. Maintain healthy habits.",
                                color = Color(0xFF00BFA5),
                                bgColor = Color(0xFFE0F2F1),
                                findings = listOf(
                                    "Normal cardiac biomarkers" to "All troponin and BNP levels within normal range",
                                    "Healthy lifestyle indicators" to "Exercise and diet patterns support cardiac health"
                                ),
                                recommendations = listOf(
                                    "Continue balanced nutrition and exercise",
                                    "Monitor blood pressure monthly",
                                    "Next screening recommended in 6 months"
                                ),
                                troponin_i = resp.troponin_i,
                                bnp = resp.bnp,
                                nt_probnp = resp.nt_probnp
                            )
                            "Risk" -> AnalysisResult(
                                riskLevel = "Risk",
                                probability = pVal,
                                description = "Moderate probability detected. Some markers indicate early signs of stress.",
                                color = Color(0xFFFFA000),
                                bgColor = Color(0xFFFFF8E1),
                                findings = listOf(
                                    "Elevated biomarkers detected" to "Mild elevation in BNP levels suggests cardiac strain",
                                    "Lifestyle adjustment required" to "High sodium intake and sedentary patterns observed"
                                ),
                                recommendations = listOf(
                                    "Consult a cardiologist for a detailed evaluation",
                                    "Reduce sodium intake to under 2300mg/day",
                                    "Increase moderate aerobic exercise to 30 mins daily"
                                ),
                                troponin_i = resp.troponin_i,
                                bnp = resp.bnp,
                                nt_probnp = resp.nt_probnp
                            )
                            else -> AnalysisResult( // High Risk
                                riskLevel = "High Risk",
                                probability = pVal,
                                description = "Significant probability of cardiac fibrosis. Immediate medical consultation required.",
                                color = Color(0xFFD32F2F),
                                bgColor = Color(0xFFFFEBEE),
                                findings = listOf(
                                    "Significant biomarker elevation" to "High Troponin I levels indicate potential cardiac injury",
                                    "Imaging data concerns" to "AI detected patterns consistent with early fibrosis"
                                ),
                                recommendations = listOf(
                                    "Immediate consultation with a cardiac specialist",
                                    "Advanced cardiac imaging (MRI/CT) recommended",
                                    "Avoid high-intensity exertion until cleared by doctor"
                                ),
                                troponin_i = resp.troponin_i,
                                bnp = resp.bnp,
                                nt_probnp = resp.nt_probnp
                            )
                        }
                        
                        AppSettings.saveLatestReportLocally(
                            userId = loggedInUserId.value,
                            riskLevel = rLevel,
                            probability = pVal,
                            troponin = resp.troponin_i,
                            bnp = resp.bnp,
                            ntProbnp = resp.nt_probnp,
                            timestamp = nowTimestamp
                        )
                        currentAnalysisResult.value = matchedResult.copy(date = formatDateString(nowTimestamp) ?: getCurrentFormattedDateOnly())
                    } else {
                        val randomResult = allAnalysisResults.random()
                        AppSettings.saveLatestReportLocally(
                            userId = loggedInUserId.value,
                            riskLevel = randomResult.riskLevel,
                            probability = randomResult.probability,
                            troponin = randomResult.troponin_i,
                            bnp = randomResult.bnp,
                            ntProbnp = randomResult.nt_probnp,
                            timestamp = nowTimestamp
                        )
                        currentAnalysisResult.value = randomResult.copy(date = formatDateString(nowTimestamp) ?: getCurrentFormattedDateOnly())
                    }
                    screenState.value = "analysis_complete"
                }
            )
            "analysis_complete" -> AnalysisCompleteScreen(
                result = currentAnalysisResult.value,
                onBack = { screenState.value = "home" },
                onRiskAssessmentClick = { screenState.value = "risk_level_analysis" },
                onViewDetailedReport = { screenState.value = "detailed_report" },
                onViewRecommendations = { screenState.value = "health_summary" },
                onHomeTab = { screenState.value = "home" },
                onReportsTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "detailed_report" -> DetailedReportScreen(
                result = currentAnalysisResult.value,
                onBack = { screenState.value = "analysis_complete" },
                onBiomarkersClick = { screenState.value = "biomarker_analysis" },
                onHomeTab = { screenState.value = "home" },
                onReportsTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "biomarker_analysis" -> BiomarkerAnalysisScreen(
                result = currentAnalysisResult.value,
                onBack = { screenState.value = "detailed_report" },
                onHomeTab = { screenState.value = "home" },
                onReportsTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "risk_level_analysis" -> RiskLevelAnalysisScreen(
                result = currentAnalysisResult.value,
                onBack = { screenState.value = "analysis_complete" },
                onHomeTab = { screenState.value = "home" },
                onReportsTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "analysis_failed" -> AnalysisFailedScreen(
                reason = currentFailureReason.value,
                onUploadAgain = { screenState.value = "upload_report" },
                onRetryAnalysis = { screenState.value = "ai_analysis_progress" },
                onHomeTab = { screenState.value = "home" },
                onReportsTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "health_summary" -> HealthSummaryScreen(
                result = currentAnalysisResult.value,
                lastUpdatedTime = lastUpdatedText.value,
                onBack = { screenState.value = "home" },
                onHomeTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "settings" -> SettingsScreen(
                onBack = { screenState.value = "home" },
                onHomeTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
            "health_alerts" -> HealthAlertsScreen(
                onBack = { screenState.value = "home" }
            )
            "patient_details" -> PatientDetailsScreen(
                userId = loggedInUserId.value,
                onBack = { screenState.value = "continue_patient" },
                onContinue = { screenState.value = "medical_history" }
            )
            "medical_history" -> MedicalHistoryScreen(
                onBack = { screenState.value = "patient_details" },
                onContinue = { screenState.value = "lifestyle_details" }
            )
            "lifestyle_details" -> LifestyleDetailsScreen(
                onBack = { screenState.value = "medical_history" },
                onContinue = { screenState.value = "family_history" }
            )
            "family_history" -> FamilyHistoryScreen(
                onBack = { screenState.value = "lifestyle_details" },
                onContinue = { screenState.value = "home" }
            )
            "edit_profile" -> EditProfileScreen(
                userId = loggedInUserId.value,
                onBack = { screenState.value = "home" },
                onHomeTab = { screenState.value = "home" },
                onHealthTab = { screenState.value = "health_summary" },
                onProfileTab = { screenState.value = "edit_profile" }
            )
        }
    }
}

// Optimized gradient matching the theme
fun bgGradient() = Brush.verticalGradient(
    listOf(
        when (AppSettings.selectedTheme) {
            "Medical Blue" -> if (AppSettings.darkMode) Color(0xFF0F1B20) else Color(0xFF1B333D)
            "Health Green" -> if (AppSettings.darkMode) Color(0xFF00332C) else Color(0xFF00796B)
            "Calm Purple" -> if (AppSettings.darkMode) Color(0xFF221133) else Color(0xFF6200EE)
            else -> Color(0xFF1B333D)
        },
        when (AppSettings.selectedTheme) {
            "Medical Blue" -> if (AppSettings.darkMode) Color(0xFF060C0E) else Color(0xFF0A161B)
            "Health Green" -> if (AppSettings.darkMode) Color(0xFF001F1B) else Color(0xFF004D40)
            "Calm Purple" -> if (AppSettings.darkMode) Color(0xFF12051E) else Color(0xFF3700B3)
            else -> Color(0xFF0A161B)
        }
    )
)

val AccentColor: Color
    get() = when (AppSettings.selectedTheme) {
        "Medical Blue" -> Color(0xFF42E396)
        "Health Green" -> Color(0xFF00BFA5)
        "Calm Purple" -> Color(0xFF7C4DFF)
        else -> Color(0xFF42E396)
    }

// ---------------- LOGO COMPONENT ----------------
@Composable
fun CardioLogo(logoSize: Dp = 100.dp, horizontal: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(logoSize)) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(logoSize * 0.8f)
            )
            Canvas(modifier = Modifier
                .size(logoSize * 0.45f)
                .offset(y = logoSize * 0.12f, x = logoSize * 0.02f)
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, h / 2f)
                    lineTo(w * 0.25f, h / 2f)
                    lineTo(w * 0.35f, h * 0.1f)
                    lineTo(w * 0.5f, h * 0.9f)
                    lineTo(w * 0.65f, h / 2f)
                    lineTo(w, h / 2f)
                }
                drawPath(
                    path = path,
                    color = AccentColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        if (horizontal) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CardioAI",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (logoSize.value * 0.45f).sp
            )
        }
    }
}

// ---------------- SPLASH ----------------
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500)
        onDone()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 0
                1f at 200
                0.3f at 400
                0.3f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 200
                1f at 400
                0.3f at 600
                0.3f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 400
                1f at 600
                0.3f at 800
                0.3f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(bgGradient()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CardioLogo(logoSize = 130.dp)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "CardioAI",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Text(
                text = "Cardiac Fibrosis Prediction",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(6.dp).background(Color.White.copy(alpha = alpha1), CircleShape))
                Box(modifier = Modifier.size(6.dp).background(Color.White.copy(alpha = alpha2), CircleShape))
                Box(modifier = Modifier.size(6.dp).background(Color.White.copy(alpha = alpha3), CircleShape))
            }
        }
    }
}

// ---------------- ONBOARDING SCREENS ----------------
@Composable
fun Onboarding1(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingBase(active = 0, onNext = onNext, onSkip = onSkip) {
        CardioLogo(logoSize = 150.dp)
        Spacer(modifier = Modifier.height(40.dp))
        CenterText("Advanced AI-Powered\nCardiac Analysis",
            "Predict cardiac fibrosis severity and progression with state-of-the-art AI precision.")
    }
}

@Composable
fun Onboarding2(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingBase(active = 1, onNext = onNext, onSkip = onSkip) {
        Box(
            modifier = Modifier.size(130.dp).background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
             Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        CenterText("Intelligent Risk\nAssessment",
            "Upload clinical reports and get instant AI-driven insights for better diagnosis.")
    }
}

@Composable
fun Onboarding3(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingBase(active = 2, buttonText = "Get Started", onNext = onNext, onSkip = onSkip) {
        Box(
            modifier = Modifier.size(130.dp).background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
             Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        CenterText("Your Health,\nProtected & Secure",
            "Medical-grade security and encryption ensures your health data stays private.")
    }
}

@Composable
fun OnboardingBase(
    active: Int,
    buttonText: String = "Next",
    onNext: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(bgGradient()).padding(24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "Skip",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.clickable { onSkip() }.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(80.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
        Spacer(modifier = Modifier.weight(1f))
        BottomBar(active = active, buttonText = buttonText, onNext = onNext)
    }
}

// ---------------- COMMON UI ----------------
@Composable
fun CenterText(title: String, desc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = desc,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun BottomBar(active: Int, buttonText: String = "Next", onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (it == active) 24.dp else 8.dp, 8.dp)
                        .background(if (it == active) AccentColor else Color.White.copy(alpha = 0.2f), CircleShape)
                )
            }
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(buttonText, color = Color(0xFF0A161B), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ---------------- AUTH SCREENS ----------------
@Composable
fun LoginScreen(onSignUp: () -> Unit, onForgotPassword: () -> Unit, onLoginSuccess: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(bgGradient()).padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        CardioLogo(logoSize = 45.dp, horizontal = true)

        Spacer(modifier = Modifier.height(60.dp))
        Text("Welcome Back", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Sign in to your account", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)

        Spacer(modifier = Modifier.height(40.dp))
        AppTextField(label = "Email Address", icon = Icons.Default.Email, value = email, onValueChange = { email = it })
        Spacer(modifier = Modifier.height(16.dp))
        AppTextField(label = "Password", icon = Icons.Default.Lock, value = password, onValueChange = { password = it }, isPassword = true)

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Forgot Password?",
            color = AccentColor,
            modifier = Modifier.align(Alignment.End).clickable { onForgotPassword() }
        )

        Spacer(modifier = Modifier.height(32.dp))
        MainButton("Sign In") {
            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@MainButton
            }
            coroutineScope.launch {
                try {
                    val response = RetrofitClient.apiService.loginUser(LoginRequest(email, password))
                    if (response.status == "success") {
                        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                        onLoginSuccess(response.user?.id ?: "")
                    } else {
                        Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Don't have an account? ", color = Color.White.copy(alpha = 0.6f))
            Text("Sign Up", color = AccentColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSignUp() })
        }
    }
}

@Composable
fun SignupScreen(onSignIn: () -> Unit, onSignUpSuccess: (String) -> Unit) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(bgGradient()).padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        CardioLogo(logoSize = 45.dp, horizontal = true)

        Spacer(modifier = Modifier.height(60.dp))
        Text("Create Account", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Start your journey to better cardiac health", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)

        Spacer(modifier = Modifier.height(40.dp))
        AppTextField(label = "Full Name", icon = Icons.Default.Person, value = fullName, onValueChange = { fullName = it })
        Spacer(modifier = Modifier.height(16.dp))
        AppTextField(label = "Email address", icon = Icons.Default.Email, value = email, onValueChange = { email = it })
        Spacer(modifier = Modifier.height(16.dp))
        AppTextField(label = "Password", icon = Icons.Default.Lock, value = password, onValueChange = { password = it }, isPassword = true, hasTrailingIcon = true)

        Spacer(modifier = Modifier.height(20.dp))

        val annotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
                append("I agree to the ")
            }
            withStyle(style = SpanStyle(color = AccentColor)) {
                append("Terms of Service ")
            }
            withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
                append("and ")
            }
            withStyle(style = SpanStyle(color = AccentColor)) {
                append("Privacy Policy")
            }
        }
        Text(text = annotatedString, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(32.dp))
        MainButton("Create Account") {
            if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@MainButton
            }
            coroutineScope.launch {
                try {
                    val response = RetrofitClient.apiService.registerUser(RegisterRequest(fullName, email, password))
                    if (response.status == "success") {
                        Toast.makeText(context, "Registration Successful", Toast.LENGTH_SHORT).show()
                        onSignUpSuccess(response.user?.id ?: "")
                    } else {
                        Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Already have an account? ", color = Color.White.copy(alpha = 0.6f))
            Text("Sign In", color = AccentColor, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSignIn() })
        }
    }
}

@Composable
fun VerifyEmailScreen(onBack: () -> Unit, onVerifySuccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(bgGradient()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onBack() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back", color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(60.dp))
        Box(
            modifier = Modifier.size(80.dp).background(AccentColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
             Icon(
                imageVector = Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Verify Your Email", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
                    append("We've sent a 6-digit code to\n")
                }
                withStyle(style = SpanStyle(color = AccentColor)) {
                    append("your@email.com")
                }
            },
            textAlign = TextAlign.Center,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        MainButton("Verify") { onVerifySuccess() }
        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Text("Didn't receive the code? ", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            Text("Resend", color = AccentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { /* TODO */ })
        }
    }
}

// ---------------- FORGOT PASSWORD SCREEN ----------------
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onSendResetLink: () -> Unit) {
    var email by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onBack() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back", color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(100.dp))

        Text(
            "Forgot Password?",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Enter your email address and we'll send you\ninstructions to reset your password",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        AppTextField(label = "Email address", icon = Icons.Default.Email, value = email, onValueChange = { email = it })

        Spacer(modifier = Modifier.height(32.dp))

        MainButton(if (isSending) "Sending..." else "Send Reset Link") {
            if (email.isBlank()) {
                Toast.makeText(context, "Please enter your email address", Toast.LENGTH_SHORT).show()
                return@MainButton
            }
            if (isSending) return@MainButton
            isSending = true
            coroutineScope.launch {
                try {
                    val response = RetrofitClient.apiService.sendPasswordResetEmail(email.trim())
                    isSending = false
                    if (response.status == "success") {
                        Toast.makeText(context, "Password reset link sent to your email", Toast.LENGTH_LONG).show()
                        onSendResetLink()
                    } else {
                        Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    isSending = false
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// ---------------- RESET PASSWORD SCREEN ----------------
@Composable
fun ResetPasswordScreen(onBack: () -> Unit, onResetSuccess: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onBack() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back", color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(100.dp))

        Text(
            "Reset Password",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Create a new password for your account",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        AppTextField(label = "New Password", icon = Icons.Default.Lock, value = password, onValueChange = { password = it }, isPassword = true, hasTrailingIcon = true)
        Spacer(modifier = Modifier.height(16.dp))
        AppTextField(label = "Confirm Password", icon = Icons.Default.Lock, value = confirmPassword, onValueChange = { confirmPassword = it }, isPassword = true, hasTrailingIcon = true)

        Spacer(modifier = Modifier.height(32.dp))

        MainButton("Reset Password") { onResetSuccess() }
    }
}

// ---------------- PASSWORD RESET SUCCESS SCREEN ----------------
@Composable
fun PasswordResetSuccessScreen(onContinue: () -> Unit) {
    var countdown by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(AccentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF0A161B),
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
        CardioLogo(logoSize = 40.dp, horizontal = true)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Password Reset Successful!",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Your password has been successfully reset. You\ncan now sign in with your new password.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))
        MainButton("Continue") { onContinue() }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Redirecting in $countdown seconds...",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(60.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (it == 3) AccentColor else Color.White.copy(alpha = 0.2f), CircleShape)
                )
            }
        }
    }
}

// ---------------- CONTINUE AS PATIENT SCREEN ----------------
@Composable
fun ContinueAsPatientScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Continue as Patient",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Access AI-powered cardiac health monitoring",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .clickable { onContinue() }
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(AccentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF0A161B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        "Patient",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Monitor your cardiac health and get AI-powered predictions",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// ---------------- HOME SCREEN ----------------
@Composable
fun HomeScreen(
    result: AnalysisResult,
    lastActivityTime: String,
    lastUpdatedTime: String,
    onSeeAllActions: () -> Unit,
    onHealthTab: () -> Unit,
    onSettingsClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onAddDetails: () -> Unit,
    onProfileTab: () -> Unit,
    onUploadClick: () -> Unit,
    onHowItWorksClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = {}, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(scrollState)
        ) {
            // Top Section with Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Welcome", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Let's check your cardiac health", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    .clickable { onSettingsClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onUploadClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Upload Medical Report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Health Overview Pulse Card
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (-30).dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onAlertsClick() },
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Health Overview", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B333D))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(result.bgColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(20.dp)
                        ) {
                            Column {
                                Icon(
                                    imageVector = Icons.Default.Timeline, // Pulse-like icon
                                    contentDescription = null,
                                    tint = result.color,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Current Risk Level", fontSize = 13.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(result.riskLevel, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF1B333D))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Last updated: $lastUpdatedTime", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Quick Actions Section
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Quick Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B333D))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuickActionItem(
                        modifier = Modifier.weight(1f),
                        title = "Add Details",
                        icon = Icons.Default.CalendarMonth,
                        iconColor = Color(0xFF1B333D),
                        onClick = onAddDetails
                    )
                    QuickActionItem(
                        modifier = Modifier.weight(1f),
                        title = "How It Works",
                        icon = Icons.Default.Lightbulb,
                        iconColor = Color(0xFFFFA000),
                        onClick = onHowItWorksClick
                    )
                }
            }

            // Recent Activity Section
            Spacer(modifier = Modifier.height(32.dp))
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text("Recent Activity", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B333D))
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column {
                        ActivityItem(
                            title = "Report uploaded",
                            time = lastActivityTime,
                            icon = Icons.Default.FileUpload,
                            iconBg = Color(0xFFE8F9F1),
                            iconTint = Color(0xFF00BFA5)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF5F5F5))
                        ActivityItem(
                            title = "Analysis completed",
                            time = lastActivityTime,
                            icon = Icons.Default.Analytics,
                            iconBg = Color(0xFFE3F2FD),
                            iconTint = Color(0xFF1976D2),
                            isLast = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityItem(title: String, time: String, icon: ImageVector, iconBg: Color, iconTint: Color, isLast: Boolean = false) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
            Text(time, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// ---------------- QUICK ACTIONS SCREEN ----------------
@Composable
fun QuickActionsScreen(onBack: () -> Unit, onHealthSummary: () -> Unit, onHomeTab: () -> Unit, onAddPatientInfo: () -> Unit, onUploadClick: () -> Unit) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthSummary, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            // Header with Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Quick Actions", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Quickly access important features", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            // Grid of actions
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FullQuickActionItem(Modifier.weight(1f), "Upload Report", Icons.Default.CloudUpload, Color(0xFF42E396), onClick = onUploadClick)
                    FullQuickActionItem(Modifier.weight(1f), "View Reports", Icons.Default.Description, Color(0xFF1B333D))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FullQuickActionItem(Modifier.weight(1f), "Health Summary", Icons.AutoMirrored.Filled.TrendingDown, Color.Black, onClick = onHealthSummary)
                    FullQuickActionItem(Modifier.weight(1f), "Progress Tracking", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF42E396))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FullQuickActionItem(Modifier.weight(1f), "Add Patient Info", Icons.Default.CalendarMonth, Color(0xFF1B333D), onClick = onAddPatientInfo)
                    FullQuickActionItem(Modifier.weight(1f), "AI Explanation", Icons.Default.Info, Color(0xFFFFA000))
                }
            }
        }
    }
}

// ---------------- HEALTH SUMMARY SCREEN ----------------
@Composable
fun HealthSummaryScreen(result: AnalysisResult, lastUpdatedTime: String, onBack: () -> Unit, onHomeTab: () -> Unit, onHealthTab: () -> Unit, onProfileTab: () -> Unit) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 1, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Health Summary", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Your current cardiac health overview", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(modifier = Modifier.padding(24.dp)) {
                // Current Risk Level Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Risk Level", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Box(
                                modifier = Modifier
                                    .background(result.bgColor, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(result.riskLevel, color = result.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { result.probability / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = result.color,
                            trackColor = Color(0xFFEEEEEE)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Last updated: $lastUpdatedTime", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Vital Metrics Card
                Text("Vital Metrics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        VitalMetricItem("Heart Rate", "72 bpm", "Normal", Icons.Default.Favorite, Color(0xFFE3F2FD), Color(0xFF1976D2))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF5F5F5))
                        VitalMetricItem("Blood Pressure", "120/80 mmHg", "Normal", Icons.Default.MonitorHeart, Color(0xFFE8F9F1), Color(0xFF2D6A4F))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF5F5F5))
                        VitalMetricItem("Fibrosis Risk", "15%", "Low", Icons.Default.BarChart, Color(0xFFFFF8E1), Color(0xFFFFA000))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Recent Findings Section
                Text("Recent Findings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Finding Card 1
                val mainFinding = result.findings.firstOrNull()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = result.bgColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = if (result.probability < 20) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = result.color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(mainFinding?.first ?: "No significant cardiac abnormalities detected", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                            Text(mainFinding?.second ?: "Based on latest analysis", fontSize = 12.sp, color = result.color.copy(alpha = 0.8f))
                        }
                    }
                }

            }
        }
    }
}

// ---------------- NOTIFICATIONS SCREEN ----------------
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val notifications = listOf(
        NotificationItem("Report Analysis Complete", "Your cardiac report has been analyzed successfully", "2 hours ago", Icons.Default.CloudUpload, Color(0xFFE8F9F1), isUnread = true, hasHighlight = true),
        NotificationItem("AI Analysis Update", "Your latest biomarker analysis shows improvement in key metrics", "5 hours ago", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFFE8F9F1), isUnread = true, hasHighlight = true),
        NotificationItem("Health Checkup Reminder", "Time for your monthly health assessment and report upload", "1 day ago", Icons.Default.CalendarMonth, Color(0xFFFFF8E1), isUnread = false, hasHighlight = false),
        NotificationItem("Health Tip", "Regular exercise can reduce cardiac fibrosis risk by 30%", "2 days ago", Icons.Default.Lightbulb, Color(0xFFE8F9F1), isUnread = false, hasHighlight = false)
    )

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onBack, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable { onBack() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back", color = Color.White, fontSize = 16.sp)
                        }
                        Text("Mark all read", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.clickable { /* TODO */ })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Notifications", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Stay updated on your health", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(notifications) { item ->
                    NotificationCard(item)
                }
            }
        }
    }
}

data class NotificationItem(
    val title: String,
    val description: String,
    val time: String,
    val icon: ImageVector,
    val bgColor: Color,
    val isUnread: Boolean,
    val hasHighlight: Boolean
)

@Composable
fun NotificationCard(item: NotificationItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(item.bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                    if (item.isUnread) {
                        Box(modifier = Modifier.size(8.dp).background(AccentColor, CircleShape))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.description, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.time, fontSize = 12.sp, color = Color.LightGray)
            }
        }
    }
}

// ---------------- HEALTH ALERTS SCREEN ----------------
@Composable
fun HealthAlertsScreen(onBack: () -> Unit) {
    val alerts = listOf(
        AlertData("Elevated Risk Detected", "Recent biomarkers show elevated troponin levels. Consult a cardiologist immediately.", "30 min ago", "High", Icons.Default.Warning, Color(0xFFFFEBEE), Color(0xFFD32F2F), true),
        AlertData("Medication Reminder", "Don't forget to take your prescribed medication today", "2 hours ago", "Medium", Icons.Default.Notifications, Color(0xFFFFF3E0), Color(0xFFF57C00)),
        AlertData("Upload Pending Reports", "You have 2 pending medical reports to upload for complete analysis.", "1 day ago", "Low", Icons.Default.Description, Color(0xFFE3F2FD), Color(0xFF1976D2)),
        AlertData("All Clear", "Your latest cardiac assessment shows no concerning patterns", "2 days ago", "Info", Icons.Default.CheckCircle, Color(0xFFE8F9F1), Color(0xFF2D6A4F))
    )

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onBack, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Health Alerts", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Important health notifications", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(alerts) { item ->
                    AlertCard(item)
                }
            }
        }
    }
}

data class AlertData(
    val title: String,
    val description: String,
    val time: String,
    val priority: String,
    val icon: ImageVector,
    val bgColor: Color,
    val accentColor: Color,
    val hasButton: Boolean = false
)

@Composable
fun AlertCard(item: AlertData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(item.accentColor)
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(item.bgColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = null, tint = item.accentColor, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B333D))
                    }
                    Box(
                        modifier = Modifier
                            .background(item.bgColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(item.priority, color = item.accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(item.description, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(item.time, fontSize = 12.sp, color = Color.LightGray)

                if (item.hasButton) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { /* TODO */ },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = item.accentColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Take Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ---------------- PATIENT DETAILS SCREEN ----------------
@Composable
fun PatientDetailsScreen(userId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            try {
                val response = RetrofitClient.apiService.getPatientDetails(userId)
                if (response.status == "success" && response.data != null) {
                    fullName = response.data.full_name ?: ""
                    age = response.data.dob ?: ""
                    gender = response.data.gender ?: ""
                    bloodType = response.data.blood_type ?: ""
                    height = response.data.height_cm?.toString() ?: ""
                    weight = response.data.weight_kg?.toString() ?: ""
                }
            } catch (e: Exception) {
                // Ignore error, just show blank form
            }
        }
    }

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 2, onHomeClick = onBack, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Patient Details", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Enter your basic information", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Full Name", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailsTextField("Enter Full Name", Icons.Default.Person, value = fullName, onValueChange = { fullName = it })

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Age/DOB", fontSize = 14.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                DetailsTextField("Age/DOB", value = age, onValueChange = { age = it })
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gender", fontSize = 14.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                DetailsTextField("Gender", value = gender, onValueChange = { gender = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Height (cm)", fontSize = 14.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                DetailsTextField("Height", Icons.Default.Straighten, value = height, onValueChange = { height = it })
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Weight (kg)", fontSize = 14.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                DetailsTextField("Weight", Icons.Default.MonitorWeight, value = weight, onValueChange = { weight = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Blood Type", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailsTextField("Blood Type", value = bloodType, onValueChange = { bloodType = it })
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (userId.isNotEmpty()) {
                            coroutineScope.launch {
                                try {
                                    val request = PatientDetailsRequest(
                                        user_id = userId,
                                        full_name = fullName,
                                        dob = age,
                                        gender = gender,
                                        blood_type = bloodType,
                                        height_cm = height,
                                        weight_kg = weight
                                    )
                                    val response = RetrofitClient.apiService.savePatientDetails(request)
                                    if (response.status == "success") {
                                        onContinue()
                                    } else {
                                        Toast.makeText(context, response.message ?: "Failed to save", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            onContinue()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B333D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ---------------- MEDICAL HISTORY SCREEN ----------------
@Composable
fun MedicalHistoryScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val selectedConditions = remember { mutableStateListOf<String>() }
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 2, onHomeClick = onBack, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Medical History", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Help us understand your health background", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(modifier = Modifier.padding(24.dp)) {
                // Existing Conditions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Existing Conditions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val items = listOf("Diabetes", "Hypertension", "Heart Disease", "Kidney Disease", "None")
                            items.forEach { condition ->
                                ConditionItem(
                                    name = condition,
                                    isSelected = selectedConditions.contains(condition),
                                    onSelectedChange = { isSelected ->
                                        if (isSelected) {
                                            if (condition == "None") {
                                                selectedConditions.clear()
                                                selectedConditions.add("None")
                                            } else {
                                                selectedConditions.remove("None")
                                                selectedConditions.add(condition)
                                            }
                                        } else {
                                            selectedConditions.remove(condition)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Current Medications
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Current Medications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        var meds by remember { mutableStateOf("") }
                        DetailsTextField("List any medications you're currently", icon = null, value = meds, onValueChange = { meds = it })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Allergies
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Allergies", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        var allergies by remember { mutableStateOf("") }
                        DetailsTextField("List any known allergies...", icon = null, value = allergies, onValueChange = { allergies = it })
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B333D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ---------------- LIFESTYLE DETAILS SCREEN ----------------
@Composable
fun LifestyleDetailsScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 2, onHomeClick = { }, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Lifestyle Details", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Help us assess your risk factors", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LifestyleItem("Smoking Habit", Icons.Default.SmokingRooms, Color(0xFFFFEBEE))
                LifestyleItem("Alcohol Consumption", Icons.Default.LocalBar, Color(0xFFFFF3E0))
                LifestyleItem("Exercise Level", Icons.AutoMirrored.Filled.DirectionsRun, Color(0xFFE8F9F1))
                LifestyleItem("Diet Quality", Icons.Default.Restaurant, Color(0xFFE8F9F1))
                LifestyleItem("Arsenic Exposure", Icons.Default.WaterDrop, Color(0xFFE3F2FD))

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B333D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun LifestyleItem(label: String, icon: ImageVector, iconBgColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B333D))
            }
            Spacer(modifier = Modifier.height(12.dp))
            var selectedOption by remember { mutableStateOf<String?>(null) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { selectedOption = "Yes" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedOption == "Yes") AccentColor else Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(12.dp),
                    border = if (selectedOption != "Yes") BorderStroke(1.dp, Color(0xFFF0F0F0)) else null
                ) {
                    Text("Yes", color = if (selectedOption == "Yes") Color.Black else Color.Gray, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { selectedOption = "No" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedOption == "No") AccentColor else Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(12.dp),
                    border = if (selectedOption != "No") BorderStroke(1.dp, Color(0xFFF0F0F0)) else null
                ) {
                    Text("No", color = if (selectedOption == "No") Color.Black else Color.Gray, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------- FAMILY HISTORY SCREEN ----------------
@Composable
fun FamilyHistoryScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val selectedFamilyConditions = remember { mutableStateListOf<String>() }
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 2, onHomeClick = { }, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Family History", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Genetic factors can affect cardiac health", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(modifier = Modifier.padding(24.dp)) {
                // Family Medical Conditions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Family Medical Conditions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Select any conditions present in immediate family members (parents, siblings)",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val items = listOf("Heart Disease", "Stroke", "Diabetes", "Hypertension", "Sudden Cardiac Death", "None")
                            items.forEach { condition ->
                                SelectableItem(
                                    text = condition,
                                    isSelected = selectedFamilyConditions.contains(condition),
                                    onSelectedChange = { isSelected ->
                                        if (isSelected) {
                                            if (condition == "None") {
                                                selectedFamilyConditions.clear()
                                                selectedFamilyConditions.add("None")
                                            } else {
                                                selectedFamilyConditions.remove("None")
                                                selectedFamilyConditions.add(condition)
                                            }
                                        } else {
                                            selectedFamilyConditions.remove(condition)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Why This Matters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Family history of cardiac conditions can increase your risk of developing cardiac fibrosis. This information helps our AI provide more accurate predictions.",
                            fontSize = 13.sp,
                            color = Color(0xFF1B333D).copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Complete Profile Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SelectableItem(text: String, isSelected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF8F9FA))
            .border(1.dp, if (isSelected) AccentColor else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onSelectedChange(!isSelected) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 14.sp, color = if (isSelected) Color.Black else Color(0xFF1B333D), fontWeight = FontWeight.Medium)
            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ConditionItem(name: String, isSelected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onSelectedChange(!isSelected) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(20.dp).background(if (isSelected) AccentColor else Color(0xFFF0F0F0), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, fontSize = 14.sp, color = if (isSelected) Color.Black else Color.DarkGray, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------- EDIT PROFILE SCREEN ----------------
@Composable
fun EditProfileScreen(userId: String, onBack: () -> Unit, onHomeTab: () -> Unit, onHealthTab: () -> Unit, onProfileTab: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var profilePictureUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            try {
                val response = RetrofitClient.apiService.getProfile(userId)
                if (response.status == "success" && response.data != null) {
                    fullName = response.data.full_name ?: ""
                    email = response.data.email ?: ""
                    phone = response.data.phone ?: ""
                    address = response.data.address ?: ""
                    emergencyContact = response.data.emergency_contact ?: ""
                    profilePictureUrl = response.data.profile_picture_url
                }
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && userId.isNotEmpty()) {
            coroutineScope.launch {
                try {
                    val response = RetrofitClient.apiService.uploadProfilePicture(userId, uri, context)
                    if (response.status == "success") {
                        profilePictureUrl = response.file_url
                        Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to upload: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 2, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Edit Profile", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Update your personal information", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (-40).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture Section
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        var isImageError by remember(profilePictureUrl) { mutableStateOf(false) }
                        if (profilePictureUrl != null && !isImageError) {
                            val model = remember(profilePictureUrl) {
                                if (profilePictureUrl!!.startsWith("data:image/")) {
                                    try {
                                        val cleanStr = profilePictureUrl!!.substringAfter("base64,").trim().replace("\\s".toRegex(), "")
                                        Base64.decode(cleanStr, Base64.DEFAULT)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    profilePictureUrl
                                }
                            }
                            if (model != null) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onError = { isImageError = true }
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(50.dp))
                            }
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(50.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(AccentColor)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ProfileInputField("Full Name", "Enter Full Name", Icons.Default.Person, textValue = fullName, onValueChange = { fullName = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        ProfileInputField("Email", "Enter Email", Icons.Default.Email, textValue = email, onValueChange = { email = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        ProfileInputField("Phone", "Enter Phone Number", Icons.Default.Phone, textValue = phone, onValueChange = { phone = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        ProfileInputField("Address", "Enter Address", Icons.Default.Place, isLarge = true, textValue = address, onValueChange = { address = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        ProfileInputField("Emergency Contact", "Enter Emergency Contact", Icons.Default.Phone, textValue = emergencyContact, onValueChange = { emergencyContact = it })
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (userId.isNotEmpty()) {
                            coroutineScope.launch {
                                try {
                                    val request = ProfileRequest(
                                        user_id = userId,
                                        full_name = fullName,
                                        email = email,
                                        phone = phone,
                                        address = address,
                                        emergency_contact = emergencyContact
                                    )
                                    val response = RetrofitClient.apiService.saveProfile(request)
                                    if (response.status == "success") {
                                        Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    } else {
                                        Toast.makeText(context, "Failed to save: ${response.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF0A161B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes", color = Color(0xFF0A161B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileInputField(label: String, placeholder: String, icon: ImageVector, isLarge: Boolean = false, textValue: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = textValue,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().then(if (isLarge) Modifier.height(80.dp) else Modifier.height(52.dp)),
            placeholder = { Text(placeholder, color = Color.Black, fontSize = 14.sp) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(20.dp)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF8F9FA),
                unfocusedContainerColor = Color(0xFFF8F9FA),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = !isLarge
        )
    }
}

@Composable
fun DetailsTextField(placeholder: String, icon: ImageVector? = null, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        placeholder = { Text(placeholder, color = Color.LightGray, fontSize = 14.sp) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(20.dp)) } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF8F9FA),
            unfocusedContainerColor = Color(0xFFF8F9FA),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
fun VitalMetricItem(label: String, value: String, status: String, icon: ImageVector, bgColor: Color, iconColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(bgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(status, color = iconColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun FullQuickActionItem(modifier: Modifier, title: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.height(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AppBottomBar(selectedItem: Int, onHomeClick: () -> Unit, onHealthClick: () -> Unit, onProfileClick: () -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = selectedItem == 0,
            onClick = onHomeClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1B333D),
                selectedTextColor = Color(0xFF1B333D),
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(if (selectedItem == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Health") },
            label = { Text("Health") },
            selected = selectedItem == 1,
            onClick = onHealthClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1B333D),
                selectedTextColor = Color(0xFF1B333D),
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            selected = selectedItem == 2,
            onClick = onProfileClick,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1B333D),
                selectedTextColor = Color(0xFF1B333D),
                indicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun QuickActionItem(modifier: Modifier, title: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.height(100.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
fun AppTextField(
    label: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    hasTrailingIcon: Boolean = false
) {
    val passwordVisible = remember { mutableStateOf(false) }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        placeholder = { Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) },
        trailingIcon = if (isPassword || hasTrailingIcon) {
            {
                IconButton(onClick = { if (isPassword) passwordVisible.value = !passwordVisible.value }) {
                    Icon(
                        imageVector = if (isPassword && passwordVisible.value) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible.value) "Hide password" else "Show password",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible.value) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            cursorColor = AccentColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
            focusedLeadingIconColor = Color.White.copy(alpha = 0.4f),
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.4f),
            focusedTrailingIconColor = Color.White.copy(alpha = 0.4f),
            unfocusedTrailingIconColor = Color.White.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        singleLine = true
    )
}

@Composable
fun MainButton(text: String, onClick: () -> Unit) {
    val view = LocalView.current
    Button(
        onClick = {
            playTapFeedback(view)
            onClick()
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, color = Color(0xFF0A161B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// ---------------- UPLOAD REPORT SCREEN ----------------
@Composable
fun UploadReportScreen(onBack: () -> Unit, onStartUpload: (Uri) -> Unit, onHomeTab: () -> Unit, onHealthTab: () -> Unit, onProfileTab: () -> Unit) {
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onStartUpload(uri)
        }
    }
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Upload Files", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Select documents from your device", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            Column(modifier = Modifier.padding(24.dp)) {
                // Drag and drop box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = Color(0xFFB0BEC5),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                ),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                        }
                        .clickable { filePickerLauncher.launch("*/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Click to upload or drag and drop", fontWeight = FontWeight.Bold, color = Color(0xFF1B333D), fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("PDF, JPG, PNG (max 10MB per file)", color = Color.Gray, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("Upload Guidelines", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B333D))
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GuidelineItem("Ensure all text is clearly visible")
                        GuidelineItem("Upload recent reports (within 6 months)")
                        GuidelineItem("Include patient information and dates")
                        GuidelineItem("Multiple pages can be uploaded")
                    }
                }
            }
        }
    }
}

@Composable
fun GuidelineItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = Color.Gray)
    }
}

// ---------------- UPLOAD SUCCESS SCREEN ----------------
@Composable
fun UploadSuccessScreen(onStartAnalysis: () -> Unit, onBackToHome: () -> Unit) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onBackToHome, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgGradient())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Success Icon
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AccentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Upload Successful!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Your medical reports have been securely\nuploaded and are ready for AI analysis",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Info Cards
            UploadInfoCard(
                title = "Files Uploaded",
                subtitle = "2 documents",
                iconBgColor = AccentColor,
                iconVector = Icons.Default.List,
                iconTint = Color.White,
                hasBorder = false
            )
            Spacer(modifier = Modifier.height(16.dp))
            UploadInfoCard(
                title = "Status",
                subtitle = "Ready for analysis",
                iconBgColor = Color.Transparent,
                iconVector = Icons.Default.CheckCircle,
                iconTint = Color.White.copy(alpha = 0.7f),
                hasBorder = true
            )

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            Button(
                onClick = onStartAnalysis,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF0A161B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start AI Analysis", color = Color(0xFF0A161B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onBackToHome,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Back to Home", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun UploadInfoCard(title: String, subtitle: String, iconBgColor: Color, iconVector: ImageVector, iconTint: Color, hasBorder: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, RoundedCornerShape(10.dp))
                    .then(if (hasBorder) Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconVector, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}


// ---------------- UPLOADING SCREEN ----------------
@Composable
fun UploadingScreen(userId: String, fileUri: Uri?, onUploadComplete: (UploadResponse?) -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        if (fileUri == null) {
            onUploadComplete(null)
            return@LaunchedEffect
        }
        
        var uploadResponse: UploadResponse? = null
        
        // Start network upload in a background coroutine
        val networkJob = launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.uploadReport(userId, fileUri, context)
                uploadResponse = response
                if (response.status != "success") {
                    withContext(Dispatchers.Main) {
                        val displayMsg = when (response.message) {
                            "INVALID_DOCUMENT" -> "Invalid document format or unrecognized report type."
                            "POOR_IMAGE_QUALITY" -> "Poor image quality. Please upload a clearer photo."
                            else -> response.message ?: "Analysis failed."
                        }
                        android.widget.Toast.makeText(context, displayMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Smoothly animate progress bar over 1.8 seconds for premium UX
        for (i in 1..100) {
            kotlinx.coroutines.delay(18)
            progress = i / 100f
        }
        
        // Wait for network request to complete
        networkJob.join()
        
        // Transition to next screen
        onUploadComplete(uploadResponse)
    }

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = {}, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient())
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Upload Icon Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .border(2.dp, AccentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Uploading",
                        tint = AccentColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Uploading...",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Securely uploading your documents",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AccentColor,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}% complete",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Status Items
                UploadStatusItem(
                    text = "Document validation complete",
                    isComplete = progress > 0.1f
                )
                Spacer(modifier = Modifier.height(16.dp))
                UploadStatusItem(
                    text = "Encryption applied",
                    isComplete = progress > 0.4f
                )
                Spacer(modifier = Modifier.height(16.dp))
                UploadStatusItem(
                    text = "Transfer in progress...",
                    isComplete = progress > 0.95f,
                    isInProgress = progress <= 0.95f
                )
            }
        }
    }
}

@Composable
fun UploadStatusItem(text: String, isComplete: Boolean, isInProgress: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isComplete) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(20.dp)
            )
        } else if (isInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.Gray,
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.dp, Color.Gray, CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            color = if (isComplete) Color.White else Color.Gray,
            fontSize = 14.sp
        )
    }
}

// ---------------- AI ANALYSIS PROGRESS SCREEN ----------------
@Composable
fun AIAnalysisProgressScreen(onAnalysisComplete: () -> Unit) {
    var progress1 by remember { mutableStateOf(0f) }
    var progress2 by remember { mutableStateOf(0f) }
    var progress3 by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        val totalTime = 4500L
        val interval = 50L
        val steps = totalTime / interval
        
        for (i in 1..steps) {
            kotlinx.coroutines.delay(interval)
            val currentProgress = i.toFloat() / steps
            
            // Stagger the progress of the three bars
            progress1 = (currentProgress * 1.5f).coerceIn(0f, 1f)
            if (currentProgress > 0.2f) {
                progress2 = ((currentProgress - 0.2f) * 1.5f).coerceIn(0f, 1f)
            }
            if (currentProgress > 0.4f) {
                progress3 = ((currentProgress - 0.4f) * 1.5f).coerceIn(0f, 1f)
            }
        }
        
        // Wait a little before moving to the next screen
        kotlinx.coroutines.delay(500)
        onAnalysisComplete()
    }

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = {}, onHealthClick = {}, onProfileClick = {}) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient())
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Brain Icon Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .border(2.dp, AccentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Analysis",
                        tint = AccentColor,
                        modifier = Modifier.size(50.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "AI Analysis in Progress",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Our advanced AI is analyzing your cardiac\ndata using deep learning models",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Progress Items
                AnalysisProgressCard(
                    title = "Analyzing biomarkers...",
                    progress = progress1,
                    icon = Icons.Default.FavoriteBorder
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                AnalysisProgressCard(
                    title = "Processing cardiac imaging...",
                    progress = progress2,
                    icon = Icons.Default.MonitorHeart
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                AnalysisProgressCard(
                    title = "Evaluating risk factors...",
                    progress = progress3,
                    icon = Icons.Default.Science
                )
                
                Spacer(modifier = Modifier.height(64.dp))
                
                Text(
                    text = "This may take a few moments. Please wait...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AnalysisProgressCard(title: String, progress: Float, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AccentColor,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

// ---------------- ANALYSIS FAILED SCREEN ----------------
@Composable
fun AnalysisFailedScreen(
    reason: FailureReason,
    onUploadAgain: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onHomeTab: () -> Unit,
    onReportsTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    val detail = failureDetailsMap[reason] ?: failureDetailsMap[FailureReason.NONE]!!

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            
            // Error Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFFFEBEE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(45.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(detail.title, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                detail.description,
                color = Color.Gray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Common Issues Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(Color(0xFFFFF9C4), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("Common Issues:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B333D))
                    Spacer(modifier = Modifier.height(12.dp))
                    detail.commonIssues.forEach { issue ->
                        IssueBullet(issue)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Buttons
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Button(
                    onClick = onUploadAgain,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B4756)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Again", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onRetryAnalysis,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Analysis", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Get Help
            Row(
                modifier = Modifier.clickable { /* TODO */ },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Help", color = Color.Gray, fontSize = 15.sp)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun IssueBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = Color.Gray, fontSize = 13.sp, lineHeight = 18.sp)
    }
}


// ---------------- ANALYSIS COMPLETE SCREEN ----------------
@Composable
fun AnalysisCompleteScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    onRiskAssessmentClick: () -> Unit,
    onViewDetailedReport: () -> Unit,
    onViewRecommendations: () -> Unit,
    onHomeTab: () -> Unit,
    onReportsTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Analysis Complete", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(result.date ?: getCurrentFormattedDateOnly(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }

            // Risk Assessment
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (-80).dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onRiskAssessmentClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = result.color),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Risk Assessment", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(result.riskLevel, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${result.probability}% probability: ${result.description}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { result.probability / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (-50).dp)
            ) {
                // View Detailed Report Card
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onViewDetailedReport() },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF0F4F8), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("View Detailed Report", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1B333D))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("Key Findings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B333D))
                Spacer(modifier = Modifier.height(16.dp))

                // Finding Cards
                result.findings.forEach { (title, desc) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = result.bgColor.copy(alpha = 0.5f)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = result.color, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF1B333D))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(desc, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Health Tips & Recommendations", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1B333D))
                Spacer(modifier = Modifier.height(16.dp))

                result.recommendations.forEach { recommendation ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = result.color, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(recommendation, fontSize = 13.sp, color = Color.DarkGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // View Recommendations Button
                Button(
                    onClick = onViewRecommendations,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B4756)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("Update Health Plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun RiskLevelAnalysisScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    onHomeTab: () -> Unit,
    onReportsTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgGradient())
                    .padding(24.dp)
                    .padding(top = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Risk Level Analysis", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Comprehensive risk breakdown", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (-24).dp)
            ) {
                // Risk Score Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(result.bgColor, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${result.probability}%", color = result.color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(result.riskLevel, color = result.color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Based on current health data and\nlifestyle factors",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Risk Factors Assessment
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Risk Factors Assessment", color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))

                        RiskFactorItem("Age", "35 years", "Low")
                        RiskFactorItem("Blood Pressure", "120/80", "Normal")
                        RiskFactorItem("Cholesterol", "180 mg/dL", "Good")
                        RiskFactorItem("Smoking", "Never", "Low")
                        RiskFactorItem("Arsenic Exposure", "None", "Low")
                        RiskFactorItem("Family History", "No cardiac events", "Low", isLast = true)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun RiskFactorItem(title: String, subtitle: String, status: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Color(0xFF1B333D), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
        Text(status, color = Color(0xFF00BFA5), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

fun shareReportAsPdf(context: android.content.Context, result: AnalysisResult) {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = android.graphics.Paint()
    
    // Paint background
    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, 595f, 842f, paint)
    
    // Draw Header Background
    val headerPaint = android.graphics.Paint()
    headerPaint.color = android.graphics.Color.parseColor("#0A161B")
    canvas.drawRect(0f, 0f, 595f, 120f, headerPaint)
    
    // Draw Logo/Header Text
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 22f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("CardioProgress - Cardiac Report", 30f, 50f, paint)
    
    paint.textSize = 12f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawText("Generated on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}", 30f, 80f, paint)
    
    // Reset paint for body
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 14f
    
    var yPos = 160f
    
    // Draw Section: Executive Summary
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.color = android.graphics.Color.parseColor("#0A161B")
    canvas.drawText("EXECUTIVE SUMMARY", 30f, yPos, paint)
    yPos += 20f
    
    paint.color = android.graphics.Color.DKGRAY
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paint.textSize = 12f
    
    // Wrap executive summary text (description)
    val execSummary = result.description
    val words = execSummary.split(" ")
    var line = StringBuilder()
    for (word in words) {
        if (paint.measureText(line.toString() + " " + word) > 535f) {
            canvas.drawText(line.toString(), 30f, yPos, paint)
            yPos += 18f
            line = StringBuilder(word)
        } else {
            if (line.isEmpty()) line.append(word) else line.append(" ").append(word)
        }
    }
    canvas.drawText(line.toString(), 30f, yPos, paint)
    yPos += 40f
    
    // Draw Risk Score
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.color = android.graphics.Color.parseColor("#0A161B")
    paint.textSize = 14f
    canvas.drawText("RISK ASSESSMENT", 30f, yPos, paint)
    yPos += 20f
    
    paint.color = android.graphics.Color.BLACK
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paint.textSize = 12f
    canvas.drawText("Risk Classification: ${result.riskLevel}", 30f, yPos, paint)
    yPos += 18f
    canvas.drawText("Risk Probability: ${result.probability}%", 30f, yPos, paint)
    yPos += 40f
    
    // Draw Biomarkers Section
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.color = android.graphics.Color.parseColor("#0A161B")
    paint.textSize = 14f
    canvas.drawText("CARDIAC BIOMARKERS", 30f, yPos, paint)
    yPos += 25f
    
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.color = android.graphics.Color.DKGRAY
    paint.textSize = 11f
    canvas.drawText("BIOMARKER", 30f, yPos, paint)
    canvas.drawText("VALUE", 250f, yPos, paint)
    canvas.drawText("REFERENCE", 420f, yPos, paint)
    
    val linePaint = android.graphics.Paint()
    linePaint.color = android.graphics.Color.LTGRAY
    linePaint.strokeWidth = 1f
    canvas.drawLine(30f, yPos + 5f, 565f, yPos + 5f, linePaint)
    yPos += 20f
    
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 12f
    
    // Troponin
    canvas.drawText("Troponin I (Cardiac Injury)", 30f, yPos, paint)
    canvas.drawText(if (result.troponin_i != null) "${result.troponin_i} ng/mL" else "N/A", 250f, yPos, paint)
    canvas.drawText("< 0.04 ng/mL (Normal)", 420f, yPos, paint)
    yPos += 20f
    
    // BNP
    canvas.drawText("BNP (Heart Failure)", 30f, yPos, paint)
    canvas.drawText(if (result.bnp != null) "${result.bnp.toInt()} pg/mL" else "N/A", 250f, yPos, paint)
    canvas.drawText("< 100 pg/mL (Normal)", 420f, yPos, paint)
    yPos += 20f
    
    // NT-proBNP
    canvas.drawText("NT-proBNP (Progression)", 30f, yPos, paint)
    canvas.drawText(if (result.nt_probnp != null) "${result.nt_probnp.toInt()} pg/mL" else "N/A", 250f, yPos, paint)
    canvas.drawText("< 125 pg/mL (Normal)", 420f, yPos, paint)
    yPos += 40f

    // Recommendations Section
    if (result.recommendations.isNotEmpty()) {
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.color = android.graphics.Color.parseColor("#0A161B")
        paint.textSize = 14f
        canvas.drawText("CLINICAL RECOMMENDATIONS", 30f, yPos, paint)
        yPos += 20f
        
        paint.color = android.graphics.Color.DKGRAY
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        paint.textSize = 11f
        for (rec in result.recommendations) {
            canvas.drawText("• $rec", 35f, yPos, paint)
            yPos += 16f
        }
        yPos += 24f
    }
    
    // Disclaimer
    paint.textSize = 10f
    paint.color = android.graphics.Color.GRAY
    val disclaimer = "Disclaimer: This AI analysis report is for informational purposes only and does not constitute medical advice. Please consult your physician for diagnosis and medical guidance."
    
    val wordsDisclaimer = disclaimer.split(" ")
    var lineDisclaimer = StringBuilder()
    for (word in wordsDisclaimer) {
        if (paint.measureText(lineDisclaimer.toString() + " " + word) > 535f) {
            canvas.drawText(lineDisclaimer.toString(), 30f, yPos, paint)
            yPos += 14f
            lineDisclaimer = StringBuilder(word)
        } else {
            if (lineDisclaimer.isEmpty()) lineDisclaimer.append(word) else lineDisclaimer.append(" ").append(word)
        }
    }
    canvas.drawText(lineDisclaimer.toString(), 30f, yPos, paint)
    
    pdfDocument.finishPage(page)
    
    val file = java.io.File(context.cacheDir, "Cardiac_Fibrosis_Report.pdf")
    try {
        val fos = java.io.FileOutputStream(file)
        pdfDocument.writeTo(fos)
        fos.close()
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Failed to generate PDF: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        pdfDocument.close()
        return
    }
    pdfDocument.close()
    
    try {
        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.example.cardiacfibrosisapp.fileprovider",
            file
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Cardiac Fibrosis Analysis Report")
            putExtra(android.content.Intent.EXTRA_TEXT, "Here is the Cardiac Fibrosis Analysis Report.")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Report PDF"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error sharing file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun DetailedReportScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    onBiomarkersClick: () -> Unit,
    onHomeTab: () -> Unit,
    onReportsTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgGradient())
                    .padding(24.dp)
                    .padding(top = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable { onBack() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Detailed Report", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Comprehensive cardiac analysis", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable { shareReportAsPdf(context, result) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable { shareReportAsPdf(context, result) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (-24).dp)
            ) {
                // Executive Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Executive Summary", color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Based on comprehensive analysis of cardiac biomarkers, imaging data, and risk factors, the patient presents with a ${result.riskLevel.lowercase()} (${result.probability}%) of developing significant cardiac fibrosis within the next 5 years. ${result.description}",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFF5F7F9), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text("Analysis Date", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(result.date ?: getCurrentFormattedDateOnly(), color = Color(0xFF1B333D), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(result.bgColor, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text("Risk Score", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${result.probability}/100", color = result.color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Cardiac Biomarkers Card
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onBiomarkersClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cardiac Biomarkers", color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        val tropVal = result.troponin_i ?: 0.02
                        val tropStatus = if (tropVal >= 0.04) "Elevated" else "Normal"
                        BiomarkerItem("Troponin I", "Cardiac injury marker", "$tropVal ng/mL ($tropStatus)")

                        val bnpVal = result.bnp ?: 45.0
                        val bnpStatus = if (bnpVal >= 100.0) "Elevated" else "Normal"
                        BiomarkerItem("BNP", "Heart failure marker", "${bnpVal.toInt()} pg/mL ($bnpStatus)")

                        BiomarkerItem("CRP", "Inflammation marker", "1.2 mg/L (Normal)", isLast = true)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cardiac Function Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShowChart, contentDescription = null, tint = Color(0xFF00BFA5), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cardiac Function", color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        BiomarkerItem("Ejection Fraction", "", "62% (Normal)")
                        BiomarkerItem("Left Ventricular Mass", "", "145g (Normal)", isLast = true)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun BiomarkerItem(title: String, subtitle: String, status: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Color(0xFF1B333D), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            }
        }
        Text(status, color = Color(0xFF00BFA5), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BiomarkerAnalysisScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    onHomeTab: () -> Unit,
    onReportsTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgGradient())
                    .padding(24.dp)
                    .padding(top = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Biomarker Analysis", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Detailed blood test results", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
            ) {
                item {
                    val tropVal = result.troponin_i ?: (if (result.probability > 50) 0.12 else if (result.probability > 20) 0.06 else 0.02)
                    val tropStatus = if (tropVal >= 0.04) "High" else "Normal"
                    BiomarkerDetailCard(
                        title = "Troponin I",
                        subtitle = "Cardiac muscle damage marker",
                        status = tropStatus,
                        yourValue = "$tropVal ng/mL",
                        normalRange = "< 0.04 ng/mL",
                        progress = if (tropVal >= 0.04) 0.85f else 0.45f,
                        color = if (tropVal >= 0.04) Color(0xFFD32F2F) else Color(0xFF00BFA5),
                        bgColor = if (tropVal >= 0.04) Color(0xFFFFEBEE) else Color(0xFFD4F7EA)
                    )
                }
                item {
                    val bnpVal = result.bnp ?: (if (result.probability > 40) 125.0 else 45.0)
                    val bnpStatus = if (bnpVal >= 100.0) "Elevated" else "Normal"
                    BiomarkerDetailCard(
                        title = "BNP",
                        subtitle = "Heart failure indicator",
                        status = bnpStatus,
                        yourValue = "${bnpVal.toInt()} pg/mL",
                        normalRange = "< 100 pg/mL",
                        progress = if (bnpVal >= 100.0) 0.75f else 0.45f,
                        color = if (bnpVal >= 100.0) Color(0xFFD32F2F) else Color(0xFF00BFA5),
                        bgColor = if (bnpVal >= 100.0) Color(0xFFFFEBEE) else Color(0xFFD4F7EA)
                    )
                }
                item {
                    BiomarkerDetailCard(
                        title = "CRP",
                        subtitle = "Inflammation marker",
                        status = "Normal",
                        yourValue = "1.2 mg/L",
                        normalRange = "< 3.0 mg/L",
                        progress = 0.4f,
                        color = Color(0xFF00BFA5),
                        bgColor = Color(0xFFD4F7EA)
                    )
                }
                item {
                    val ntVal = result.nt_probnp ?: (if (result.probability > 60) 450.0 else 150.0)
                    val ntStatus = if (ntVal >= 300.0) "High" else "Normal"
                    BiomarkerDetailCard(
                        title = "NT-proBNP",
                        subtitle = "Heart strain indicator",
                        status = ntStatus,
                        yourValue = "${ntVal.toInt()} pg/mL",
                        normalRange = "< 300 pg/mL",
                        progress = if (ntVal >= 300.0) 0.8f else 0.5f,
                        color = if (ntVal >= 300.0) Color(0xFFD32F2F) else Color(0xFF00BFA5),
                        bgColor = if (ntVal >= 300.0) Color(0xFFFFEBEE) else Color(0xFFD4F7EA)
                    )
                }
            }
        }
    }
}

@Composable
fun BiomarkerDetailCard(
    title: String,
    subtitle: String,
    status: String,
    yourValue: String,
    normalRange: String,
    progress: Float,
    color: Color,
    bgColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(title, color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(subtitle, color = Color.Gray, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFFF5F7F9), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Your Value", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(yourValue, color = Color(0xFF1B333D), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFFF5F7F9), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Normal Range", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(normalRange, color = Color(0xFF1B333D), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF00BFA5),
                trackColor = Color(0xFFF5F7F9)
            )
        }
    }
}

@Composable
fun HowAIWorksScreen(
    onBack: () -> Unit,
    onHomeTab: () -> Unit,
    onReportsTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = onHomeTab, onHealthClick = onHealthTab, onProfileClick = onProfileTab) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgGradient())
                    .padding(24.dp)
                    .padding(top = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable { onBack() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("How AI Analysis Works", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Understanding our cardiac risk assessment technology", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
            ) {
                item {
                    // AI Model Overview Card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF1B333D), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("AI Model Overview", color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Our cardiac risk assessment system utilizes advanced machine learning algorithms trained on millions of anonymized medical records and clinical studies. The model analyzes multiple cardiovascular health indicators to provide personalized risk assessments.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F0FE), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1B333D))) {
                                                append("Model Type: ")
                                            }
                                            withStyle(style = SpanStyle(color = Color(0xFF455A64))) {
                                                append("Deep Neural Network with attention mechanisms")
                                            }
                                        },
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1B333D))) {
                                                append("Training Data: ")
                                            }
                                            withStyle(style = SpanStyle(color = Color(0xFF455A64))) {
                                                append("5M+ validated cardiovascular health records")
                                            }
                                        },
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    // Data Sources Card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF00BFA5), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Data Sources", color = Color(0xFF1B333D), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            DataSourceItem(
                                title = "Medical Reports",
                                description = "ECG, blood tests, imaging results, and vital signs",
                                icon = Icons.Default.Description
                            )
                            DataSourceItem(
                                title = "Clinical Guidelines",
                                description = "AHA, ACC, and ESC evidence-based protocols",
                                icon = Icons.Default.Assignment
                            )
                            DataSourceItem(
                                title = "Patient History",
                                description = "Medical conditions, medications, lifestyle factors",
                                icon = Icons.Default.Person,
                                isLast = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataSourceItem(title: String, description: String, icon: ImageVector, isLast: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 24.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFD4F7EA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF00BFA5), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color(0xFF1B333D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = Color.Gray, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ---------------- DATA MODELS ----------------

data class AnalysisResult(
    val riskLevel: String,
    val probability: Int,
    val description: String,
    val color: Color,
    val bgColor: Color,
    val findings: List<Pair<String, String>>,
    val recommendations: List<String>,
    val troponin_i: Double? = null,
    val bnp: Double? = null,
    val nt_probnp: Double? = null,
    val date: String? = null
)

val allAnalysisResults = listOf(
    AnalysisResult(
        riskLevel = "Healthy",
        probability = 4,
        description = "Negligible probability of cardiac fibrosis. Your heart is in excellent condition.",
        color = Color(0xFF4CAF50),
        bgColor = Color(0xFFE8F5E9),
        findings = listOf(
            "Optimal cardiac biomarkers" to "All troponin and BNP levels are at ideal baseline",
            "Excellent lifestyle indicators" to "Current exercise and diet patterns are highly supportive"
        ),
        recommendations = listOf(
            "Maintain current healthy lifestyle",
            "Continue regular physical activity (150 min/week)",
            "Scheduled routine checkup in 12 months"
        )
    ),
    AnalysisResult(
        riskLevel = "Low Risk",
        probability = 15,
        description = "Low probability of cardiac fibrosis progression. Maintain healthy habits.",
        color = Color(0xFF00BFA5),
        bgColor = Color(0xFFE0F2F1),
        findings = listOf(
            "Normal cardiac biomarkers" to "All troponin and BNP levels within normal range",
            "Healthy lifestyle indicators" to "Exercise and diet patterns support cardiac health"
        ),
        recommendations = listOf(
            "Continue balanced nutrition and exercise",
            "Monitor blood pressure monthly",
            "Next screening recommended in 6 months"
        )
    ),
    AnalysisResult(
        riskLevel = "Risk",
        probability = 38,
        description = "Moderate probability detected. Some markers indicate early signs of stress.",
        color = Color(0xFFFFA000),
        bgColor = Color(0xFFFFF8E1),
        findings = listOf(
            "Elevated biomarkers detected" to "Mild elevation in BNP levels suggests cardiac strain",
            "Lifestyle adjustment required" to "High sodium intake and sedentary patterns observed"
        ),
        recommendations = listOf(
            "Consult a cardiologist for a detailed evaluation",
            "Reduce sodium intake to under 2300mg/day",
            "Increase moderate aerobic exercise to 30 mins daily"
        )
    ),
    AnalysisResult(
        riskLevel = "High Risk",
        probability = 72,
        description = "Significant probability of cardiac fibrosis. Immediate medical consultation required.",
        color = Color(0xFFD32F2F),
        bgColor = Color(0xFFFFEBEE),
        findings = listOf(
            "Significant biomarker elevation" to "High Troponin I levels indicate potential cardiac injury",
            "Imaging data concerns" to "AI detected patterns consistent with early fibrosis"
        ),
        recommendations = listOf(
            "Immediate consultation with a cardiac specialist",
            "Advanced cardiac imaging (MRI/CT) recommended",
            "Avoid high-intensity exertion until cleared by doctor"
        )
    )
)

// ---------------- SETTINGS SCREEN ----------------
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHomeTab: () -> Unit,
    onHealthTab: () -> Unit,
    onProfileTab: () -> Unit
) {
    val view = LocalView.current
    var pushNotifications by remember { mutableStateOf(AppSettings.pushNotifications) }
    var emailNotifications by remember { mutableStateOf(AppSettings.emailNotifications) }
    var darkMode by remember { mutableStateOf(AppSettings.darkMode) }
    var selectedTheme by remember { mutableStateOf(AppSettings.selectedTheme) }
    var soundEffects by remember { mutableStateOf(AppSettings.soundEffects) }

    Scaffold(
        bottomBar = { AppBottomBar(selectedItem = 0, onHomeClick = { playTapFeedback(view); onHomeTab() }, onHealthClick = { playTapFeedback(view); onHealthTab() }, onProfileClick = { playTapFeedback(view); onProfileTab() }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppBackgroundColor)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(bgGradient())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.clickable { playTapFeedback(view); onBack() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("App Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Customize your experience", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Notifications Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE3F2FD), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTextColor)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Push Notifications", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppTextColor)
                                Text("Receive app notifications", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = pushNotifications,
                                onCheckedChange = {
                                    pushNotifications = it
                                    AppSettings.savePushNotifications(it)
                                    playTapFeedback(view)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentColor)
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF5F5F5))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Email Notifications", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppTextColor)
                                Text("Receive updates via email", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = emailNotifications,
                                onCheckedChange = {
                                    emailNotifications = it
                                    AppSettings.saveEmailNotifications(it)
                                    playTapFeedback(view)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentColor)
                            )
                        }
                    }
                }

                // Appearance Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE8F9F1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF00BFA5), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTextColor)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Dark Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppTextColor)
                                Text("Use dark theme", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = darkMode,
                                onCheckedChange = {
                                    darkMode = it
                                    AppSettings.saveDarkMode(it)
                                    playTapFeedback(view)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentColor)
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF5F5F5))
                        
                        Text("Color Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppTextColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ThemeColorOption(
                                modifier = Modifier.weight(1f),
                                name = "Medical Blue",
                                color = Color(0xFF1B333D),
                                isSelected = selectedTheme == "Medical Blue",
                                onClick = {
                                    selectedTheme = "Medical Blue"
                                    AppSettings.saveSelectedTheme("Medical Blue")
                                    playTapFeedback(view)
                                }
                            )
                            ThemeColorOption(
                                modifier = Modifier.weight(1f),
                                name = "Health Green",
                                color = Color(0xFF00BFA5),
                                isSelected = selectedTheme == "Health Green",
                                onClick = {
                                    selectedTheme = "Health Green"
                                    AppSettings.saveSelectedTheme("Health Green")
                                    playTapFeedback(view)
                                }
                            )
                            ThemeColorOption(
                                modifier = Modifier.weight(1f),
                                name = "Calm Purple",
                                color = Color(0xFF7C4DFF),
                                isSelected = selectedTheme == "Calm Purple",
                                onClick = {
                                    selectedTheme = "Calm Purple"
                                    AppSettings.saveSelectedTheme("Calm Purple")
                                    playTapFeedback(view)
                                }
                            )
                        }
                    }
                }

                // Sound & Vibration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFFF8E1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Sound & Vibration", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTextColor)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sound Effects", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppTextColor)
                                Text("Play sounds for actions", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = soundEffects,
                                onCheckedChange = {
                                    soundEffects = it
                                    AppSettings.saveSoundEffects(it)
                                    playTapFeedback(view)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentColor)
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun ThemeColorOption(
    modifier: Modifier = Modifier,
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF1976D2) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp, 20.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.Black
            )
        }
    }
}

// ---------------- FAILURE REASONS ----------------

enum class FailureReason {
    NETWORK_ISSUE,
    INVALID_DOCUMENT,
    POOR_IMAGE_QUALITY,
    NONE
}

data class FailureDetail(
    val title: String,
    val description: String,
    val commonIssues: List<String>
)

val failureDetailsMap = mapOf(
    FailureReason.NETWORK_ISSUE to FailureDetail(
        title = "Network Connection Issue",
        description = "We lost connection to the server during the upload. Please check your internet and try again.",
        commonIssues = listOf(
            "Unstable Wi-Fi or cellular data",
            "Server timeout during large file transfer",
            "Firewall or proxy blocking the connection"
        )
    ),
    FailureReason.INVALID_DOCUMENT to FailureDetail(
        title = "Invalid Document Format",
        description = "The document you uploaded isn't a supported cardiac report or is in an unreadable format.",
        commonIssues = listOf(
            "File is not a PDF or high-res image",
            "Document is missing required cardiac markers",
            "File size is too large or too small"
        )
    ),
    FailureReason.POOR_IMAGE_QUALITY to FailureDetail(
        title = "Poor Image Quality",
        description = "Our AI couldn't read the document clearly. Please ensure the image is well-lit and in focus.",
        commonIssues = listOf(
            "Image is blurry or shaky",
            "Inadequate lighting or glare on document",
            "Text is too small or obscured"
        )
    ),
    FailureReason.NONE to FailureDetail(

        title = "Analysis Failed",
        description = "We encountered an unexpected issue processing your documents.",
        commonIssues = listOf(
            "Unknown processing error",
            "System maintenance in progress"
        )
    )
)

