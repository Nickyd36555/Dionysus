# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dionysus.tv.**$$serializer { *; }
-keepclassmembers class com.dionysus.tv.** {
    *** Companion;
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# LibVLC (JNI-backed; keep everything it needs at runtime)
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**
