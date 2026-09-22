# Everything the native library reaches by name.
#
# JNI does not link. It looks symbols up as strings at runtime, so R8 renaming any of the
# names below turns into an UnsatisfiedLinkError or a null jmethodID the first time a model
# is loaded. None of it is reachable from Kotlin in a way R8 can see, which is why it has
# to be spelled out. These rules are consumer rules so they travel with the module: the app
# should not have to know that the engine has a native half.

# The external functions resolve as Java_io_github_..._LlamaBridge_nativeLoadModel and
# friends, so both the class name and the method names have to survive.
-keep class io.github.alpharomercoma.openweights.core.engine.LlamaBridge {
    native <methods>;
}

# Belt and braces for any other class that grows a native method later.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# llama_jni.cpp does FindClass on this to throw a load or generation failure back into
# Kotlin, then ThrowNew, which needs the String constructor. Nothing else about the class
# is reached by name, so nothing else is kept.
-keep class io.github.alpharomercoma.openweights.core.engine.LlamaException {
    <init>(java.lang.String);
}

# The token and reply callbacks are found with GetMethodID while generation is running.
#
# These rules said `-keep,allowobfuscation` and that shipped a release build which aborted
# the process on the first token of every reply:
#
#   No pending exception expected: java.lang.NoSuchMethodError:
#   no non-static method "Lw2;.onToken(Ljava/lang/String;)Z"
#
# `allowobfuscation` does not mean "the class may be renamed". It means the whole match may
# be renamed, members included, and the member name is the only part JNI actually needs:
# native code gets the class with GetObjectClass, so it never spells the class out, and then
# asks for "onToken" as a string. Granting permission to rename that method granted
# permission to break every generation, in a way no debug build can show, because R8 does
# not run there.
#
# The members are matched by name with any arguments, not by signature. On 2026-09-10 the
# token callback gained a second argument, the token's log-probability, for the confidence
# gate. The rule still spelled `onToken(java.lang.String)`, which no longer matched
# anything, so R8 kept the interface's name, dropped its method, and folded the lambda
# into a shared synthetic class. Every GGUF reply in every release from version code 604
# on aborted the process at its first token with the message above, now reading
# `"Li02;.onToken(Ljava/lang/String;F)Z"`. A signature in a keep rule is a second copy of
# the one in llama_jni.cpp that nothing kept in step; the name is the part that has to
# survive, and `verifyJniSymbols` in app/build.gradle.kts checks the full signature against
# the built dex.
-keep interface io.github.alpharomercoma.openweights.core.engine.LlamaBridge$TokenSink {
    *** onToken(...);
}
-keep interface io.github.alpharomercoma.openweights.core.engine.LlamaBridge$ReplySink {
    *** onReply(...);
}
-keepclassmembers class * implements
    io.github.alpharomercoma.openweights.core.engine.LlamaBridge$TokenSink {
    *** onToken(...);
}
-keepclassmembers class * implements
    io.github.alpharomercoma.openweights.core.engine.LlamaBridge$ReplySink {
    *** onReply(...);
}

# fbjni, kept whole.
#
# fbjni 0.7.0's AAR ships no consumer rules, and the ExecuTorch AAR's rules cover only its
# own package. On the shipped release build R8 removed every fbjni class but HybridData:
# not HybridData$Destructor, whose field the native side finds by name when it wraps the
# runner it has just built, and not CppException, which is what a C++ exception is raised
# as. So opening a model reached "Using method: forward" and then fbjni, unable to find
# either, aborted the process with the message 'ptr' (2026-09-06, Poco X8 Pro, LFM2.5 1.2B
# xnnpack; the same files load and generate on the unminified build). Every class in the
# package is reached from native code by descriptor, which R8 cannot see, and the package
# is fourteen classes. ExecuTorch's own Java classes keep their AAR's rules; they held.
-keep class com.facebook.jni.** { *; }

# Prefill/Decode Disaggregated Native Bridge
-keep class io.github.alpharomercoma.openweights.core.engine.DisaggregatedBridge {
    native <methods>;
}
-keep interface io.github.alpharomercoma.openweights.core.engine.DisaggregatedCallback {
    boolean onToken(java.lang.String);
}
-keepclassmembers class * implements io.github.alpharomercoma.openweights.core.engine.DisaggregatedCallback {
    boolean onToken(java.lang.String);
}
