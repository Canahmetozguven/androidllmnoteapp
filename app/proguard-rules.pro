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
