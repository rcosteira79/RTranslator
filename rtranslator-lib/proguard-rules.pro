# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep SentencePiece JNI interface
-keep class com.rtranslator.translation.internal.SentencePieceProcessorJava {
    native <methods>;
}

# Keep public API
-keep public class com.rtranslator.** {
    public *;
}

# Keep data classes
-keepclassmembers class com.rtranslator.** {
    public <init>(...);
}

