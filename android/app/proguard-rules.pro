# MapLibre uses JNI/native components. Keep SDK metadata available to R8.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
