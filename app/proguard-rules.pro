-keepattributes *Annotation*, InnerClasses
# Line numbers stay, so traces in the in-app error log can be retraced with the build's mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.dexter.data.**$$serializer { *; }
-keepclassmembers class com.dexter.data.** { *** Companion; }
-keepclasseswithmembers class com.dexter.data.** { kotlinx.serialization.KSerializer serializer(...); }

# Release only: move every app class into one package and let R8 widen access, so it can inline and merge more.
-repackageclasses
-allowaccessmodification

# Kotlin checks every parameter and platform value for null at run time. The compiler already proves these,
# so release builds drop the checks. A `!!` still throws, since checkNotNull(Object) is left alone.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}

# Debug logging never reaches a release build. Errors still log.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
