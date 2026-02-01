# Synapse Notes ProGuard Rules

# Keep native methods and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI classes - Explicitly keep LlamaContext and its members
-keep class com.synapsenotes.ai.core.ai.LlamaContext {
    native <methods>;
    <init>(...);
    *;
}

# Keep Callback Interface and methods (prevent stripping of onToken)
-keep interface com.synapsenotes.ai.core.ai.LlmCallback {
    public void onToken(java.lang.String);
}

# Keep LLM context interfaces/impls explicitly (interface + implementation)
-keep interface com.synapsenotes.ai.core.ai.LlmContext { *; }
-keep class com.synapsenotes.ai.core.ai.DefaultLlmContext { *; }

# Keep everything in the AI package to be safe
-keep class com.synapsenotes.ai.core.ai.** { *; }

# Keep HardwareCapabilityProvider specifically if it's being used for feature detection
-keep class com.synapsenotes.ai.core.ai.HardwareCapabilityProvider { *; }


# Hilt/Dagger rules
-keep class dagger.hilt.android.internal.** { *; }
-keep interface dagger.hilt.EntryPoint { *; }

# Room rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit/OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod

# Markdown rendering
-dontwarn com.mikepenz.markdown.**

# Google Drive API / reflection rules
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.json.gson.** { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontwarn com.google.api.client.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**

# Apache HTTP Client / R8 Fixes
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**
-dontwarn javax.naming.ldap.**
-dontwarn org.ietf.jgss.**
