-keep class com.lumo.app.** { *; }
-keep class androidx.media3.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
