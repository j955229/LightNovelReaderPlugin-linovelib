import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as parser;
import 'package:shared_preferences/shared_preferences.dart';
import 'catalog.dart';
import 'book_search.dart';
import 'tag_catalogue.dart';

enum BookSource {
  wenku8('轻小说文库'), linovelib('嗶哩輕小說');
  const BookSource(this.label);
  final String label;
}

/// Stable, collision-free source identifiers, independent of the active tab.
class DualSourceRepository extends NovelRepository
    implements ProgressiveTagCatalogue {
  DualSourceRepository(this.wenku, this.linovelib, this.prefs)
      : selected = prefs.getString('source_choice') == 'linovelib'
          ? BookSource.linovelib : BookSource.wenku8;
  final NovelRepository wenku;
  final LinovelibRepository linovelib;
  final SharedPreferences prefs;
  BookSource selected;
  void choose(BookSource choice) {
    if (selected == choice) return;
    selected = choice;
    unawaited(prefs.setString('source_choice', selected.name));
  }
  NovelRepository get active =>
      selected == BookSource.linovelib ? linovelib : wenku;
  NovelRepository forId(int id) =>
      LinovelibRepository.owns(id) ? linovelib : wenku;
  @override
  Future<List<Novel>> list() => active.list();
  @override
  Future<Novel?> find(int id) => forId(id).find(id);
  @override
  Future<Novel> detail(Novel novel) => forId(novel.id).detail(novel);
  @override
  Future<List<NovelChapter>> chapters(int id) => forId(id).chapters(id);
  @override
  Future<String> chapterContent(int id, NovelChapter c) =>
      forId(id).chapterContent(id, c);
  @override
  Future<List<Novel>> search(String text) => active.search(text);
  @override
  Future<NovelPage> browse({NovelSort sort = NovelSort.lastUpdate,
      int page = 1, bool refresh = false}) =>
      active.browse(sort: sort, page: page, refresh: refresh);
  @override
  Future<NovelPage> searchPage(String query, {int page = 1, bool refresh = false}) =>
      active.searchPage(query, page: page, refresh: refresh);
  @override
  Future<NovelPage> searchFilteredPage(String query, {
    BookSearchField field = BookSearchField.all,
    int page = 1, bool refresh = false}) =>
    active.searchFilteredPage(query, field: field, page: page, refresh: refresh);
  @override
  Future<NovelPage> authorPage(String author, {int page = 1, bool refresh = false}) =>
      active.authorPage(author, page: page, refresh: refresh);
  @override
  Future<NovelPage> tagPage(String tag, {int page = 1, bool refresh = false,
      bool Function()? current}) {
    if (selected == BookSource.wenku8 && wenku is ProgressiveTagCatalogue) {
      return (wenku as ProgressiveTagCatalogue).tagPage(
          tag, page: page, refresh: refresh, current: current);
    }
    return searchFilteredPage(tag, field: BookSearchField.tags,
        page: page, refresh: refresh);
  }
}

class LinovelibRepository extends NovelRepository {
  LinovelibRepository();
  static const site = 'https://tw.linovelib.com';
  static const chapterSite = 'https://www.bilinovel.com';
  static const idOffset = 1200000000;
  static bool owns(int id) => id >= idOffset && id < idOffset + 10000000;
  final _http = HttpClient();
  final _cache = <int, Novel>{};
  final _chapters = <int, List<NovelChapter>>{};
  final _pages = <(NovelSort, int), NovelPage>{};

  String fullUrl(String path, [String origin = site]) {
    if (path.isEmpty) return '';
    final url = Uri.parse(origin).resolve(path);
    return url.scheme == 'https' ? url.toString() : '';
  }

  Future<String> requestHtml(String address) async {
    final url = Uri.parse(address);
    if (url.scheme != 'https' || !const [
      'tw.linovelib.com', 'www.bilinovel.com',
    ].contains(url.host)) {
      throw const FormatException('書源域名錯誤');
    }
    final request = await _http.getUrl(url).timeout(const Duration(seconds: 20));
    request.headers.set(HttpHeaders.userAgentHeader,
      'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36');
    request.headers.set('Accept-Language', 'zh-TW,zh;q=0.9');
    request.headers.set('Referer', (url.host == 'www.bilinovel.com' ? chapterSite : site) + '/');
    final response = await request.close().timeout(const Duration(seconds: 25));
    if (response.statusCode != 200) {
      await response.drain<void>();
      throw HttpException('網站回應 HTTP ' + response.statusCode.toString(), uri: url);
    }
    final raw = await response.fold<List<int>>(<int>[], (a, b) => a..addAll(b));
    return utf8.decode(raw, allowMalformed: true);
  }

