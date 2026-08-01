# youtubedl-android: keep its classes and native bridge intact
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-dontwarn com.yausername.**

# Jackson (youtubedl-android parses yt-dlp output with it) resolves types reflectively;
# without these R8 rewrites classes it instantiates and init fails with
# "class … is not a concrete class".
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**

# commons-io / commons-compress unpack the bundled Python payload.
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.zkwokleung.backchannel.**$$serializer { *; }
-keepclassmembers class com.zkwokleung.backchannel.** { *** Companion; }
-keepclasseswithmembers class com.zkwokleung.backchannel.** { kotlinx.serialization.KSerializer serializer(...); }
