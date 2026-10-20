from pathlib import Path

def sub(path, old, new, expected=1):
    p=Path(path)
    s=p.read_text()
    actual=s.count(old)
    if actual != expected:
        raise RuntimeError(f"{path}: expected {expected}, got {actual}: {old[:110]!r}")
    p.write_text(s.replace(old,new,expected))

Path('lib/linovelib_cover.dart').write_text(
    Path('../tools/linovelib_cover.dart').read_text())
Path('lib/linovelib_search_client.dart').write_text(
    Path('../tools/linovelib_search_client.dart').read_text())

sub('lib/linovelib_repository.dart',
    "import 'tag_catalogue.dart';",
    "import 'tag_catalogue.dart';\n"
    "import 'linovelib_search_client.dart';")

sub('lib/linovelib_repository.dart',
    "  final _http = HttpClient();",
    "  final _http = HttpClient();\n"
    "  final _search = LinovelibSearchClient();")

start='  @override\n  Future<NovelPage> searchPage(String query, {int page = 1, bool refresh = false}) async {'
end='  @override\n  Future<NovelPage> searchFilteredPage('
path=Path('lib/linovelib_repository.dart')
s=path.read_text()
a=s.index(start,s.index('class LinovelibRepository extends NovelRepository'))
b=s.index(end,a)
s=s[:a]+"""  @override
  Future<NovelPage> searchPage(String query, {
      int page = 1, bool refresh = false}) async {
    final word = query.trim();
    if (word.isEmpty) return browse(page: page, refresh: refresh);
    if (page > 1) return NovelPage(items: const [], page: page, totalPages: page);
    final direct = int.tryParse(word) ??
      int.tryParse(RegExp(r'/novel/(\\d+)').firstMatch(word)?.group(1) ?? '');
    if (direct != null && direct > 0) {
      final book = await find(idOffset + direct);
      return NovelPage(items: book == null ? [] : [book],
          page: 1, totalPages: 1);
    }
    // The site issues a fresh search ticket per request. A plain GET is
    // generally rejected; surfacing that failure lets the reader retry.
    final response = await _search.search(word);
    var books = parseList(response.body);
    if (books.isEmpty) {
      final redirected = RegExp(r'/novel/(\\d+)(?:\\.html|/catalog)?')
          .firstMatch(response.url.path)?.group(1);
      if (redirected != null) {
        final book = await find(idOffset + int.parse(redirected));
        if (book != null) books = [book];
      }
    }
    // A challenge page must not pretend the query had zero matches.
    if (books.isEmpty && (response.body.contains('search_guard') ||
        response.body.contains('Just a moment'))) {
      throw const FormatException('嗶哩輕小說搜尋驗證失敗，請重試');
    }
    return NovelPage(items: books, page: 1, totalPages: 1,
        totalItems: books.length);
  }

"""+s[b:]
path.write_text(s)

start='  @override\n  Future<NovelPage> searchFilteredPage('
p=Path('lib/linovelib_repository.dart')
s=p.read_text()
a=s.index(start,s.index('class LinovelibRepository extends NovelRepository'))
# this is last method in class
s=s[:a]+"""  @override
  Future<NovelPage> searchFilteredPage(String query, {
      BookSearchField field = BookSearchField.all,
      int page = 1, bool refresh = false}) async {
    final remote = await searchPage(query, page: page, refresh: refresh);
    if (field == BookSearchField.all) return remote;
    final pattern = BookSearch(query, field: field);
    final filtered = remote.items.where((e) => pattern.matches(
      title: e.title, author: e.author, summary: e.summary,
      tags: e.genre, id: (e.id - idOffset).toString(),
    )).toList();
    return NovelPage(items: filtered, page: page, totalPages: page,
        totalItems: filtered.length);
  }
}
"""
p.write_text(s)

sub('lib/app_ui.dart',
    "import 'legacy_cover.dart';",
    "import 'legacy_cover.dart';\n"
    "import 'linovelib_cover.dart';\n"
    "import 'linovelib_repository.dart';")

sub('lib/app_ui.dart',
    "imageProvider: switch (LocalBookMedia.peek(novel.coverUrl) ??\n"
    "              LegacyCover.peek(novel.id)) {",
    "imageProvider: switch (LocalBookMedia.peek(novel.coverUrl) ??\n"
    "              (LinovelibRepository.owns(novel.id)\n"
    "                  ? LinovelibCover.peek(novel.coverUrl)\n"
    "                  : LegacyCover.peek(novel.id))) {")

