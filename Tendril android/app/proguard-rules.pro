# Add project specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Keep Compose runtime metadata used by tooling.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Kotlin metadata (for reflection-based libraries; safe default even if unused today).
-keep class kotlin.Metadata { *; }
