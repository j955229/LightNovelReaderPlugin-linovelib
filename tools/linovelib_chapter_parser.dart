import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as html_parser;

class LinovelibChapterPage {
  const LinovelibChapterPage({
    required this.blocks,
    required this.nextUrl,
    required this.hasNext,
  });

  final List<String> blocks;
  final String nextUrl;
  final bool hasNext;
}

/// Chapter parsing and anti-scrambling are independent of the transport.
abstract final class LinovelibChapterParser {
  static const failureMarkers = <String>[
    '內容加載失敗', '内容加载失败',
    '請刷新或更換瀏覽器', '请刷新或更换浏览器',
  ];

  static LinovelibChapterPage parse(String html, int chapterId,
      {String base = 'https://www.bilinovel.com'}) {
    final document = html_parser.parse(html);
    final content = document.querySelector('#acontent');
    if (content == null) {
      throw const FormatException('網站未返回正文內容');
    }
    if (failureMarkers.any((part) =>
        content.text.contains(part) || content.innerHtml.contains(part))) {
      throw const FormatException('網頁正文載入失敗，請重試');
    }
    for (final noise in content.querySelectorAll(
        '.cgo, center, script, style, ins')) {
      noise.remove();
    }
    final children = content.children.toList();
    final paragraphPositions = <int>[];
    for (var index = 0; index < children.length; index++) {
      final e = children[index];
      if (e.localName == 'p' && e.innerHtml.trim().isNotEmpty) {
        paragraphPositions.add(index);
      }
    }
    final original = [for (final i in paragraphPositions) children[i]];
    final restored = restoreOrder(chapterId, original);
    for (var i = 0; i < paragraphPositions.length; i++) {
      children[paragraphPositions[i]] = restored[i];
    }

    final blocks = <String>[];
    for (final root in children) {
      final buf = StringBuffer();

      void flush() {
        final text = buf.toString().trim();
        buf.clear();
        if (text.isNotEmpty) blocks.add(text);
      }

      void visit(dom.Node node) {
        if (node is dom.Text) {
          buf.write(node.text);
          return;
        }
        if (node is! dom.Element) return;
        if (node.classes.contains('cgo') ||
            const ['script','style','ins','center'].contains(node.localName)) {
          return;
        }
        if (node.localName == 'br') {
          buf.write('\n');
          return;
        }
        if (node.localName == 'img') {
          flush();
          final image = node.attributes['data-src'] ??
              node.attributes['data-original'] ??
              node.attributes['src'] ?? '';
          if (image.trim().isNotEmpty) {
            final full = Uri.parse(base).resolve(image.trim()).toString();
            if (Uri.tryParse(full)?.scheme == 'https') {
              blocks.add('<!--image-->' + full + '<!--image-->');
            }
          }
          return;
        }
        for (final child in node.nodes) {
          visit(child);
        }
      }

      visit(root);
      flush();
    }
    if (blocks.isEmpty) throw const FormatException('本頁正文為空');

    final scripts = document.querySelectorAll('script')
        .map((e) => e.text + '\n' + e.innerHtml).join('\n');
    final raw = RegExp(r'''url_next\s*[:=]\s*["']([^"']+)["']''')
        .firstMatch(scripts)?.group(1) ?? '';
    final next = raw.isEmpty ? '' : Uri.parse(base).resolve(raw).toString();
    final parsedChapterId = RegExp(r'/novel/\d+/(\d+)(?:_\d+)?\.html')
        .firstMatch(next)?.group(1);
    return LinovelibChapterPage(
      blocks: List.unmodifiable(blocks),
      nextUrl: next,
      hasNext: parsedChapterId != null &&
          int.tryParse(parsedChapterId) == chapterId,
    );
  }

  /// Reverses the site chapter-seeded paragraph permutation.
  static List<T> restoreOrder<T>(int chapterId, List<T> shuffled) {
    if (shuffled.length <= 20) return List.of(shuffled);
    final permutation = List<int>.generate(shuffled.length, (i) => i);
    var seed = chapterId * 126 + 232;
    for (var i = shuffled.length - 1; i > 20; i--) {
      seed = (seed * 9302 + 49397) % 233280;
      final swap = 20 + (seed / 233280 * (i - 20 + 1)).floor();
      final temp = permutation[i];
      permutation[i] = permutation[swap];
      permutation[swap] = temp;
    }
    final result = List<T>.of(shuffled);
    for (var i = 0; i < shuffled.length; i++) {
      result[permutation[i]] = shuffled[i];
    }
    return result;
  }
}
