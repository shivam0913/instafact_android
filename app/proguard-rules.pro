# R8 / ProGuard rules for release builds.
#
# The API layer is Retrofit + Gson with NO @SerializedName annotations: every field name
# is derived from the Kotlin property name via LOWER_CASE_WITH_UNDERSCORES (see
# NetworkModule). That mapping is done reflectively at runtime, so R8 cannot see it.
# Without the rules below R8 renames `refreshToken` to `a` and strips response classes
# it thinks nothing constructs - the app then sends {"a": "..."} and parses every
# response into nulls. It compiles and installs fine; it just cannot talk to the server.

# ---------------------------------------------------------------------------
# Attributes Retrofit and Gson need at runtime
# ---------------------------------------------------------------------------
# Signature: Retrofit reads the generic parameter of suspend fun/Call<T> to pick a
# converter. Without it: "Method return type must not include a type variable".
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
# Keep line numbers so Play Console stack traces stay readable after deobfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# API models - the critical rule
# ---------------------------------------------------------------------------
# Field names ARE the wire format here, so neither the classes nor their fields may be
# renamed or stripped. Gson instantiates these, which R8 reads as "never constructed".
-keep class com.instafact.app.data.model.** { *; }
-keepclassmembers class com.instafact.app.data.model.** { <fields>; }

# Enums crossing the wire (FeedbackType) serialize by constant name.
-keepclassmembers enum com.instafact.app.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Locally persisted models that are read back by name.
-keep class com.instafact.app.utils.ClientVideoMetadata { *; }
-keep class com.instafact.app.utils.NotificationRecord { *; }

# ---------------------------------------------------------------------------
# Retrofit
# ---------------------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# The service interface's annotations and method signatures drive the whole HTTP layer.
-keep,allowobfuscation interface com.instafact.app.data.api.ApiService
-keepclassmembers,allowobfuscation interface com.instafact.app.data.api.ApiService { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ---------------------------------------------------------------------------
# Gson
# ---------------------------------------------------------------------------
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------------------------------------------------------------------------
# OkHttp
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Firebase / Play services
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---------------------------------------------------------------------------
# App entry points referenced by name from the manifest or layouts
# ---------------------------------------------------------------------------
-keep class com.instafact.app.InstafactApplication { *; }
-keep class com.instafact.app.fcm.InstafactFirebaseMessagingService { *; }
# Custom views are inflated reflectively from XML by their fully-qualified name.
-keep class com.instafact.app.ui.detail.ConfidenceGaugeView { *; }
-keep class com.instafact.app.ui.walkthrough.FeatheredImageView { *; }
-keep class com.instafact.app.utils.ShimmerView { *; }
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------------------------------------------------------------------------
# Markwon / commonmark
# ---------------------------------------------------------------------------
-dontwarn io.noties.markwon.**
-dontwarn org.commonmark.**

# ---------------------------------------------------------------------------
# Strip the session debug log from release builds.
# It prints auth tokens, refresh tokens and phone numbers to logcat.
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
