import 'package:html/parser.dart' as html_parser;
import 'package:html/dom.dart' as dom;

class LinovelibParsedPage {
  const LinovelibParsedPage({
    required this.blocks,
    required this.nextUrl,
    required this.hasFailureMarker,
  });
  final List<String> blocks;
  final String nextUrl;
  final bool hasFailureMarker;
}

/// Reuses the paragraph permutation documented by the Linovelib scraper.
/// The web page places the first 20 paragraphs in sequence and shuffles
/// subsequent paragraphs based on the chapter's numeric ID.
class LinovelibChapterDecoder {
  static const failureMarkers = [
    '內容加載失敗', '内容加载失败',
    '請刷新或更換瀏覽器', '请刷新或更换浏览器',
    '請重載或更換瀏覽器',
  ];

  static List<T> restore<T>(int chapterId, List<T> shuffled) {
    if (shuffled.length <= 20) return shuffled;
    final permutation = List<int>.generate(shuffled.length, (i) => i);
    var seed = chapterId * 126 + 232;
    for (var index = shuffled.length - 1; index > 20; index--) {
      seed = (seed * 9302 + 49397) % 233280;
      final swap = 20 + (seed / 233280 * (index - 20 + 1)).floor();
      final tmp = permutation[index];
      permutation[index] = permutation[swap];
      permutation[swap] = tmp;
    }
    final restored = List<T>.from(shuffled);
    for (var i = 0; i < shuffled.length; i++) {
      restored[permutation[i]] = shuffled[i];
    }
    return restored;
  }

  static LinovelibParsedPage parse(
    String html,
    {required int chapterId, required String baseUrl}
  ) {
    final doc = html_parser.parse(html);
    final content = doc.querySelector('#acontent');
    if (content == null) throw const FormatException('找不到章節正文');
    for (final el in content.querySelectorAll('.cgo, center, ins, script, style')) {
      el.remove();
    }
    final elements = content.children.toList();
    final paragraphPositions = [
      for (var i = 0; i < elements.length; i++)
        if (elements[i].localName == 'p' &&
            elements[i].innerHtml.trim().isNotEmpty) i,
    ];
    final ordered = List<dom.Element>.from(elements);
    final restored = restore(chapterId, [
      for (final i in paragraphPositions) elements[i],
    ]);
    for (var i = 0; i < paragraphPositions.length; i++) {
      ordered[paragraphPositions[i]] = restored[i];
    }

    final lines = <String>[];
    var failure = false;
    String clean(String text) {
      var source = text.trim();
      for (final marker in failureMarkers) {
        final pos = source.indexOf(marker);
        if (pos >= 0) {
          failure = true;
          source = source.substring(0, pos).trim();
        }
      }
      return source.replaceAll(RegExp(r'(\.{3,}|…{2,})$'), '').trim();
    }

    String? imageUrl(dom.Element element) {
      final raw = [
        element.attributes['data-src'],
        element.attributes['data-original'],
        element.attributes['data-lazy-src'],
        element.attributes['src'],
      ].whereType<String>().firstWhere(
        (v) => v.trim().isNotEmpty && !v.startsWith('data:'),
        orElse: () => '',
      );
      if (raw.isEmpty) return null;
      final absolute = Uri.tryParse(baseUrl)?.resolve(raw);
      if (absolute == null || absolute.scheme != 'https') return null;
      return absolute.toString();
    }

    void addText(String text) {
      final normalized = clean(text);
      if (normalized.isNotEmpty) lines.add(normalized);
    }

    for (final element in ordered) {
      if (element.localName == 'img') {
        final image = imageUrl(element);
        if (image != null) lines.add('<!--image-->' + image + '<!--image-->');
        continue;
      }
      final imgs = element.querySelectorAll('img');
      if (imgs.isEmpty) {
        addText(element.text);
        continue;
      }
      // Do not duplicate inline illustration text by querying all
      // descendant p/img nodes at once.
      final copy = element.clone(true);
      for (final img in copy.querySelectorAll('img')) {
        final image = imageUrl(img);
        img.replaceWith(dom.Text(image == null ? '' :
          '\n<!--image-->' + image + '<!--image-->\n'));
      }
      for (final line in copy.text.split(RegExp(r'\n+'))) {
        if (line.trim().startsWith('<!--image-->')) {
          lines.add(line.trim());
        } else {
          addText(line);
        }
      }
    }

    final scripts = doc.querySelectorAll('script').map((e)=>e.text).join('\n');
    final match = RegExp(
      r'''url_next\s*[:=]\s*["']([^"']+)["']'''
    ).firstMatch(scripts);
    return LinovelibParsedPage(
      blocks: lines,
      nextUrl: match?.group(1) ?? '',
      hasFailureMarker: failure,
    );
  }
}
