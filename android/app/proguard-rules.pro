# 提醒数据用 kotlinx.serialization，保留序列化器相关的名字
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.qingtx.qingtiixing.**$$serializer { *; }
-keepclassmembers class com.qingtx.qingtiixing.** {
    *** Companion;
}
-keepclasseswithmembers class com.qingtx.qingtiixing.** {
    kotlinx.serialization.KSerializer serializer(...);
}
