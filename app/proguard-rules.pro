# JSON is parsed by hand with org.json (part of Android), and OkHttp, Coil and Media3 ship
# their own consumer rules, so the default Android rules are enough.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# SMB (smbj), for moving deleted songs to the Trash on the NAS. It builds its protocol messages
# and events by reflection in places, so it's kept whole; its optional extras aren't on Android.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**
-dontwarn org.slf4j.**
-dontwarn net.engio.mbassy.**
-dontwarn com.hierynomus.**
