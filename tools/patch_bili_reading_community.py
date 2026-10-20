from pathlib import Path

def patch(path, old, new):
    p=Path(path)
    data=p.read_text()
    n=data.count(old)
    if n != 1:
        raise RuntimeError(f"{path}: expected 1 match got {n} for {old[:150]!r}")
    p.write_text(data.replace(old,new,1))

def insert(path, before, after):
    patch(path,before,after+before)

Path("lib/linovelib_web_page.dart").write_text(
    Path("../tools/linovelib_web_page.dart").read_text()
)
Path("lib/linovelib_chapter_decoder.dart").write_text(
    Path("../tools/linovelib_chapter_decoder.dart").read_text()
)
patch("pubspec.yaml",
      "  html: ^0.15.6",
      "  html: ^0.15.6\n  webview_flutter: ^4.13.0")
patch("lib/linovelib_repository.dart",
      "import 'linovelib_search_client.dart';",
      "import 'linovelib_search_client.dart';\n"
      "import 'linovelib_chapter_decoder.dart';")

p=Path('lib/linovelib_repository.dart')
s=p.read_text()
a=s.index('  @override\n  Future<String> chapterContent(int id, NovelChapter chapter) async {',
          s.index('class LinovelibRepository extends NovelRepository'))
b=s.index('\n  @override\n  Future<List<Novel>> search',a)
s=s[:a]+"""  @override
  Future<String> chapterContent(int id, NovelChapter chapter) async {
    final bookId = id - idOffset;
    var url = chapterSite + '/novel/' + bookId.toString() +
        '/' + chapter.id.toString() + '.html';
    final visited = <String>{};
    final paragraphs = <String>[];
    var incomplete = false;
    for (var count = 0; count < 30 && visited.add(url); count++) {
      final decoded = LinovelibChapterDecoder.parse(
        await requestHtml(url),
        chapterId: chapter.id,
        baseUrl: url,
      );
      paragraphs.addAll(decoded.blocks);
      incomplete = incomplete || decoded.hasFailureMarker;
      final next = fullUrl(decoded.nextUrl, chapterSite);
      final candidate = RegExp(r'/novel/\\\\d+/(\\\\d+)(?:_\\\\d+)?\\\\.html')
          .firstMatch(next);
      if (candidate == null || int.tryParse(candidate.group(1)!) != chapter.id ||
          !next.startsWith(chapterSite + '/novel/')) {
        break;
      }
      url = next;
    }
    if (incomplete) {
      // A crawler receives a deliberately truncated website payload.
      // Never save or show that as the complete chapter.
      throw const FormatException(
        '網站返回了不完整正文，請點右上角「網頁閱讀」使用網站原生閱讀器',
      );
    }
    if (paragraphs.join().length < 20) {
      throw const FormatException('小說正文為空，請使用右上角「網頁閱讀」');
    }
    return paragraphs.join('\\\\n\\\\n');
  }
"""+s[b:]
p.write_text(s)

# Reader paragraph parsing whitelist: external illustrations require an
# explicit trusted domain before becoming an image block.
patch("lib/reader_content.dart",
      "          uri.host == 'pic.777743.xyz')) {",
      "          uri.host == 'pic.777743.xyz' ||\n"
      "          uri.host == 'tw.linovelib.com' ||\n"
      "          uri.host == 'www.linovelib.com' ||\n"
      "          uri.host == 'www.bilinovel.com' ||\n"
      "          uri.host == 'img3.readpai.com')) {")
patch("lib/linovelib_cover.dart",
      "                'www.bilinovel.com'].contains(uri.host)) {",
      "                'www.bilinovel.com', 'img3.readpai.com'].contains(uri.host)) {")
patch("lib/linovelib_cover.dart",
      "request.headers.set(HttpHeaders.refererHeader,\n"
      "          uri.host == 'www.bilinovel.com'\n"
      "              ? 'https://www.bilinovel.com/'\n"
      "              : 'https://tw.linovelib.com/');",
      "request.headers.set(HttpHeaders.refererHeader,\n"
      "          uri.host == 'www.bilinovel.com' || uri.host == 'img3.readpai.com'\n"
      "              ? 'https://www.bilinovel.com/'\n"
      "              : 'https://tw.linovelib.com/');")
patch("lib/reader_illustrations.dart",
      "import 'local_image_layout.dart';",
      "import 'local_image_layout.dart';\n"
      "import 'linovelib_cover.dart';")
