# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================================
#  Strip logging verbose/debug/info dari release build
# ============================================================================
# ChatFin mencetak data keuangan ke logcat lewat Log.d (jumlah transaksi,
# nama model, status sync). Tanpa aturan ini semua Log.* ikut terkompilasi ke
# release dan bisa dibaca aplikasi lain / adb logcat.
#
# Log.w dan Log.e SENGAJA dipertahankan — dibutuhkan untuk diagnosa crash.
# Konsekuensinya: jangan pernah menaruh nilai finansial atau API key di Log.w/e.
#
# Catatan: -assumenosideeffects hanya bekerja saat optimisasi aktif. Build ini
# memakai proguard-android-optimize.txt, jadi syaratnya terpenuhi.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# ============================================================================
#  OkHttp + kotlinx.serialization (panggilan REST Gemini, ai/GeminiClient.kt)
# ============================================================================
# Sebelumnya dependency ini masuk transitif lewat SDK generativeai yang sudah
# membawa consumer-rules sendiri. Sejak migrasi ke OkHttp langsung, project ini
# butuh aturan sendiri supaya release build (isMinifyEnabled=true) tidak diam-
# diam merusak serialisasi DTO Gemini atau memicu warning R8 pada kelas
# opsional yang direferensikan OkHttp (provider TLS yang tidak dipakai di
# Android — Conscrypt/BouncyCastle/OpenJSSE).
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.sndiy.chatfin.ai.Gemini* { *; }
-keep,includedescriptorclasses class com.sndiy.chatfin.ai.Gemini*$serializer { *; }