# ============================================================
# MixTape MP3 - regras de keep para o build minificado
# Validar com: ./gradlew :app:assembleMinified
# ============================================================

-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable

# --- JNI: encoder LAME (resolvido por nome de classe/metodo) ---
-keep class com.cdvirtual.app.media.encoder.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# --- NewPipeExtractor: reflection, Rhino (JS) e parse do player ---
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.github.** { *; }
-dontwarn org.schabi.newpipe.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**
-dontwarn com.github.**

# --- Media3 / ExoPlayer ---
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class * extends androidx.media3.session.MediaLibraryService { *; }
-dontwarn androidx.media3.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# --- Coroutines ---
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- OkHttp / Okio / Guava / Conscrypt ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.common.**
-dontwarn org.conscrypt.**

# --- Compose ---
-dontwarn androidx.compose.**

# --- App: dominio de video resolvido por nome ---
-keep class com.cdvirtual.app.feature.youtube.** { *; }
