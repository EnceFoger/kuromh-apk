# Aturan ProGuard/R8 untuk KUROMH.
# Compose, AndroidX, dan Kotlin sudah membawa consumer rules sendiri.
-keepattributes *Annotation*,Signature,InnerClasses
-dontwarn kotlinx.coroutines.**
