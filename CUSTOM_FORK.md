# Custom Fork Maintenance & Upstream Sync Guide

This repository (`fandiidnaf/dart-intellij-third-party`) is a custom fork of [`flutter/dart-intellij-third-party`](https://github.com/flutter/dart-intellij-third-party) (`upstream`).

Read this document completely before checking upstream updates, modifying fork behavior, building a release, or cleaning up workspace artifacts.

---

## 1. What This Fork Does & Why It Exists

### Problem Solved
In upstream `dart-intellij-third-party`, the Quick Documentation popup fails to appear (or omits field documentation) when completing or inspecting named arguments and field-formal parameters—for example:

```dart
/// this is A class
class A {
  /// this is a doc
  final String a;

  new({required this.a});
}

void main(List<String> args) {
  A(a) // Typing `a` inside `A(...)` should show `{required String a}`, `Declared in A.`, and `this is a doc`
}
```

Two distinct pipelines exist depending on IDE settings and Dart SDK version:

1. **LSP Completion Pipeline (Active by default on Dart SDK `>= 3.14.0-226.0.dev` when `Experimental LSP features` is enabled)**
   - Upstream routes completion through `LspCompletionContributor` -> `LspCompletionObject`, bypassing `DartServerCompletionContributor` and `DartDocumentationProvider`.
   - Dart's LSP server omits `documentation` and `data` on named-argument `CompletionItem`s (`textDocument/completion` and `completionItem/resolve`), and with `labelDetailsSupport = true` emits `label = "a:"` (no trailing space) and `filterText = null`.
   - Furthermore, `LspCompletionContributor` uses `Semaphore(2)`, which triggers concurrent `completionItem/resolve` calls that can cause concurrent legacy `completion_getSuggestions2` requests on DAS to abort each other.

2. **Legacy DAS Completion Pipeline (When `Experimental LSP features` is disabled or on older Dart SDKs)**
   - Upstream's `DartDocumentationProvider.generateDoc` gated documentation behind an active `DartLookupObject`, and `DartLookupObject.findPsiElement()` / `DartDocUtil.generateDoc()` did not resolve constructor `DartFieldFormalParameter` (`this.a`) back to the underlying class field `a` or handle `file://` / `temp://` URI fallbacks.

---

## 2. Inventory of Customizations in This Fork

### A. LSP Named-Argument Documentation Enrichment
- [`third_party/src/main/java/com/jetbrains/lang/dart/lsp/DartBridgeLspServer.kt`](third_party/src/main/java/com/jetbrains/lang/dart/lsp/DartBridgeLspServer.kt)
  - `completion()`: tags named-argument candidates (`item.documentation == null && item.data == null` and `label` or `filterText` ending with `:` after `trimEnd()`, when `itemDefaults?.data == null`) with an internal `__dartBridgeNamedArg` context marker `(uri, line, character)`.
  - `resolveCompletionItem()`: intercepts `__dartBridgeNamedArg`, strips `data = null` so the marker never leaks to DAS, serializes DAS lookup under `namedArgResolveLock` (preventing parallel `completionItem/resolve` calls from aborting each other), syncs `das.updateFilesContent()` inside `runReadAction`, calls `das.completion_getSuggestions2` (with brief retry if empty and per-`(filePath, offset, modStamp)` caching), resolves PSI details via `DartLookupObject` + `DartDocUtil`, and formats Markdown (`{required Type name}` / `{Type name}`, `Declared in \`Class\`.`, and `///` docs) into `MarkupContent`.
- [`third_party/src/main/java/com/jetbrains/lang/dart/ide/documentation/DartDocUtil.java`](third_party/src/main/java/com/jetbrains/lang/dart/ide/documentation/DartDocUtil.java)
  - Exposes `findDartComponent(PsiElement)` and `getDocumentationText(DartComponent)` as `public static` so `DartBridgeLspServer` reuses the existing comment-extraction logic without duplication.

### B. Legacy DAS Quick Documentation Fixes
- [`third_party/src/main/java/com/jetbrains/lang/dart/ide/completion/DartLookupObject.java`](third_party/src/main/java/com/jetbrains/lang/dart/ide/completion/DartLookupObject.java)
  - Adds `VirtualFileManager` fallback (`file://`, `temp://`) and resolves `DartFieldFormalParameter` (`this.x`), `DartReferenceExpression`, and `DartComponent`.
- [`third_party/src/main/java/com/jetbrains/lang/dart/ide/documentation/DartDocUtil.java`](third_party/src/main/java/com/jetbrains/lang/dart/ide/documentation/DartDocUtil.java)
  - Resolves `DartFieldFormalParameter` and `DartReferenceExpression` to their underlying target field/component before generating doc HTML.
- [`third_party/src/main/java/com/jetbrains/lang/dart/ide/documentation/DartDocumentationProvider.java`](third_party/src/main/java/com/jetbrains/lang/dart/ide/documentation/DartDocumentationProvider.java)
  - Delegates `generateDoc()` directly to `DartDocUtil.generateDoc(element)` without requiring an active lookup.

### C. Custom Plugin Metadata, Versioning & Archive Naming
- [`third_party/src/main/resources/META-INF/plugin.xml`](third_party/src/main/resources/META-INF/plugin.xml): `<name>Dart-Custom</name>` (keeping `<id>Dart</id>`).
- [`third_party/settings.gradle.kts`](third_party/settings.gradle.kts): `rootProject.name = "Dart-Custom"`.
- [`third_party/gradle.properties`](third_party/gradle.properties): `pluginName = Dart-Custom`, `pluginId = Dart`, `pluginVersion = 512.0.2-custom` (bump as needed).
- [`third_party/build.gradle.kts`](third_party/build.gradle.kts): reads `pluginVersion` property when present and outputs `<pluginName>-<pluginVersion>.zip` from `buildPlugin`.

### D. Local Mac Gradle & JDK Alignment
- [`third_party/gradle/wrapper/gradle-wrapper.properties`](third_party/gradle/wrapper/gradle-wrapper.properties): pinned to the latest Gradle `-all` distribution already cached locally on this Mac (currently `gradle-9.9.2-all.zip`, shared with the Flutter and Android tutorial workspaces to save disk space).

### E. Regression Tests
- [`third_party/src/test/java/com/jetbrains/lang/dart/lsp/DartBridgeLspServerTest.kt`](third_party/src/test/java/com/jetbrains/lang/dart/lsp/DartBridgeLspServerTest.kt): unit tests for `label: "a:"` (`labelDetailsSupport`), `resolveCompletionItem`, no marker leakage, and type-only fallback without `///`.
- [`third_party/src/test/java/com/jetbrains/lang/dart/documentation/DartDocumentationProviderTest.java`](third_party/src/test/java/com/jetbrains/lang/dart/documentation/DartDocumentationProviderTest.java): unit tests for class properties, `Main({required this.name})`, and `new({required this.a});`.
- [`third_party/src/test/java/com/jetbrains/dart/analysisServer/DartServerCompletionTest.java`](third_party/src/test/java/com/jetbrains/dart/analysisServer/DartServerCompletionTest.java): real-SDK E2E test `testLspNamedArgCompletionDoc` (requires `DART_HOME`).

---

## 3. Workflow for Future Upstream Updates

Follow these steps whenever checking or syncing with `upstream`:

### Step 1: Fetch Upstream & Check Whether Upstream Already Solves Our Use Case
1. Fetch latest upstream changes without modifying local branches:
   ```bash
   git fetch upstream
   git log HEAD..upstream/main --oneline
   ```
2. Inspect upstream changes touching completion, documentation, and LSP:
   - `DartBridgeLspServer.kt`, `DartLspServerDescriptor.kt`, `DartServerCompletionContributor.java`
   - `DartDocumentationProvider.java`, `DartDocUtil.java`, `DartLookupObject.java`
   - `third_party/thirdPartySrc/platform-lsp/.../completion/LspCompletionObject.kt`
3. **Empirically test the local Dart SDK / Flutter SDK** (if the Dart SDK was also upgraded) to check whether `dart language-server --protocol=lsp` (`textDocument/completion` with `labelDetailsSupport: true` + `completionItem/resolve`) now returns `documentation` for named arguments on `new({required this.a});` and `A({required this.a});`, AND whether upstream's plugin renders them in `LspCompletionObject` and `DartDocumentationProvider`.
4. **Decision point**:
   - If upstream now satisfies all custom requirements natively, present the evidence to the user and propose dropping the custom patches.
   - If upstream still does not satisfy them, summarize what new features/bugfixes exist in `upstream/main` compared to our current fork and plan the merge.

### Step 2: Check Local Mac Gradle, JDK & Latest Versions
Before running builds or updating `gradle-wrapper.properties`:
1. **Check local cached Gradle & JDKs on this Mac** to avoid downloading duplicates:
   - Local Gradle wrapper cache: `ls -lh ~/.gradle/wrapper/dists`
   - Current official Gradle release: `https://services.gradle.org/versions/current`
   - Local JDK / Android Studio JBR: `~/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java -version` (and `echo $JAVA_HOME`)
2. **Rule**: Always prefer the **latest Gradle `-all` version and JDK already cached/installed on this Mac** (e.g. `gradle-9.9.2-all.zip` + Android Studio JBR) or verify online if a newer shared version is already in use across the user's Flutter/Android projects.
3. **Always research latest info online** when evaluating dependency/plugin/IDE version bumps (e.g., `intellijPlatform` plugin, `ideaVersion`, Kotlin/Gradle compatibility).

### Step 3: Merge Upstream & Update Version
1. Create a new sync branch for major upstream syncs (e.g. `sync/513-custom`) or stay on the active `sync/<major>-custom` branch for patch bumps (`512.0.x-custom`).
2. Merge `upstream/main`, preserving the customizations listed in Section 2 and keeping `gradle-wrapper.properties` aligned to the latest shared local Gradle `-all` distribution.
3. Update `pluginVersion` in `third_party/gradle.properties` (e.g., `513.0.0-custom` or `512.0.3-custom`).

### Step 4: Verify Tests & Build the Plugin ZIP
1. Locate the active Dart SDK (e.g. from Flutter FVM: `dirname $(dirname $(which dart))` or `~/fvm/versions/<version>/bin/cache/dart-sdk`).
2. Run both unit tests and the real-SDK E2E test:
   ```bash
   DART_HOME=/path/to/dart-sdk ./gradlew test \
     --tests "com.jetbrains.lang.dart.lsp.DartBridgeLspServerTest" \
     --tests "com.jetbrains.lang.dart.documentation.DartDocumentationProviderTest" \
     --tests "com.jetbrains.dart.analysisServer.DartServerCompletionTest.testLspNamedArgCompletionDoc" \
     --console=plain
   ```
3. Build the installable plugin archive:
   ```bash
   ./gradlew buildPlugin --console=plain
   ```
   Confirm `third_party/build/distributions/Dart-Custom-<pluginVersion>.zip` was produced.

### Step 5: Clean Up Project Build/Caches & Keep Only 1 Latest Build
To keep the repository lightweight on disk after verification:
1. Remove generated repo-local caches and intermediate build outputs, **preserving only the single latest ZIP** in `third_party/build/distributions/`:
   ```bash
   ./gradlew --stop || true
   rm -rf third_party/.gradle third_party/.kotlin third_party/.intellijPlatform
   find third_party/build -mindepth 1 -maxdepth 1 ! -name 'distributions' -exec rm -rf {} +
   find third_party/build/distributions -mindepth 1 ! -name "Dart-Custom-<pluginVersion>.zip" -exec rm -rf {} +
   ```
2. Do **not** delete global `~/.gradle` caches without explicitly asking the user first (list any unused old Gradle version caches and their sizes for the user to decide).

### Step 6: Commit per Section & Push to GitHub
1. Stage and commit changes logically **per section** (never lump unrelated changes into one commit):
   - e.g., `merge: sync upstream/main ...` / `feat(lsp): ...`
   - `test: ...`
   - `build: bump custom plugin version to ...` / `build(gradle): ...`
   - `docs: ...`
2. Create an annotated or lightweight version tag if appropriate (e.g. `v<pluginVersion>`).
3. Push the branch (and tag) to `origin` (`fandiidnaf/dart-intellij-third-party`):
   ```bash
   git push origin <branch>
   git push origin v<pluginVersion>
   ```
   **Never** push to `upstream`.
