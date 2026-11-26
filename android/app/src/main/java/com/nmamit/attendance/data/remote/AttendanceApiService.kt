package com.nmamit.attendance.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Direct HTTPS client interface for https://studentportal.universitysolutions.in/
 * Mirrors the exact university portal PHP endpoints so the APK works standalone
 * on any phone without requiring a local proxy server.
 */
interface AttendanceApiService {

    @GET("get_captcha.php")
    suspend fun getCaptcha(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("signin.php")
    suspend fun signIn(
        @Field("regno") regno: String,
        @Field("passwd") passwd: String,
        @Field("captcha") captcha: String
    ): Response<ResponseBody>

    @POST("src/profile.php")
    suspend fun getProfile(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("app.php?a=viewAttendanceDetsummary")
    suspend fun getAttendanceSummary(
        @Query("univcode") univcode: String,
        @Field("date") date: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("app.php?a=viewAttendanceDet")
    suspend fun getDailyAttendance(
        @Query("univcode") univcode: String,
        @Field("date") date: String,
        @Field("regno") regno: String
    ): Response<ResponseBody>

    @GET("app.php?a=viewIaMarksNew")
    suspend fun getIaMarksNew(
        @Query("univcode") univcode: String
    ): Response<ResponseBody>

    @GET("app.php?a=elementWiseMarks")
    suspend fun getElementWiseMarks(
        @Query("univcode") univcode: String
    ): Response<ResponseBody>
}
