# R8 rules for the release build (stage 5.6).
# Every rule here is backed by a concrete R8 warning or a documented library requirement —
# no blanket -dontwarn entries. Keep it that way.

# OkHttp references optional TLS providers that never ship on Android and are only touched
# when one of them is installed at runtime (OkHttp's own setup guide lists exactly these
# suppressions). Warnings, not missing behaviour.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
