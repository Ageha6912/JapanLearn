# kotlinx-serialization：保留内容 DTO 的序列化器（反射生成，R8 无法推断）
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.japanlearn.app.**$$serializer { *; }
-keepclassmembers class com.japanlearn.app.** { *** Companion; }
-keepclasseswithmembers class com.japanlearn.app.** { kotlinx.serialization.KSerializer serializer(...); }
