# Linovelib Formatting And Publishing Design

## Goal

Publish the Linovelib data-source plugin as a standalone public repository while fixing invalid search-result dates and improving chapter paragraph presentation.

## Search Metadata

The Linovelib search page yields lightweight rows without the `BookInformation` fields required by LightNovelReader. For each parsed search row, the provider will load the corresponding TW book detail page and reuse the existing detail parser. Detail requests will run with a concurrency limit of four. A failed detail request will be logged and omitted so the UI never displays fabricated dates such as `00000/1/1`.

## Chapter Formatting

Consecutive text blocks will be rendered as one text component. Each source paragraph will receive two ideographic spaces at the beginning and paragraphs will be separated by a blank line. Image blocks remain separate and retain their original position. This produces clear paragraph boundaries while preserving the APP's font, line-height, theme, and text-formatting controls.

## Standalone Repository

Create `j955229/LightNovelReaderPlugin-linovelib` as a public standalone repository. Use the `potato_lib` branch of `dmzz-yyhyy/LightNovelReaderPlugin-Template` as the build reference without retaining a fork relationship. Retain the LightNovelReader 1.2.0 API 2 compile interface because the current published snapshot has an incompatible `Identifier` contract. Link it with `compileOnly`, include tests, a Chinese README, and a build workflow. The README must link the reference template and briefly explain installation, building, supported features, and limitations.

## Release

The plugin version will be `1.0.17` with version code `18`. The local embedded module and standalone source must pass the complete unit-test suite and assemble successfully before publication.
