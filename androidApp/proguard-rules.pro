# Orbit Money — R8 / ProGuard release rules

# Preserve readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization (models, API payloads, backup snapshots, navigation keys)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.overbay.orbit.**$$serializer { *; }
-keepclassmembers class app.overbay.orbit.** {
    *** Companion;
}
-keepclasseswithmembers class app.overbay.orbit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class app.overbay.orbit.** { *; }
-keepclassmembers enum app.overbay.orbit.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose Multiplatform generated resource accessors
-keep class orbitmoney.composeapp.generated.resources.** { *; }
