# Signal protocol native bindings
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }

# Room
-keep class cz.kuclab.hertzchat.data.db.** { *; }

# SQLCipher - ships with NO consumer proguard rules of its own. Its native
# library calls back into these Java classes/methods by exact name (JNI),
# so if R8 renames or strips anything here the app crashes the instant it
# tries to open the (encrypted) database - i.e. almost immediately on cold
# start, since that happens as soon as the first screen needs the DB.
-keep class net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# kotlinx.serialization - keep generated (de)serializers reachable via
# reflection-free but name-based lookup for our own @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class cz.kuclab.hertzchat.**$$serializer { *; }
-keepclassmembers class cz.kuclab.hertzchat.** {
    *** Companion;
}
-keepclasseswithmembers class cz.kuclab.hertzchat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
