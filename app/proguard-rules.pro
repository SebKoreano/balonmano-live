# Network transports are supplied by RootEncoder; keep TLS provider entry points.
-dontwarn org.bouncycastle.**
# The upstream RTMP implementation logs server responses. Strip logs in the shared APK.
-assumenosideeffects class android.util.Log { public static *** *(...); }