  String attribute(dom.Element? e, List<String> names) {
    for (final key in names) {
      final value = e?.attributes[key] ?? '';
      if (value.isNotEmpty) return value.trim();
    }
    return '';
  }

  Novel? linkBook(dom.Element element) {
    final path = attribute(element, ['href']);
    final m = RegExp(r'/novel/(\d+)(?:\.html|/catalog)?').firstMatch(path);
    if (m == null) return null;
    final book = int.parse(m.group(1)!);
    final image = element.querySelector('img');
    var title = element.querySelector('.module-slide-caption, .book-title')
        ?.text.trim() ?? '';
    final alt = attribute(image, ['alt']);
    if (title.isEmpty || title.endsWith('...') || title.endsWith('…')) {
      title = alt.isNotEmpty ? alt : title;
    }
    if (title.isEmpty) title = element.text.trim();
    if (title.isEmpty) return null;
    final author = element.querySelector(
      '.module-slide-author .gray, .book-author, .module-slide-author')
      ?.text.trim() ?? '';
    final imageUrl = fullUrl(attribute(image, ['data-src','data-original','src']));
    final id = idOffset + book;
    final result = Novel(
      id: id, title: title, author: author, genre: '輕小說',
      summary: '', colors: const [Color(0xFFCAC7C0), Color(0xFF767570)],
      coverUrl: imageUrl.isNotEmpty
        ? imageUrl : site + '/files/article/image/' +
           (book ~/ 1000).toString() + '/' + book.toString() + '/' +
           book.toString() + 's.jpg',
    );
    return _cache[id] = result;
  }

  List<Novel> parseList(String html) {
    final document = parser.parse(html);
    final unique = <int, Novel>{};
    for (final e in document.querySelectorAll(
      'ol.book-ol .book-li a.book-layout[href],'
      ' a.book-layout[href], a.module-slide-a[href]')) {
      final book = linkBook(e);
      if (book != null) unique[book.id] = book;
    }
    return unique.values.toList();
  }

  @override
  Future<List<Novel>> list() async => (await browse()).items;

  @override
  Future<NovelPage> browse({NovelSort sort = NovelSort.lastUpdate,
      int page = 1, bool refresh = false}) async {
    if (page < 1) throw ArgumentError.value(page);
    final key = (sort, page);
    if (refresh) _pages.remove(key);
    if (_pages[key] case final cached?) return cached;
    final order = sort == NovelSort.fullFlag ? 'lastupdate' : sort.apiKey;
    final html = await requestHtml(
      site + '/wenku/' + order + '_0_0_0_0_0_0_0_' + page.toString() + '_0.html');
    final books = parseList(html);
    if (books.isEmpty) throw const FormatException('嗶哩輕小說書目載入失敗');
    final lastText = parser.parse(html).querySelector('#pagelink a.last')?.text;
    final maxPage = int.tryParse(lastText?.trim() ?? '');
    return _pages[key] = NovelPage(
      items: books, page: page, totalPages: maxPage ?? (books.length < 15 ? page : null),
    );
  }

  @override
  Future<Novel?> find(int id) async {
    if (!owns(id)) return null;
    if (_cache[id] case final book?) return book;
    return detail(Novel(id: id, title: '', author: '', genre: '',
      summary: '', colors: const [Color(0xFFCECECC), Color(0xFF777777)]));
  }

  @override
  Future<Novel> detail(Novel novel) async {
    final rawId = novel.id - idOffset;
    final doc = parser.parse(await requestHtml(site + '/novel/' + rawId.toString() + '.html'));
    String meta(String key) => doc.querySelector('meta[property="' + key + '"]')
      ?.attributes['content']?.trim() ?? '';
    final title = doc.querySelector('.book-title')?.text.trim() ?? '';
    final author = doc.querySelector('.authorname a')?.text.trim() ?? '';
    final tags = doc.querySelectorAll('.tag-small-group .tag-small')
      .map((e) => e.text.trim()).where((e) => e.isNotEmpty).toSet();
    final summary = doc.querySelector('#bookSummary')?.text.trim() ?? '';
    final resolvedTitle = title.isNotEmpty ? title : meta('og:novel:book_name');
    final result = Novel(
      id: novel.id, title: resolvedTitle.isEmpty ? novel.title : resolvedTitle,
      author: author.isEmpty ? novel.author : author,
      genre: tags.isEmpty ? novel.genre : tags.join(' '),
      summary: summary.isNotEmpty ? summary : (meta('og:description').isNotEmpty
          ? meta('og:description') : novel.summary),
      colors: novel.colors,
      coverUrl: meta('og:image').isNotEmpty
          ? fullUrl(meta('og:image')) : novel.coverUrl,
      isComplete: meta('og:novel:status').contains('完'),
      chapters: _chapters[novel.id] ?? novel.chapters,
    );
    return _cache[novel.id] = result;
  }

