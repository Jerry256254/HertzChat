# A crash report is only actionable if it names real files and line numbers, and
# our own classes aren't renamed out of recognition - without this, the report
# CrashReporter saves reads as a wall of a/b/c.d() with no line information.
# Our own code is a small fraction of the APK (the bulk is I2P/libsignal/SQLCipher,
# all of which are already kept whole below), so keeping it costs very little.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class cz.kuclab.hertzchat.** { *; }

# Signal protocol native bindings
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }

# I2P router/streaming - like SQLCipher below, this is a large third-party
# library with real crypto/native (libjbigi.so) surface and no consumer
# proguard rules of its own; keeping it whole avoids a repeat of the exact
# class of crash that SQLCipher's stripped classes caused (R8 renaming
# something a native/reflective caller expects by exact name).
-keep class net.i2p.** { *; }
-keepclassmembers class net.i2p.** { *; }
-dontwarn net.i2p.**
# Apache HttpClient code bundled inside net.i2p (used for I2P's own reseed
# HTTPS fetches) references javax.naming/LDAP classes that don't exist on
# Android - that code path isn't reachable from how we use the library.
-dontwarn javax.naming.**

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
