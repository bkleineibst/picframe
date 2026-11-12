# R8 rules for the release build (minifyEnabled + shrinkResources in build.gradle).

# Only remove unused code. Renaming, and optimizations that merge classes or move
# methods between them, would leave logcat stack traces pointing at the wrong
# classes and lines; they'd save only a little more.
-dontobfuscate
-dontoptimize
-keepattributes SourceFile,LineNumberTable

# CustomViewPager swaps ViewPager's scroller via reflection to slow the slide
# animation. If these fields are renamed or removed, mScroller stays null and the
# slideshow crashes.
-keepclassmembers class android.support.v4.view.ViewPager {
    *** mScroller;
    *** sInterpolator;
}

# DownloadService clears the notification's actions via reflection.
-keepclassmembers class android.support.v4.app.NotificationCompat$Builder {
    *** mActions;
}

# Networking stack: commons-httpclient creates auth schemes and cookie specs by
# reflection, and the ownCloud library builds on it. Leave these libraries whole
# rather than chase every reflective path, since only a real server exercises them.
-keep class com.owncloud.android.lib.** { *; }
-keep class org.apache.commons.httpclient.** { *; }
-keep class org.apache.jackrabbit.webdav.** { *; }
-keep class org.apache.commons.logging.** { *; }
-keep class org.slf4j.** { *; }

# Referenced only by code that never runs on Android: jackrabbit's server-side
# servlet classes, OSGi build annotations, and slf4j's optional logger binding
# (without one, slf4j falls back to a no-op logger, as before).
-dontwarn javax.servlet.**
-dontwarn aQute.bnd.annotation.**
-dontwarn org.slf4j.impl.**
