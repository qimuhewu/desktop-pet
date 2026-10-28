# 混淆规则：保留 Cubism SDK JNI 入口与模型加载反射依赖
# （仅在放置了 Cubism SDK 的构建中部分条目生效，无害保留）

# Cubism Core JNI
-keep class com.live2d.sdk.cubism.** { *; }
-keep class com.live2d.sdk.cubism.core.** { *; }
-dontwarn com.live2d.sdk.cubism.**

# Live2D 模型 JSON 中的动作/表情按文件名反射加载，保留文件名信息
-keepattributes SourceFile,LineNumberTable

# Room / Coroutines 常规豁免
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.jetbrains.kotlinx.coroutines.**
