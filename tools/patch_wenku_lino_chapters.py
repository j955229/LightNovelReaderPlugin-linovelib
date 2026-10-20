from pathlib import Path

def change(path, before, after, expected=1):
    p=Path(path)
    body=p.read_text()
    got=body.count(before)
    if got!=expected:
        raise RuntimeError(f"{path} expected {expected}, found {got}: {before[:100]!r}")
    p.write_text(body.replace(before,after,expected))

Path('lib/linovelib_chapter_parser.dart').write_text(
    Path('../tools/linovelib_chapter_parser.dart').read_text())

change('lib/linovelib_repository.dart',
       "import 'linovelib_search_client.dart';",
       "import 'linovelib_search_client.dart';\n"
       "import 'linovelib_chapter_parser.dart';")

# Inject fixed HTML responses for deterministic pagination/integrity tests.
change('lib/linovelib_repository.dart',
       "  LinovelibRepository();",
       "  LinovelibRepository({Future<String> Function(String)? htmlLoader})\n"
       "      : _htmlLoader = htmlLoader;\n"
       "  final Future<String> Function(String)? _htmlLoader;")
change('lib/linovelib_repository.dart',
       "  Future<String> requestHtml(String address) async {\n"
       "    final url = Uri.parse(address);",
       "  Future<String> requestHtml(String address) async {\n"
       "    if (_htmlLoader case final loader?) return loader(address);\n"
       "    final url = Uri.parse(address);")

p=Path('lib/linovelib_repository.dart')
data=p.read_text()
start=data.index("  @override\n  Future<String> chapterContent(int id, NovelChapter chapter) async {",
                 data.index("class LinovelibRepository extends NovelRepository"))
end=data.index("  @override\n  Future<List<Novel>> search(",start)
rewrite="""  @override
  Future<String> chapterContent(int id, NovelChapter chapter) async {
    final book = id - idOffset;
    var address = chapterSite + '/novel/' + book.toString() + '/' +
        chapter.id.toString() + '.html';
    final visited = <String>{};
    final blocks = <String>[];

    for (var page = 0; page < 64; page++) {
      if (!visited.add(address)) {
        throw const FormatException('章節分頁連結重複，無法確認內容完整性');
      }
      LinovelibChapterPage? result;
      Object? failure;
      for (var retry = 0; retry < 3; retry++) {
        try {
          final html = await requestHtml(address);
          result = LinovelibChapterParser.parse(
              html, chapter.id, base: chapterSite);
          break;
        } catch (error) {
          failure = error;
          if (retry < 2) {
            await Future<void>.delayed(Duration(milliseconds: 350 * (retry + 1)));
          }
        }
      }
      if (result == null) {
        throw FormatException('章節第' + (page + 1).toString() +
            '頁載入失敗：' + failure.toString());
      }
      blocks.addAll(result.blocks);
      if (!result.hasNext) {
        final content = blocks.join('\\n\\n').trim();
        if (content.length < 20) {
          throw const FormatException('章節正文為空');
        }
        return content;
      }
      address = result.nextUrl;
    }
    throw const FormatException('章節超過最大分頁數，請重試');
  }

"""
p.write_text(data[:start]+rewrite+data[end:])

# Guard input URLs for actual external illustration host (the same loader
# used by external book covers) and retain original transport for Wenku8.
# Review and login use their own WebView sessions.

# Accurate static types preserve access to the existing update and account
# services after splitting the MyPage builder for source-aware labels.
change('lib/my_page.dart',
       "  Widget _body(BuildContext context, dynamic features, dynamic updates,\n"
       "      bool isLinovelib) {",
       "  Widget _body(BuildContext context, WenkuFeatures? features,\n"
       "      AppUpdates? updates, bool isLinovelib) {")

Path('test/linovelib_chapter_integrity_test.dart').write_text(r"""
import 'package:flutter_test/flutter_test.dart';
import 'package:wenku8_flutter/catalog.dart';
import 'package:wenku8_flutter/linovelib_repository.dart';
import 'package:wenku8_flutter/linovelib_chapter_parser.dart';

void main() {
  test('rejects website load-failure marker instead of inserting it into a novel', () {
    expect(() => LinovelibChapterParser.parse(
      '<div id="acontent"><p>完整句子。</p><p>內容加載失敗！請刷新或更換瀏覽器</p></div>',
      123,
    ), throwsFormatException);
  });

  test('image is in source order between paragraphs and is fully qualified', () {
    final page = LinovelibChapterParser.parse(
      '<div id="acontent"><p>第一段</p><p>中間<img data-src="/img/test.png">後段</p></div>',
      123,
    );
    expect(page.blocks, [
      '第一段','中間',
      '<!--image-->https://www.bilinovel.com/img/test.png<!--image-->',
      '後段',
    ]);
  });

  test('all same-chapter pages are joined without inserting error markers', () async {
    final requested = <String>[];
    final pages = {
      'https://www.bilinovel.com/novel/3247/301.html':
        '<div id="acontent"><p>第一頁的內容，完整。</p></div>'
        '<script>var ReadParams={url_next:"/novel/3247/301_2.html"};</script>',
      'https://www.bilinovel.com/novel/3247/301_2.html':
        '<div id="acontent"><p>第二頁的內容，完整。</p></div>'
        '<script>var ReadParams={url_next:"/novel/3247/302.html"};</script>',
    };
    final repository = LinovelibRepository(htmlLoader: (url) async {
      requested.add(url);
      return pages[url]!;
    });
    final result = await repository.chapterContent(1200003247,
      const NovelChapter('測試章節', '', id:301,volumeId:1,volumeTitle:'卷一'));
    expect(result, contains('第一頁的內容'));
    expect(result, contains('第二頁的內容'));
    expect(requested, hasLength(2));
  });

  test('incomplete second page never returns first-page text', () async {
    final repository = LinovelibRepository(htmlLoader: (url) async {
      if (url.endsWith('301.html')) {
        return '<div id="acontent"><p>第一頁的內容，完整。</p></div>'
          '<script>var ReadParams={url_next:"/novel/3247/301_2.html"};</script>';
      }
      return '<div id="acontent"><p>內容加載失敗！請刷新或更換瀏覽器</p></div>';
    });
    await expectLater(
      repository.chapterContent(1200003247,
        const NovelChapter('測試章節', '', id:301,volumeId:1,volumeTitle:'卷一')),
      throwsFormatException,
    );
  });

  test('paragraph permutation restoration reproduces original order', () {
    const chapterId=3105;
    final original=List<int>.generate(46,(i)=>i);
    final permutation=List<int>.generate(46,(i)=>i);
    var seed=chapterId*126+232;
    for (var i=45;i>20;i--) {
      seed=(seed*9302+49397)%233280;
      final swap=20+(seed/233280*(i-20+1)).floor();
      final v=permutation[i];
      permutation[i]=permutation[swap];
      permutation[swap]=v;
    }
    final shuffled=List<int>.generate(46,(i)=>original[permutation[i]]);
    expect(LinovelibChapterParser.restoreOrder(chapterId,shuffled),original);
  });
}
""")
print('Linovelib complete chapter and reader illustration changes applied.')