patch("lib/reader_illustrations.dart",
      "  static final shared = ReaderIllustrationCache();",
      "  static final shared = ReaderIllustrationCache(request: (url) {\n"
      "    final host = Uri.tryParse(url)?.host;\n"
      "    if (const {'tw.linovelib.com', 'www.linovelib.com',\n"
      "      'www.bilinovel.com', 'img3.readpai.com'}.contains(host)) {\n"
      "      return LinovelibCover.bytes(url);\n"
      "    }\n"
      "    return LegacyTransport.illustration(url);\n"
      "  });")

patch("lib/pages.dart",
      "import 'linovelib_repository.dart';",
      "import 'linovelib_repository.dart';\n"
      "import 'linovelib_web_page.dart';")
# Reader toolbar, only for Bilinovel IDs. Website operates with JavaScript,
# while the regular built-in reader remains unchanged for complete chapters.
insert("lib/pages.dart",
       "                        const SizedBox(width: 4),\n"
       "                        GlassIconButton(\n"
       "                          key: const ValueKey('reader-settings-entry'),",
       "                        if (LinovelibRepository.owns(widget.novel.id))\n"
       "                          GlassIconButton(\n"
       "                            key: const ValueKey('linovelib-native-web-reading'),\n"
       "                            icon: CupertinoIcons.globe,\n"
       "                            label: '網頁閱讀',\n"
       "                            onPressed: () => pushNovelPage<void>(\n"
       "                              context,\n"
       "                              LinovelibWebPage(\n"
       "                                title: widget.novel.title,\n"
       "                                url: LinovelibWebPage.chapter(\n"
       "                                  widget.novel.id,\n"
       "                                  widget.novel.chapters[_chapter].id,\n"
       "                                ),\n"
       "                              ),\n"
       "                              reduceMotion: widget.state.reduceMotion,\n"
       "                            ),\n"
       "                          ),\n")

# Replace mistaken Wenku8 review endpoints and native Wenku login in the
# Bili branch of the details actions.
patch("lib/review_pages.dart",
      "import 'wenku_reviews.dart';",
      "import 'wenku_reviews.dart';\n"
      "import 'linovelib_repository.dart';\n"
      "import 'linovelib_web_page.dart';")
patch("lib/review_pages.dart",
      "  @override\n  Widget build(BuildContext context) {\n"
      "    final features = WenkuFeaturesScope.maybeOf(context);",
      """  @override
  Widget build(BuildContext context) {
    if (LinovelibRepository.owns(novel.id)) {
      return SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(children: [
          GlassTextButton(
            key: const ValueKey('detail-bili-reviews'),
            compact: true,
            onPressed: () => pushNovelPage<void>(
              context,
              LinovelibWebPage(
                title: '嗶哩輕小說書評',
                url: LinovelibWebPage.reviews(novel.id),
              ),
              reduceMotion: state.reduceMotion,
            ),
            child: const AppText('書評'),
          ),
          const SizedBox(width: 10),
          GlassTextButton(
            key: const ValueKey('detail-bili-login'),
            compact: true,
            onPressed: () => pushNovelPage<void>(
              context,
              LinovelibWebPage(
                title: '登入嗶哩輕小說',
                url: LinovelibWebPage.login(),
              ),
              reduceMotion: state.reduceMotion,
            ),
            child: const AppText('嗶哩帳號'),
          ),
        ]),
      );
    }
    final features = WenkuFeaturesScope.maybeOf(context);""")

# Existing My/account page needs to route depending on selected source.
patch("lib/my_page.dart",
      "import 'wenku_features.dart';",
      "import 'wenku_features.dart';\n"
      "import 'linovelib_repository.dart';\n"
      "import 'linovelib_web_page.dart';")
patch("lib/my_page.dart",
      "  @override\n  Widget build(BuildContext context) {\n"
      "    final features = WenkuFeaturesScope.maybeOf(context);",
      """  @override
  Widget build(BuildContext context) {
    final root = repository is OfflineNovelRepository
        ? (repository as OfflineNovelRepository).remote : repository;
    if (root is DualSourceRepository) {
      return ValueListenableBuilder<BookSource>(
        valueListenable: root.changes,
        builder: (context, source, _) => _buildPage(context, source),
      );
    }
    return _buildPage(context, BookSource.wenku8);
  }

  Widget _buildPage(BuildContext context, BookSource source) {
    final isBili = source == BookSource.linovelib;
    final features = WenkuFeaturesScope.maybeOf(context);""")
