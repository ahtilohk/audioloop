# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Line number info for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson: keep all fields in the app package for serialization
-keepclassmembers class com.example.audioloop.** {
    <fields>;
}

# Google Play Services Auth
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Glance widget classes
-keep class com.example.audioloop.widget.** { *; }

# MediaSession support library
-keep class android.support.v4.media.** { *; }
-dontwarn android.support.v4.media.**

# Room – keep Entity, DAO, and Database classes so KSP-generated code works in release
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keepclassmembers @androidx.room.Entity class * { <fields>; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose – suppress warnings from internal compiler stubs
-dontwarn androidx.compose.**

# Splash Screen API
-keep class androidx.core.splashscreen.** { *; }

# Window Size Class
-keep class androidx.compose.material3.windowsizeclass.** { *; }
