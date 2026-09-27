# Origin-aware WebMessageListener entry points are referenced by AndroidX WebKit.
-keep class com.vitalis.healthos.MainActivity$VitalisWebMessageListener { *; }

# JSON models are constructed explicitly, but their names are retained for useful release traces.
-keepattributes SourceFile,LineNumberTable