patch("lib/my_page.dart",
      "                    onTap: account == null\n"
      "                        ? null\n"
      "                        : () => pushNovelPage<void>(",
      "                    onTap: isBili\n"
      "                        ? () => pushNovelPage<void>(\n"
      "                            context,\n"
      "                            LinovelibWebPage(\n"
      "                              title: '登入嗶哩輕小說',\n"
      "                              url: LinovelibWebPage.login(),\n"
      "                            ),\n"
      "                            reduceMotion: state.reduceMotion,\n"
      "                          )\n"
      "                        : account == null\n"
      "                        ? null\n"
      "                        : () => pushNovelPage<void>(")
patch("lib/my_page.dart",
      "                          child: account?.authenticated == true",
      "                          child: !isBili && account?.authenticated == true")
patch("lib/my_page.dart",
      "                                account?.authenticated == true\n"
      "                                     ? profile?.displayName ?? '文库账号'\n"
      "                                     : '登录文库账号',",
      "                                 isBili ? '登入嗶哩輕小說'\n"
      "                                   : account?.authenticated == true\n"
      "                                       ? profile?.displayName ?? '文库账号'\n"
      "                                       : '登录文库账号',")
patch("lib/my_page.dart",
      "                                account?.authenticated == true\n"
      "                                     ? profile == null",
      "                                 isBili ? '嗶哩輕小說帳號 · 網站登入與書評互動'\n"
      "                                   : account?.authenticated == true\n"
      "                                     ? profile == null")

patch("pubspec.yaml","version: 0.4.5+95","version: 0.4.6+96")
patch("android/app/build.gradle.kts",
      'applicationIdSuffix = ".fold_reader_v4"',
      'applicationIdSuffix = ".fold_reader_v5"')
patch("android/app/build.gradle.kts",
      '"轻小说文库·書源修復"',
      '"轻小说文库·嗶哩閱讀"')

Path('test/bili_reader_and_community_test.dart').write_text(r"""
import 'package:flutter_test/flutter_test.dart';
import 'package:wenku8_flutter/linovelib_chapter_decoder.dart';
import 'package:wenku8_flutter/reader_content.dart';
import 'package:wenku8_flutter/linovelib_web_page.dart';

void main() {
  test('chapter parser drops partial-site failure marker', () {
    final result = LinovelibChapterDecoder.parse('''
      <div id="acontent">
        <p>第一段</p><p>第二段……（內容加載失敗！請刷新或更換瀏覽器）</p>
      </div>
      <script>var ReadParams = {url_next: "/novel/3247/61958_2.html"};</script>
    ''', chapterId:61958,baseUrl:'https://www.bilinovel.com/novel/3247/61958.html');
    expect(result.hasFailureMarker, true);
    expect(result.blocks.join(''), isNot(contains('內容加載失敗')));
    expect(result.nextUrl, '/novel/3247/61958_2.html');
  });

  test('chapter parser preserves inline images without duplicated text', () {
    final page=LinovelibChapterDecoder.parse('''
      <div id="acontent"><p>之前
        <img data-src="https://img3.readpai.com/abc.jpg">之後</p></div>
    ''',chapterId:61958,baseUrl:'https://www.bilinovel.com/novel/3247/61958.html');
    expect(page.blocks,contains('<!--image-->https://img3.readpai.com/abc.jpg<!--image-->'));
    expect(page.blocks.join(''),contains('之前'));
    expect(page.blocks.join(''),contains('之後'));
  });

  test('trusted Linovelib inline illustration is recognized by reader', () {
    expect(readerIllustrationUrl(
      '<!--image-->https://img3.readpai.com/illustration.jpg<!--image-->'
    ),'https://img3.readpai.com/illustration.jpg');
    expect(readerIllustrationUrl(
      '<!--image-->https://www.bilinovel.com/files/a.jpg<!--image-->'
    ),'https://www.bilinovel.com/files/a.jpg');
  });

  test('Bili reviews and login use website, not Wenku8 endpoints', () {
    expect(LinovelibWebPage.reviews(1200003247),
      'https://tw.linovelib.com/reviews_3247_1.html');
    expect(LinovelibWebPage.login(), 'https://tw.linovelib.com/login.php');
    expect(LinovelibWebPage.chapter(1200003247, 61958),
      'https://tw.linovelib.com/novel/3247/61958.html');
  });
}
""")
print('Bili chapter/image/reviews/account patch complete')