sub('lib/app_ui.dart',
    "                future: novel.coverUrl?.startsWith('wenku-local:') == true\n"
    "                    ? LocalBookMedia.loadCover(novel.coverUrl!)\n"
    "                    : LegacyCover.bytes(novel.id),",
    "                future: novel.coverUrl?.startsWith('wenku-local:') == true\n"
    "                    ? LocalBookMedia.loadCover(novel.coverUrl!)\n"
    "                    : LinovelibRepository.owns(novel.id)\n"
    "                        ? LinovelibCover.bytes(novel.coverUrl!)\n"
    "                        : LegacyCover.bytes(novel.id),")

sub('lib/app_ui.dart',
    "                    LocalBookMedia.peek(novel.coverUrl) ??\n"
    "                    LegacyCover.peek(novel.id),",
    "                    LocalBookMedia.peek(novel.coverUrl) ??\n"
    "                    (LinovelibRepository.owns(novel.id)\n"
    "                        ? LinovelibCover.peek(novel.coverUrl)\n"
    "                        : LegacyCover.peek(novel.id)),")

sub('lib/cover_images.dart',
    "import 'legacy_cover.dart';",
    "import 'legacy_cover.dart';\n"
    "import 'linovelib_repository.dart';\n"
    "import 'linovelib_cover.dart';")

sub('lib/cover_images.dart',
    "    ReaderIllustrationCache(request: (_) => LegacyCover.bytes(book.id));",
    "    ReaderIllustrationCache(request: (url) =>\n"
    "      LinovelibRepository.owns(book.id)\n"
    "        ? LinovelibCover.bytes(url)\n"
    "        : LegacyCover.bytes(book.id));")

sub('pubspec.yaml', 'version: 0.4.4+94', 'version: 0.4.5+95')
sub('android/app/build.gradle.kts',
    'applicationIdSuffix = ".fold_reader_v3"',
    'applicationIdSuffix = ".fold_reader_v4"')
sub('android/app/build.gradle.kts',
    '"轻小说文库·书源导航"',
    '"轻小说文库·書源修復"')

Path('test/linovelib_network_fix_test.dart').write_text(r"""
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wenku8_flutter/catalog.dart';
import 'package:wenku8_flutter/app_ui.dart';
import 'package:wenku8_flutter/linovelib_cover.dart';
import 'package:wenku8_flutter/linovelib_repository.dart';
import 'package:wenku8_flutter/linovelib_search_client.dart';

void main() {
  test('Guarded search completes all four HTTP requests with exact cookies',
      () async {
    final visited = <String>[];
    final client = LinovelibSearchClient(
      simplify: (word) async => '奇幻生活',
      request: (uri, cookies) async {
        visited.add(uri.queryParameters['search_guard'] ??
          uri.queryParameters['searchkey'] ?? '');
        switch (visited.length) {
          case 1:
            expect(cookies, isEmpty);
            return LinovelibWebReply(url: uri,
              body: 'document.cookie="jieqiSearchJs=abc123; path=/"');
          case 2:
            return LinovelibWebReply(url: uri, body: '',
              cookies: {'jieqiSearchCss':'css456'});
          case 3:
            expect(cookies['jieqiSearchJs'], 'abc123');
            expect(cookies['jieqiSearchCss'], 'css456');
            expect(cookies['night'], '0');
            return LinovelibWebReply(url: uri, body: '',
              cookies: {'jieqiSearchTicket':'ticket789'});
          case 4:
            expect(cookies, {'jieqiSearchTicket':'ticket789'});
            return LinovelibWebReply(url: uri,
              body: '<ol class="book-ol"><li class="book-li">'
                    '<a class="book-layout" href="/novel/3095.html">'
                    '<span class="book-title">奇幻生活</span></a></li></ol>');
          default:
            fail('Unexpected request');
        }
      },
    );
    final result = await client.search('奇幻生活');
    expect(result.body, contains('/novel/3095.html'));
    expect(visited, ['js', 'css', 'redeem', '奇幻生活']);
  });

  test('covers use external URLs, not Wenku8 integer native IDs', () async {
    const url = 'https://tw.linovelib.com/files/article/image/3/3095/3095s.jpg';
    expect(LinovelibRepository.owns(1200003095), true);
    expect(LinovelibRepository.owns(3095), false);
    expect(LinovelibCover.peek(url), isNull);
  });

  testWidgets('external cover can render from its own cached bytes',
      (tester) async {
    const url = 'https://tw.linovelib.com/files/article/image/3/3095/3095s.jpg';
    // A 1x1 RGBA PNG supplies an encoded image without network access.
    LinovelibCover.seed(url, base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ'
      'AAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==',
    ));
    const novel = Novel(
      id: 1200003095, title: '測試書籍', author: '作者',
      genre: '輕小說', summary: '',
      colors: [Colors.white], coverUrl: url,
    );
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: NovelCover(novel)),
    ));
    await tester.pump();
    expect(find.byType(Image), findsWidgets);
  });
}
""")
print('Applied Linovelib cover and guarded search repairs.')
