# 📱 NMAMIT Attendance Tracker — Native Android App (Kotlin)

A native Android app built using **Kotlin** and **Jetpack Compose (Material 3)** that connects to your existing Express proxy backend (`server.js`) to display real-time student attendance, margins, danger alerts, and predictive what-if projections.

---

## 🏗️ Architecture & Features

- **Modern Tech Stack:**
  - **Jetpack Compose + Material 3:** Declarative UI with cyber-inspired dark aesthetics (`#0B0F19`, `#00E5FF`).
  - **Retrofit 2 + OkHttp:** Type-safe REST client with automatic `x-session-id` header injection via `AuthInterceptor`.
  - **Encrypted Session DataStore:** Secure local storage for token preservation across app launches.
  - **MVVM Architecture:** Clean separation of concerns with `StateFlow` and Coroutines.
- **Key Features:**
  - 🔑 **Captcha-Protected Portal Login:** Automatically loads and refreshes portal captchas.
  - 🧪 **One-Tap Demo Mode:** Test all features instantly without requiring university portal credentials.
  - 📊 **Hero Circular Progress Dial:** Visual gauge of total attendance percentage and conducted hours.
  - 🚨 **Danger Radar (<75%):** Automatically flags subjects under the mandatory university threshold and calculates exact recovery classes needed.
  - 🧮 **Bunk Budget Calculator:** Shows how many future classes you can safely miss while remaining above 75% or 85%.
  - 🔮 **What-If Simulator:** Interactive sliders allowing students to simulate attending or missing upcoming classes per course.

---

## 🚀 How to Run in Android Studio

### 1. Open the Project
1. Launch **Android Studio (Hedgehog, Iguana, Koala, Ladybug, or newer)**.
2. Select **Open** and choose this `android` folder (`c:\CORE\Side QUESTS\attendance tracker 2\android`).
3. Let Gradle sync project dependencies.

---

### 2. Configure Backend API Endpoint

Make sure your Node.js backend is running:
```bash
# In the root attendance tracker folder:
node server.js
```

Open `app/src/main/java/com/nmamit/attendance/data/remote/NetworkClient.kt` and set `baseUrl` according to your test environment:

```kotlin
// Option 1: Running on Android Studio Emulator
var baseUrl: String = "http://10.0.2.2:3050/"

// Option 2: Running on Physical Android Phone via USB / Wi-Fi
// (Replace with your computer's local Wi-Fi IPv4 address)
var baseUrl: String = "http://192.168.1.15:3050/"

// Option 3: Running against Production Deployed Backend (Vercel / Render)
var baseUrl: String = "https://your-attendance-tracker.vercel.app/"
```

> **Note:** Cleartext HTTP traffic is already pre-configured in `network_security_config.xml` so local development works smoothly without needing an SSL certificate.

---

### 3. Run the App
- Press **Shift + F10** or click the **Play** button in Android Studio.
- On the Login screen, click **"Explore Demo Mode"** to test with mock data, or sign in with your real USN and university portal password.

---

## 📂 Project Directory Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/nmamit/attendance/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── local/SessionManager.kt       # DataStore session manager
│   │   │   │   ├── model/Models.kt               # DTOs & Bunk/Recovery formulas
│   │   │   │   ├── remote/AttendanceApiService.kt# Retrofit REST interface
│   │   │   │   ├── remote/AuthInterceptor.kt     # Injects x-session-id
│   │   │   │   ├── remote/NetworkClient.kt       # Base URL & OkHttp config
│   │   │   │   └── repository/AttendanceRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── auth/LoginScreen.kt           # Captcha & Login UI
│   │   │   │   ├── auth/AuthViewModel.kt
│   │   │   │   ├── dashboard/DashboardScreen.kt  # Attendance list & radar
│   │   │   │   ├── dashboard/DashboardViewModel.kt
│   │   │   │   ├── dashboard/components/         # SubjectCard & Progress ring
│   │   │   │   ├── simulator/WhatIfSimulatorScreen.kt # Interactive sliders
│   │   │   │   ├── navigation/NavGraph.kt
│   │   │   │   └── theme/                        # Cyber dark palette & typography
│   │   └── res/                                  # Icons, strings, network security
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```
