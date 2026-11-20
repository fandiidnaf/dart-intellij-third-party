---
name: sync-custom-fork
description: Guide for inspecting upstream updates, checking if custom fork patches are still needed, syncing with latest local Gradle/JDK, testing, cleaning up repo artifacts (keeping 1 latest build), and pushing per section to GitHub.
---

# Sync Custom Fork

Use this skill whenever the user asks to check upstream `flutter/dart-intellij-third-party` updates, verify whether our fork customizations are already solved upstream, sync/upgrade this fork, or build/clean/push a new `Dart-Custom` release.

## Instructions

1. **Read [`CUSTOM_FORK.md`](../../../CUSTOM_FORK.md)** at the repository root first to understand all fork customizations, modified files, and local Mac Gradle/JDK conventions.
2. **Fetch & Check Upstream First**:
   - Run `git fetch upstream` and compare `HEAD..upstream/main`.
   - Check both upstream code changes and live `dart language-server --protocol=lsp` behavior against the user's current Dart/Flutter SDK to determine if upstream already provides named-argument / constructor field-formal documentation (`A(a)` with `new({required this.a});` and `labelDetailsSupport = true`).
   - Summarize upstream features/bugfixes vs our fork's current state.
3. **Align Tooling & Research Latest Info**:
   - Inspect `~/.gradle/wrapper/dists`, `$JAVA_HOME`, and Android Studio's JBR so this repo reuses the latest Gradle `-all` and JDK already present on this Mac without downloading duplicates.
   - Check online for latest compatible versions/release notes before bumping dependencies or Gradle.
4. **Verify & Build**:
   - Run `DartBridgeLspServerTest`, `DartDocumentationProviderTest`, and `DartServerCompletionTest.testLspNamedArgCompletionDoc` (with `DART_HOME` set to the active Dart SDK).
   - Run `./gradlew buildPlugin` to generate `third_party/build/distributions/Dart-Custom-<version>.zip`.
5. **Clean Up Repo Artifacts**:
   - Remove `third_party/.gradle`, `third_party/.kotlin`, `third_party/.intellijPlatform`, and all `third_party/build/*` except `distributions/`, and delete older ZIPs in `third_party/build/distributions/` so **only the 1 latest build ZIP remains**.
   - Never delete global `~/.gradle` caches without asking the user.
6. **Commit per Section & Push to `origin`**:
   - Commit logically by section (`feat`/`merge`, `test`, `build`, `docs`), tag if requested, and push only to `origin` (never `upstream`).
