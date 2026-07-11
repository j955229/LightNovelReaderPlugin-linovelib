# Linovelib Formatting And Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix search metadata and chapter paragraph presentation, then publish the plugin as an independently buildable public GitHub repository.

**Architecture:** Keep HTML parsing in `LinovelibHtmlParser`, enrich lightweight search rows through the existing detail loader, and isolate display paragraph assembly in a pure formatter. Build the standalone repository from the official plugin template dependency layout while retaining the tested plugin source.

**Tech Stack:** Kotlin, Android Gradle Plugin, LightNovelReader API 2, Jsoup, JUnit 4, GitHub Actions

## Global Constraints

- Repository name: `LightNovelReaderPlugin-linovelib`.
- Repository visibility: public.
- Plugin version: `1.0.17 [18]`.
- Search detail concurrency: at most four requests.
- Paragraph indentation: two ideographic spaces.
- README language: simple Traditional Chinese with a link to the original template.

---

### Task 1: Search Result Metadata

**Files:**
- Modify: `plugin/linovelib/src/main/kotlin/io/nightfish/lightnovelreader/plugin/linovelib/source/LinovelibSearchProvider.kt`
- Test: `plugin/linovelib/src/test/kotlin/io/nightfish/lightnovelreader/plugin/linovelib/source/LinovelibSearchProviderTest.kt`

**Interfaces:**
- Consumes: `htmlLoader(String): String`, `ParsedExploreBook.id`, `LinovelibUrls.book(String)`.
- Produces: complete `MutableBookInformation` values parsed from each book detail page.

- [ ] Add a provider test whose search row contains a result with no date and whose detail fixture contains `2026-06-30`.
- [ ] Run `./gradlew :plugin:linovelib:testDebugUnitTest --tests '*LinovelibSearchProviderTest*'` and confirm the lightweight result still yields the sentinel date.
- [ ] Load detail pages with a four-permit coroutine semaphore, parse complete information, and omit failed detail rows after diagnostics logging.
- [ ] Run the targeted test and confirm it passes with the fixture date, word count, and completion state.

### Task 2: Paragraph Presentation

**Files:**
- Create: `plugin/linovelib/src/main/kotlin/io/nightfish/lightnovelreader/plugin/linovelib/source/LinovelibContentFormatter.kt`
- Modify: `plugin/linovelib/src/main/kotlin/io/nightfish/lightnovelreader/plugin/linovelib/source/LinovelibWebDataSource.kt`
- Test: `plugin/linovelib/src/test/kotlin/io/nightfish/lightnovelreader/plugin/linovelib/source/LinovelibContentFormatterTest.kt`

**Interfaces:**
- Consumes: ordered `List<ParsedContentBlock>`.
- Produces: ordered blocks where consecutive text is joined using `\n\n` and each paragraph starts with `\u3000\u3000`.

- [ ] Add tests for consecutive text, embedded line breaks, and images between text groups.
- [ ] Run the formatter test and confirm it fails because the formatter is absent.
- [ ] Implement the pure formatter and apply it before `ContentBuilder` creates components.
- [ ] Run formatter and parser tests and confirm paragraph order and image order pass.

### Task 3: Version And Embedded Build

**Files:**
- Modify: `plugin/linovelib/build.gradle.kts`

**Interfaces:**
- Produces: plugin metadata `1.0.17 [18]` and a debug APK.

- [ ] Set `versionCode = 18` and `versionName = "1.0.17"`.
- [ ] Run `./gradlew :plugin:linovelib:clean :plugin:linovelib:testDebugUnitTest :plugin:linovelib:assembleDebug`.
- [ ] Confirm all test XML files report zero failures and inspect APK metadata and SHA-256.

### Task 4: Standalone Repository

**Files:**
- Create: `LightNovelReaderPlugin-linovelib/` from the template build structure.
- Create: `LightNovelReaderPlugin-linovelib/README.md`.
- Create: `LightNovelReaderPlugin-linovelib/.github/workflows/build.yml`.
- Copy: plugin source, resources, manifest, and tests into `LightNovelReaderPlugin-linovelib/plugin/`.

**Interfaces:**
- Consumes: LightNovelReader 1.2.0 API 2 compile interface and the completed embedded source.
- Produces: an independent Gradle project that builds `.apk.lnrp` artifacts.

- [ ] Create the standalone Gradle structure using the template's `potato_lib` dependency configuration.
- [ ] Write a Traditional Chinese README that links the template and explains installation, features, building, and limitations.
- [ ] Add a GitHub Actions workflow that runs tests and assembles the plugin.
- [ ] Run standalone `./gradlew clean testDebugUnitTest assembleDebug` and confirm success.

### Task 5: GitHub Publication

**Files:**
- Stage only files inside `LightNovelReaderPlugin-linovelib/`.

**Interfaces:**
- Produces: public repository `https://github.com/j955229/LightNovelReaderPlugin-linovelib`.

- [ ] Initialize the standalone directory as a new Git repository on branch `main`.
- [ ] Commit the verified source and documentation with a concise commit message.
- [ ] Create the public GitHub repository and push `main`.
- [ ] Verify the remote default branch, README, source tree, and workflow are visible.
