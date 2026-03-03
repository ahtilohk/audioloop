# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Gson serialized classes (used by PracticeStatsManager, PlaylistManager)
-keepclassmembers class com.example.audioloop.** {
    <fields>;
}

# Keep Google Play Services Auth
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep Glance widget classes
-keep class com.example.audioloop.widget.** { *; }

# Keep MediaSession callback classes
-keep class android.support.v4.media.** { *; }
-dontwarn android.support.v4.media.**