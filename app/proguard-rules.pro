# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Optional local credentials are accessed by reflection.
-keep class de.jeisfeld.mymeditation.http.HttpCredentials {
    public static final java.lang.String CLAUDE_API_KEY;
    public static final java.lang.String OPENAI_API_KEY;
    public static final java.lang.String GEMINI_API_KEY;
    public static final java.lang.String GROK_API_KEY;
    public static final java.lang.String DEEPSEEK_API_KEY;
    public static final java.lang.String KIMI_API_KEY;
    public static final java.lang.String QWEN_API_KEY;
}
