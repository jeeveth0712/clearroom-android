# ClearRoom release keep rules (M12). R8 shrinks the whole app; MediaPipe and protobuf-javalite
# ship no rules of their own, so they are kept here.

# MediaPipe: its native graph calls back into these Java classes by name (JNI), and task options
# and results are built through reflection and AutoValue.
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }

# protobuf-javalite: generated messages (MediaPipe's graph configs) are read through their fields
# by reflection.
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Flogger (MediaPipe's logging) finds its backend by name.
-keep class com.google.common.flogger.** { *; }

# MediaPipe's usage-statistics library finds its upload backend by name from the manifest, and its
# runtime ships no rules. Kept whole so nothing in it can fail at run time (its uploads are blocked:
# the INTERNET permission is removed, see AndroidManifest.xml).
-keep class com.google.android.datatransport.** { *; }

# Classes MediaPipe refers to but doesn't ship (R8's missing_rules.txt): annotations, and protos for
# its profiler and graph templates, features ClearRoom never uses.
-dontwarn com.google.auto.value.extension.memoized.Memoized
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
-dontwarn javax.annotation.Nullable
