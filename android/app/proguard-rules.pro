# youtubedl-android: keep its classes and native bridge intact
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-dontwarn com.yausername.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.zkwokleung.backchannel.**$$serializer { *; }
-keepclassmembers class com.zkwokleung.backchannel.** { *** Companion; }
-keepclasseswithmembers class com.zkwokleung.backchannel.** { kotlinx.serialization.KSerializer serializer(...); }
