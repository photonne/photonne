# R8 rules for the Android release build.
#
# Most libraries ship their own consumer rules (kotlinx.serialization,
# coroutines, Ktor, OkHttp, Coil, Media3, WorkManager, SQLDelight, Koin), so
# this file only covers what they can't know about.

# ── App models ──────────────────────────────────────────────────────────────
# DTOs are (de)serialized by kotlinx.serialization and some are persisted as
# JSON in settings. The plugin's generated serializers are kept by the
# library's rules; keeping the models whole also protects names that end up
# in persisted data and in error reports.
-keep class com.photonne.app.data.models.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.photonne.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.photonne.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum names are persisted in settings and read back with valueOf().
-keepclassmembers enum com.photonne.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WorkManager instantiates workers by class name.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Optional / JVM-only dependencies referenced but absent on Android ──────
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**

# Keep line numbers so crash reports stay readable; the mapping file in
# build/outputs/mapping/release/ de-obfuscates them.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
