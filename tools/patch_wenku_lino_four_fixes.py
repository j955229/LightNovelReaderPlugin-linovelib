from pathlib import Path

def replace(path, before, after, count=1):
    file = Path(path)
    body = file.read_text()
    got = body.count(before)
    if got != count:
        raise RuntimeError(f'{path}: expected {count} match(es), got {got} for {before[:100]!r}')
    file.write_text(body.replace(before, after, count))

Path('lib/linovelib_browser_page.dart').write_text(
    Path('../tools/linovelib_browser_page.dart').read_text())
replace('pubspec.yaml','  html: ^0.15.6',
        '  html: ^0.15.6\n  webview_flutter: ^4.14.1')
replace('pubspec.yaml','version: 0.4.5+95','version: 0.4.6+96')
replace('android/app/build.gradle.kts',
        'applicationIdSuffix = ".fold_reader_v4"',
        'applicationIdSuffix = ".fold_reader_v5"')
replace('android/app/build.gradle.kts',
        '"轻小说文库·書源修復"', '"轻小说文库·嗶哩修復"')

# External inline illustrations go directly to the external HTTPS image
# downloader. Native Wenku8 illustration transport stays unchanged.
replace('lib/reader_illustrations.dart',
        "import 'legacy_transport.dart';",
        "import 'legacy_transport.dart';\nimport 'linovelib_cover.dart';")
replace('lib/reader_illustrations.dart',
        "  }) : _request = request ?? LegacyTransport.illustration;",
        "  }) : _request = request ?? ((url) {\n"
        "          final uri = Uri.tryParse(url);\n"
        "          if (uri != null &&\n"
        "              const {'tw.linovelib.com', 'www.bilinovel.com',\n"
        "                      'www.linovelib.com'}.contains(uri.host)) {\n"
        "            return LinovelibCover.bytes(url);\n"
        "          }\n"
        "          return LegacyTransport.illustration(url);\n"
        "        });")

# Native Wenku8 XML-review endpoints are never called for BL book IDs.
replace('lib/review_pages.dart',
        "import 'wenku_reviews.dart';",
        "import 'wenku_reviews.dart';\n"
        "import 'linovelib_repository.dart';\n"
        "import 'linovelib_browser_page.dart';")
replace('lib/review_pages.dart',
        "  @override\n"
        "  Widget build(BuildContext context) {\n"
        "    final features = WenkuFeaturesScope.maybeOf(context);",
        "  @override\n"
        "  Widget build(BuildContext context) {\n"
        "    if (LinovelibRepository.owns(novel.id)) {\n"
        "      return SingleChildScrollView(\n"
        "        scrollDirection: Axis.horizontal,\n"
        "        child: Row(children: [\n"
        "          GlassTextButton(\n"
        "            key: const ValueKey('linovelib-book-reviews'),\n"
        "            onPressed: () => pushNovelPage<void>(context,\n"
        "              LinovelibBrowserPage(\n"
        "                title: '作品評論',\n"
        "                url: LinovelibBrowserPage.reviewUrl(novel.id),\n"
        "              ), reduceMotion: state.reduceMotion),\n"
        "            child: const AppText('評論'),\n"
        "          ),\n"
        "          const SizedBox(width: 8),\n"
        "          GlassTextButton(\n"
        "            key: const ValueKey('linovelib-book-site'),\n"
        "            onPressed: () => pushNovelPage<void>(context,\n"
        "              LinovelibBrowserPage(\n"
        "                title: novel.title,\n"
        "                url: LinovelibBrowserPage.bookUrl(novel.id),\n"
        "              ), reduceMotion: state.reduceMotion),\n"
        "            child: const AppText('網站互動'),\n"
        "          ),\n"
        "          const SizedBox(width: 8),\n"
        "          GlassTextButton(\n"
        "            key: const ValueKey('linovelib-account-link'),\n"
        "            onPressed: () => pushNovelPage<void>(context,\n"
        "              const LinovelibBrowserPage(\n"
        "                title: '登入嗶哩輕小說',\n"
        "                url: LinovelibBrowserPage.loginUrl,\n"
        "              ), reduceMotion: state.reduceMotion),\n"
        "            child: const AppText('登入嗶哩輕小說'),\n"
        "          ),\n"
        "        ]),\n"
        "      );\n"
        "    }\n"
        "    final features = WenkuFeaturesScope.maybeOf(context);")

