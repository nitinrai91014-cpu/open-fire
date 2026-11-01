# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keep class com.blindtechnexus.openfire.** { *; }
-dontwarn com.blindtechnexus.openfire.**

# Keep Compose runtime & UI essentials
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

