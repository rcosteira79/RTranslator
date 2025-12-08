# Consumer ProGuard rules for rtranslator-lib
# These rules will be automatically applied to apps that depend on this library

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