# The already-present account panel is kept, but when BL is selected its
# account card opens the official BL login page instead.
replace('lib/my_page.dart',
        "import 'wenku_features.dart';",
        "import 'wenku_features.dart';\n"
        "import 'linovelib_repository.dart';\n"
        "import 'linovelib_browser_page.dart';")
replace('lib/my_page.dart',
        "    final features = WenkuFeaturesScope.maybeOf(context);\n"
        "    final updates = AppUpdatesScope.maybeOf(context);",
        "    final features = WenkuFeaturesScope.maybeOf(context);\n"
        "    final updates = AppUpdatesScope.maybeOf(context);\n"
        "    final remote = repository is OfflineNovelRepository\n"
        "        ? (repository as OfflineNovelRepository).remote : repository;\n"
        "    final source = remote is DualSourceRepository ? remote : null;\n"
        "    return source == null\n"
        "        ? _body(context, features, updates, false)\n"
        "        : ValueListenableBuilder<BookSource>(\n"
        "            valueListenable: source.changes,\n"
        "            builder: (context, active, _) =>\n"
        "                _body(context, features, updates,\n"
        "                  active == BookSource.linovelib),\n"
        "          );\n"
        "  }\n\n"
        "  Widget _body(BuildContext context, dynamic features, dynamic updates,\n"
        "      bool isLinovelib) {")
replace('lib/my_page.dart',
        "                    onTap: account == null\n"
        "                        ? null\n"
        "                        : () => pushNovelPage<void>(",
        "                    onTap: isLinovelib\n"
        "                        ? () => pushNovelPage<void>(\n"
        "                            context,\n"
        "                            const LinovelibBrowserPage(\n"
        "                              title: '登入嗶哩輕小說',\n"
        "                              url: LinovelibBrowserPage.loginUrl,\n"
        "                            ),\n"
        "                            reduceMotion: state.reduceMotion,\n"
        "                          )\n"
        "                        : account == null\n"
        "                        ? null\n"
        "                        : () => pushNovelPage<void>(")
replace('lib/my_page.dart',
        "                                account?.authenticated == true\n"
        "                                    ? profile?.displayName ?? '文库账号'\n"
        "                                    : '登录文库账号',",
        "                                isLinovelib\n"
        "                                    ? '登入嗶哩輕小說'\n"
        "                                    : account?.authenticated == true\n"
        "                                    ? profile?.displayName ?? '文库账号'\n"
        "                                    : '登录文库账号',")
replace('lib/my_page.dart',
        "                                account?.authenticated == true\n"
        "                                    ? profile == null",
        "                                isLinovelib\n"
        "                                    ? '嗶哩輕小說帳號 · 查看書櫃、評論與個人資料'\n"
        "                                    : account?.authenticated == true\n"
        "                                    ? profile == null")
# The app account images remain decorative; session state stays isolated.

Path('test/linovelib_four_fixes_test.dart').write_text(r"""
import 'package:flutter_test/flutter_test.dart';
import 'package:wenku8_flutter/linovelib_repository.dart';
import 'package:wenku8_flutter/linovelib_browser_page.dart';

void main() {
  test('reviews and authentication stay on Linovelib and keep source ID', () {
    expect(LinovelibBrowserPage.reviewUrl(1200003247),
        'https://tw.linovelib.com/reviews_3247_1.html');
    expect(LinovelibBrowserPage.bookUrl(1200003247),
        'https://tw.linovelib.com/novel/3247.html');
    expect(LinovelibBrowserPage.loginUrl,
        contains('tw.linovelib.com/login.php'));
  });
}
""")
print('Prepared official-source review and account pages; native Wenku8 untouched.')