  @override
  Future<List<NovelChapter>> chapters(int id) async {
    if (_chapters[id] case final stored?) return stored;
    final doc = parser.parse(await requestHtml(
      site + '/novel/' + (id - idOffset).toString() + '/catalog'));
    final result = <NovelChapter>[];
    var volumeId = 0;
    for (final volume in doc.querySelectorAll('#volumes .catalog-volume')) {
      volumeId++;
      final heading = volume.querySelector('.chapter-bar h3')?.text.trim() ?? '第' + volumeId.toString() + '卷';
      for (final a in volume.querySelectorAll('.jsChapter a[href]')) {
        final match = RegExp(r'/novel/\d+/(\d+)(?:_\d+)?\.html')
          .firstMatch(a.attributes['href'] ?? '');
        if (match == null) continue;
        final chapterId = int.parse(match.group(1)!);
        if (result.any((c) => c.id == chapterId)) continue;
        final name = a.querySelector('.chapter-index')?.text.trim() ?? a.text.trim();
        result.add(NovelChapter(name, '', id: chapterId,
          volumeId: volumeId, volumeTitle: heading));
      }
    }
    if (result.isEmpty) throw const FormatException('嗶哩輕小說章節目錄載入失敗');
    return _chapters[id] = List.unmodifiable(result);
  }

  @override
  Future<String> chapterContent(int id, NovelChapter chapter) async {
    final book = id - idOffset;
    var address = chapterSite + '/novel/' + book.toString() + '/' +
        chapter.id.toString() + '.html';
    final visited = <String>{};
    final parts = <String>[];
    for (var i = 0; i < 12 && visited.add(address); i++) {
      final doc = parser.parse(await requestHtml(address));
      final content = doc.querySelector('#acontent');
      if (content == null) throw const FormatException('正文暫時無法讀取');
      for (final noise in content.querySelectorAll('.cgo, center, ins, script, style')) {
        noise.remove();
      }
      final elements = content.querySelectorAll('p, img');
      if (elements.isEmpty) {
        if (content.text.trim().isNotEmpty) parts.add(content.text.trim());
      } else {
        for (final e in elements) {
          if (e.localName == 'img') {
            final image = fullUrl(attribute(e, ['data-src','data-original','src']), chapterSite);
            if (image.isNotEmpty) parts.add('<!--image-->' + image + '<!--image-->');
          } else {
            final text = e.text.trim();
            if (text.isNotEmpty && !text.contains('內容載入失敗')) parts.add(text);
          }
        }
      }
      final scripts = doc.querySelectorAll('script').map((e) => e.text).join('\n');
      final next = RegExp(r'''url_next\s*[:=]\s*["']([^"']+)["']''')
          .firstMatch(scripts)?.group(1) ?? '';
      if (next.isEmpty) break;
      final candidate = fullUrl(next, chapterSite);
      final match = RegExp(r'/novel/\d+/(\d+)(?:_\d+)?\.html').firstMatch(candidate);
      if (match == null || int.tryParse(match.group(1)!) != chapter.id || address == candidate) break;
      address = candidate;
    }
    final text = parts.join('\n\n');
    if (text.length < 20) throw const FormatException('小說正文為空');
    return text;
  }

  @override
  Future<List<Novel>> search(String query) async => (await searchPage(query)).items;

  @override
  Future<NovelPage> searchPage(String query, {int page = 1, bool refresh = false}) async {
    final word = query.trim();
    if (word.isEmpty) return browse(page: page, refresh: refresh);
    try {
      final url = Uri.parse(site + '/search.html').replace(queryParameters: {'searchkey': word});
      final results = parseList(await requestHtml(url.toString()));
      if (results.isNotEmpty) return NovelPage(
        items: results, page: page, totalPages: page);
    } catch (_) {}
    if (_cache.length < 10) await browse(refresh: refresh);
    return NovelPage.fromItems(_cache.values.where((e) =>
      BookSearch(word).matches(title: e.title, author: e.author,
          summary: e.summary, tags: e.genre, id: (e.id - idOffset).toString())
    ).toList(), page);
  }

  @override
  Future<NovelPage> searchFilteredPage(String query, {
      BookSearchField field = BookSearchField.all,
      int page = 1, bool refresh = false}) async {
    if (field == BookSearchField.all) return searchPage(query, page: page, refresh: refresh);
    if (_cache.isEmpty) await browse();
    final pattern = BookSearch(query, field: field);
    return NovelPage.fromItems(_cache.values.where((e) => pattern.matches(
      title: e.title, author: e.author, summary: e.summary,
      tags: e.genre, id: (e.id - idOffset).toString(),
    )).toList(), page);
  }
}
