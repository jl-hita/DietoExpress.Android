# Keep Gson model metadata and model names used by the backend JSON contract.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.dietoexpress.mobile.data.** { *; }

# Keep Android components instantiated by the framework.
-keep class com.dietoexpress.mobile.DietoExpressApplication { *; }
-keep class com.dietoexpress.mobile.DietoExpressMessagingService { *; }

# Preserve source line information for useful crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
