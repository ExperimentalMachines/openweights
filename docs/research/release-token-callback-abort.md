# The release build aborted on every GGUF reply (2026-09-14)

**Symptom, reported from production.** Installing Liquid's LFM2.5 1.2B QAD-Q4_0 GGUF from
Discover and sending "hi" closed the app. Nothing was on screen and no error was shown.

**What it actually was.** Not the QAD file, and not the download. Every GGUF model, on
every phone, aborted the process at the first token of its first reply, in every release
built from version code 604 (2026-09-10) to 612. Debug builds were unaffected, which is
why none of the week's phone runs, all on debug and instrumented builds, saw it.

## The crash

The Poco X8 Pro Max running the Play build (version code 612) recorded four identical
aborts between 03:38 and 03:39. The same crash reproduced on the minified release build of
commit a58f782b, installed under a separate package name on an arm64 emulator, with the
same QAD file (SHA-256 `bb741ebb106d...`, identical to the Hub's). The crash buffer:

```
signal 6 (SIGABRT), code -1 (SI_QUEUE)
Abort message: 'No pending exception expected: java.lang.NoSuchMethodError:
  no non-static method "Li02;.onToken(Ljava/lang/String;F)Z"
  at long[] ...core.engine.LlamaBridge.nativeGenerate(...)
#06 art::JNI<false>::FindClass
#07 libopenweights_llama.so (Java_..._LlamaBridge_nativeGenerate+2304)
```

## Cause

Two defects, one on each side of the JNI boundary.

1. **A keep rule that stopped matching.** `llama_jni.cpp` finds the token callback with
   `GetMethodID(sink_class, "onToken", "(Ljava/lang/String;F)Z")`. The confidence gate
   (commit c5bb2ff4, 2026-09-10) added the second argument, the token's log-probability,
   on both the C++ and the Kotlin side. The R8 rule in `core/engine/consumer-rules.pro`
   still read `boolean onToken(java.lang.String);`, which matched nothing after that. R8
   kept the `TokenSink` interface's name, dropped its method, and merged the lambda into
   the shared synthetic class `i02`. The R8 mapping of the 612 bundle shows exactly that.
2. **An error path that was itself the abort.** When the lookup fails, native code calls
   `throw_engine_exception` to raise "TokenSink.onToken not found" as a catchable
   `LlamaException`. But the failed `GetMethodID` had left a `NoSuchMethodError` pending,
   and the helper's first call, `FindClass`, is not permitted with an exception pending:
   ART aborts the process. A readable error in the chat became a silent close.

**Why the guard passed.** The build already ran `verifyJniSymbols` on every release APK
and bundle, written after the same class of abort in August. It searched the dex for the
bare string `onToken`, and that string was still present, so it passed every broken
build. The name was checked; the signature, the only thing JNI actually resolves, was not.

## Fix

- **Keep rules by name, any arguments.** The four callback rules now read
  `*** onToken(...);` and `*** onReply(...);`. The signature in a keep rule was a second
  copy of the one in C++ that nothing kept in step.
- **Clear a pending exception before raising ours.** `throw_engine_exception` calls
  `ExceptionClear` first, so any future lookup failure reaches the chat as an error message
  instead of closing the app.
- **The build checks signatures against the dex.** `verifyJniSymbols` now reads the dex
  method table (name and prototype of every method) and requires every
  `GetMethodID` pair found in `llama_jni.cpp` to be in it. The pairs are read from the C++
  source, not typed into the build script, so a signature changed in C++ is checked by the
  next build. Run against the broken 612 artifacts it fails with
  `No method in app-release.apk has the name and signature llama_jni.cpp asks GetMethodID
  for: onToken(Ljava/lang/String;F)Z`; against the fixed build it reports all 17 names and
  both signatures present. It runs after every `assembleRelease` and `bundleRelease`.
- **A unit test catches the drift on every push.** `JniCallbackContractTest` reads the C++
  lookups, the Kotlin `TokenSink` and `ReplySink` interfaces by reflection, and the keep
  rules, and fails if a signature differs or a rule names arguments. CI never builds a
  release, so this is the check that runs before one exists. Restoring the old rule makes
  it fail. The engine's test task now declares those two files as inputs, because Gradle
  otherwise called the test up to date after a rule change.

## Verified

| Build | Device | "hi" to the QAD-Q4_0 GGUF |
|---|---|---|
| Play build, version code 612 | Poco X8 Pro Max, HyperOS 3, Android 16 | process aborts, four times, trace above |
| minified, a58f782b (as shipped) | arm64 emulator, Android 16 | process aborts, same trace |
| minified, with the fix | arm64 emulator, Android 16 | "Hello! How can I assist you today?", no crash |
| minified, with the fix, installed beside the Play copy | Poco X8 Pro Max | the same reply, 73 tok/s prefill, 30 tok/s decode, no crash |
| the same, "Who won the 2026 FIFA World Cup?" | Poco X8 Pro Max | searched the web and answered from the results, same process, no abort in the crash buffer |

The phone runs used the method in `.claude/skills/phone-deploy/SKILL.md` plus a temporary
`applicationIdSuffix = ".rc"` on the release build type, so the minified build installs as
a second package; HyperOS asks for an on-screen "Install via USB" confirmation that
`pm install` reports as `INSTALL_FAILED_USER_RESTRICTED` if nobody taps it.

## What to take from it

A debug build cannot show an R8 fault, and no phone run this week used a release build.
The release checklist said to smoke-test the minified build on the phone, and the releases
built after 604 went out without that step. The guard existed and checked the wrong thing.
Both halves are now mechanical: the signature check fails the bundle, and the contract
test fails CI.
