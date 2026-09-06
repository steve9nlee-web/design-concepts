# Keep default rules; minification is disabled for this app.
# If minify is ever enabled, jcifs-ng needs its classes kept:
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-dontwarn org.bouncycastle.**
