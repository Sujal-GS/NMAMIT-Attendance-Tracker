# Proguard / R8 rules for NMAMIT Attendance Tracker
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Gson models & SerializedName fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.nmamit.attendance.data.model.** { *; }

# Keep Retrofit service interface & OkHttp internals
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.nmamit.attendance.data.remote.AttendanceApiService { *; }

-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**
