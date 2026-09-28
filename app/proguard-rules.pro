# A crash report is only actionable if it names real files and line numbers, and
# our own classes aren't renamed out of recognition - without this, the report
# CrashReporter saves reads as a wall of a/b/c.d() with no line information.
# Our own code is a small fraction of the APK (the bulk is libsignal/SQLCipher,
# both kept whole below), so keeping it costs very little.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class cz.kuclab.hertzchat.** { *; }

# Signal protocol native bindings
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }

# BouncyCastle (secp256k1/Schnorr/X25519 for the relay transport) - pure Java,
# no natives, no consumer rules of its own; keeping it whole avoids R8
# renaming anything its own reflective lookups expect by exact name.
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

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
