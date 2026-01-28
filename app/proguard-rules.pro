# Synapse Notes ProGuard Rules

# Keep native methods and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI classes
-keep class com.synapsenotes.ai.core.ai.** { *; }
-keep class com.synapsenotes.ai.core.ai.** { *; }

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
