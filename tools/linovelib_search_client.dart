import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';

class LinovelibWebReply {
  const LinovelibWebReply({
    required this.url, required this.body, this.cookies = const {},
  });
  final Uri url;
  final String body;
  final Map<String,String> cookies;
}

typedef LinovelibSearchRequest =
    Future<LinovelibWebReply> Function(Uri url, Map<String,String> cookies);

/// Uses the multi-step search ticket exposed by the Linovelib website.
class LinovelibSearchClient {
  LinovelibSearchClient({
    LinovelibSearchRequest? request,
    Future<String> Function(String text)? simplify,
  }) : _request = request ?? _fetch,
       _simplify = simplify ?? LinovelibSearchConverter.toSimplified;

  static const host = 'https://www.bilinovel.com';
  final LinovelibSearchRequest _request;
  final Future<String> Function(String) _simplify;

  static String? jsGuardCookie(String body) => RegExp(
    r'''(?:^|["'])jieqiSearchJs=([^;"']+)'''
  ).firstMatch(body)?.group(1);

  Future<LinovelibWebReply> search(String keyword) async {
    final trimmed = keyword.trim();
    if (trimmed.isEmpty) {
      throw const FormatException('搜尋關鍵字不可為空');
    }
    final js = await _request(
      Uri.parse(host + '/search.html?search_guard=js'), const {});
    final jsToken = jsGuardCookie(js.body);
    if (jsToken == null || jsToken.isEmpty) {
      throw const FormatException('搜尋驗證第一階段失敗');
    }
    final css = await _request(
      Uri.parse(host + '/search.html?search_guard=css'), const {});
    final cssToken = css.cookies['jieqiSearchCss'];
    if (cssToken == null || cssToken.isEmpty) {
      throw const FormatException('搜尋驗證第二階段失敗');
    }
    final redeemUrl = Uri.parse(host + '/search.html').replace(
        queryParameters: {
          'search_guard': 'redeem',
          'r': DateTime.now().millisecondsSinceEpoch.toString(),
        });
    final redeem = await _request(redeemUrl, {
      'night': '0', 'jieqiSearchJs': jsToken,
      'jieqiSearchCss': cssToken,
    });
    final ticket = redeem.cookies['jieqiSearchTicket'];
    if (ticket == null || ticket.isEmpty) {
      throw const FormatException('搜尋驗證票證取得失敗');
    }
    final term = await _simplify(trimmed);
    return _request(Uri.parse(host + '/search.html').replace(
      queryParameters: {'searchkey': term},
    ), {'jieqiSearchTicket': ticket});
  }

  static Future<LinovelibWebReply> _fetch(
      Uri url, Map<String,String> cookies) async {
    if (url.scheme != 'https' || url.host != 'www.bilinovel.com') {
      throw const FormatException('不支援的搜尋網址');
    }
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 15);
    try {
      final request = await client.getUrl(url)
          .timeout(const Duration(seconds: 18));
      request.headers.set(HttpHeaders.userAgentHeader,
        'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 '
        '(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36');
      request.headers.set(HttpHeaders.refererHeader, host + '/');
      request.headers.set(HttpHeaders.acceptHeader, 'text/html,*/*;q=0.8');
      request.headers.set(HttpHeaders.acceptLanguageHeader, 'zh-CN,zh;q=0.9');
      request.headers.set(HttpHeaders.cacheControlHeader, 'no-cache');
      if (cookies.isNotEmpty) {
        request.headers.set(HttpHeaders.cookieHeader,
          cookies.entries.map((e) => e.key + '=' + e.value).join('; '));
      }
      final response = await request.close()
          .timeout(const Duration(seconds: 25));
      if (response.statusCode != 200) {
        await response.drain<void>();
        throw HttpException('搜尋 HTTP ' + response.statusCode.toString(),uri:url);
      }
      final cookieMap = {
        for (final cookie in response.cookies) cookie.name: cookie.value,
      };
      final body = await response.transform(utf8.decoder).join()
        .timeout(const Duration(seconds: 25));
      return LinovelibWebReply(
        url: response.redirects.isNotEmpty
          ? response.redirects.last.location : url,
        body: body,
        cookies: cookieMap,
      );
    } finally {
      client.close(force:true);
    }
  }
}

/// Reverse OpenCC dictionaries bundled with the Wenku8 application.
abstract final class LinovelibSearchConverter {
  static Future<Map<String,String>>? _dictionary;
  static Future<Map<String,String>> _read() => _dictionary ??= () async {
    final result = <String,String>{};
    for (final filename in const ['STCharacters', 'STPhrases']) {
      final data = await rootBundle.loadString('assets/opencc/' + filename + '.txt');
      for (final line in data.split('\n')) {
        if (line.isEmpty || line.startsWith('#')) continue;
        final parts = line.trim().split('\t');
        if (parts.length != 2) continue;
        final simple = parts[0];
        final traditional = parts[1].split(' ').first;
        if (traditional.isNotEmpty && simple.isNotEmpty) {
          result[traditional] = simple;
        }
      }
    }
    return result;
  }();

  static Future<String> toSimplified(String text) async {
    if (text.isEmpty) return text;
    Map<String,String> dict;
    try {
      dict = await _read();
    } catch (_) {
      return text;
    }
    final out = StringBuffer();
    for (var i=0;i<text.length;) {
      final length = (text.length - i).clamp(0, 12);
      var found = false;
      for (var size=length;size>=1;size--) {
        final part=text.substring(i,i+size);
        if (dict[part] case final mapped?) {
          out.write(mapped);
          i += size;
          found = true;
          break;
        }
      }
      if (!found) out.write(text[i++]);
    }
    return out.toString();
  }
}
