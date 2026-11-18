# Keep Avaya WebRTC Connect SDK, CSDK JNI bridges, and EventBus subscribers intact.
-keep class com.avaya.ocs.** { *; }
-keep class com.avaya.callprovider.** { *; }
-keep class com.avaya.clientservices.** { *; }
-keep class com.avaya.media.** { *; }
-keep class com.phincall.webrtc.auth.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn com.fasterxml.jackson.**
