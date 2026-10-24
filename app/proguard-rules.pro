# JSON is parsed by hand with org.json (part of Android), and OkHttp, Coil and Media3 ship
# their own consumer rules, so the default Android rules are enough.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
