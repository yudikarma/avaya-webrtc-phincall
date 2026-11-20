# Keep Avaya WebRTC Connect SDK, WebRTC/CSDK JNI bridges, Netty WebSocket/codec generics, SLF4J, Jackson POJOs, and EventBus subscribers intact.
-keep class com.avaya.** { *; }
-keep class org.webrtc.** { *; }
-keep class io.netty.** { *; }
-keep class org.slf4j.** { *; }
-keep class org.greenrobot.eventbus.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class com.phincall.webrtc.auth.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

# Strip verbose/debug/info Logcat calls in release builds to prevent runtime data leaks.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.webrtc.**
