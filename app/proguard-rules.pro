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
# Keep ALL of kotlinx.serialization (incl. the json.internal stream reader/writer).
# Without this, R8 strips the streaming code paths and they throw
# "IllegalStateException: Not implemented" at runtime in release builds only.
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep our own class + method names so release crash traces are readable.
-keepnames class com.dionysus.tv.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# LibVLC (JNI-backed; keep everything it needs at runtime)
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**
