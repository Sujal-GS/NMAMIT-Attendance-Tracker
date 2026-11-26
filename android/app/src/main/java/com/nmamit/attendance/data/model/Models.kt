package com.nmamit.attendance.data.model

import com.google.gson.annotations.SerializedName
import kotlin.math.ceil
import kotlin.math.floor

// ---------------------------------------------------------------------------
// 1. Universities & Captcha
// ---------------------------------------------------------------------------
data class University(
    @SerializedName("funivcode") val univcode: String,
    @SerializedName("funivname") val univname: String
)

data class UniversitiesResponse(
    val success: Boolean,
    val universities: List<University>?
)

data class CaptchaResponse(
    val success: Boolean,
    val captcha: String,
    val captchaToken: String,
    val message: String? = null
)

// ---------------------------------------------------------------------------
// 2. Authentication
// ---------------------------------------------------------------------------
data class LoginRequest(
    val regno: String,
    val passwd: String,
    val captcha: String,
    val captchaToken: String,
    val univcode: String = "049",
    val isDemo: Boolean = false
)

data class LoginResponse(
    val success: Boolean,
    val sessionId: String?,
    val studentInfo: StudentInfo?,
    val isDemo: Boolean = false,
    val message: String? = null
)

data class StudentInfo(
    @SerializedName("fregno") val regno: String?,
    @SerializedName("fname") val name: String?,
    @SerializedName("fdegree") val degree: String?,
    @SerializedName("fdescpn") val branch: String?,
    @SerializedName("fexamname") val semester: String?,
    @SerializedName("photo") val photo: String? = null
)

data class SessionCheckResponse(
    val success: Boolean,
    val valid: Boolean = false,
    val student: StudentInfo? = null,
    val sessionExpired: Boolean = false,
    val message: String? = null
)

// ---------------------------------------------------------------------------
// 3. Attendance Summary (Subject-wise)
// ---------------------------------------------------------------------------
data class AttendanceSummaryResponse(
    val success: Boolean,
    @SerializedName("error_code") val errorCode: Int = 0,
    val data: List<SubjectAttendanceDto>?,
    val message: String? = null,
    val sessionExpired: Boolean = false
)

data class SubjectAttendanceDto(
    @SerializedName("fsubcode") val subCode: String,
    @SerializedName("fsubname") val subName: String,
    val conducted: String?,
    val attended: String?,
    @SerializedName("ftotalclass") val totalClass: String? = null,
    @SerializedName("fpresentclass") val presentClass: String? = null
) {
    val conductedInt: Int
        get() = (conducted ?: totalClass ?: "0").trim().toIntOrNull() ?: 0

    val attendedInt: Int
        get() = (attended ?: presentClass ?: "0").trim().toIntOrNull() ?: 0

    val missedInt: Int
        get() = maxOf(0, conductedInt - attendedInt)

    val percentage: Double
        get() = if (conductedInt > 0) (attendedInt.toDouble() / conductedInt) * 100.0 else 100.0

    fun bunkBudget(thresholdPercent: Double = 85.0): Int {
        if (conductedInt == 0) return 0
        val target = thresholdPercent / 100.0
        val maxMiss = (attendedInt / target) - conductedInt
        return maxOf(0, floor(maxMiss).toInt())
    }

    fun classesToRecover(thresholdPercent: Double = 85.0): Int {
        if (conductedInt == 0 || percentage >= thresholdPercent) return 0
        val target = thresholdPercent / 100.0
        val needed = ((target * conductedInt) - attendedInt) / (1.0 - target)
        return maxOf(1, ceil(needed).toInt())
    }
}

// ---------------------------------------------------------------------------
// 4. Daily & Monthly Attendance
// ---------------------------------------------------------------------------
data class DailyAttendanceRequest(
    val date: String // Format: YYYY-MM-DD
)

data class DailyAttendanceResponse(
    val success: Boolean,
    val date: String?,
    val data: List<DailyClassRecord>?,
    val message: String? = null,
    val sessionExpired: Boolean = false
)

data class DailyClassRecord(
    @SerializedName("fsubcode") val subCode: String? = null,
    @SerializedName("fsubname") val subName: String? = null,
    @SerializedName("fperiod") val period: String? = null,
    @SerializedName("fnoclass") val noClass: String? = null,
    @SerializedName("fpresent") val present: String? = null,
    @SerializedName("fteachname") val teacherName: String? = null
) {
    val hoursCount: Int
        get() = noClass?.trim()?.toIntOrNull() ?: 1

    val isPresent: Boolean
        get() {
            val v = present?.trim()?.lowercase() ?: ""
            return v == "1" || v == "p" || v == "present"
        }
}

data class MonthAttendanceRequest(
    val year: Int,
    val month: Int
)

data class DayAttendanceSummary(
    val conducted: Int = 0,
    val attended: Int = 0,
    val classes: List<DailyClassRecord>? = null,
    val error: Boolean? = null
)

data class MonthAttendanceResponse(
    val success: Boolean,
    val monthData: Map<String, DayAttendanceSummary>? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false
)

// ---------------------------------------------------------------------------
// 5. Internal Assessment (IA) Marks
// ---------------------------------------------------------------------------
data class IAMarksResponse(
    val success: Boolean,
    val data: List<IAMarkItem>? = null,
    val rawHtml: String? = null,
    val isDemo: Boolean = false,
    val message: String? = null,
    val sessionExpired: Boolean = false
)

data class IAMarkItem(
    val slNo: String? = null,
    val sem: String? = null,
    @SerializedName("subcode") val subcode: String? = null,
    @SerializedName("fsubcode") val fsubcode: String? = null,
    @SerializedName("subname") val subname: String? = null,
    @SerializedName("fsubname") val fsubname: String? = null,
    val mse1: String? = null,
    val mse2: String? = null,
    val la1: String? = null,
    val la2: String? = null,
    val la3: String? = null,
    val record: String? = null,
    val conduction: String? = null,
    val mseLab: String? = null,
    val finalMarks: String? = null,
    @SerializedName("ftotmarks") val ftotmarks: String? = null,
    val eligibility: String? = null
) {
    val displaySubCode: String
        get() = (subcode ?: fsubcode ?: "").trim()

    val displaySubName: String
        get() = (subname ?: fsubname ?: "").trim()

    val displayFinalMarks: String
        get() = (finalMarks ?: ftotmarks ?: "").trim()
}

// ---------------------------------------------------------------------------
// 6. Generic UI Result Wrapper
// ---------------------------------------------------------------------------
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val sessionExpired: Boolean = false) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
