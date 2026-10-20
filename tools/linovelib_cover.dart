import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

/// Separate cover loader for external HTTPS sources. Wenku8 covers continue
/// using their original Android MethodChannel.
class LinovelibCover {
  LinovelibCover._();

  static final Map<String, Uint8List> _cache = {};
  static final Map<String, Future<Uint8List>> _pending = {};
  static int _byteCount = 0;
  static const _maxBytes = 18 * 1024 * 1024;
  static const _maxEntries = 180;

  static Uint8List? peek(String? url) =>
      url == null ? null : _cache[url];

  static void seed(String url, Uint8List bytes) {
    final old = _cache.remove(url);
    if (old != null) _byteCount -= old.length;
    _cache[url] = bytes;
    _byteCount += bytes.length;
  }


  static Future<Uint8List> bytes(String url) {
    if (_cache[url] case final found?) return Future.value(found);
    return _pending.putIfAbsent(url, () async {
      try {
        final result = await fetch(url);
        final existing = _cache.remove(url);
        if (existing != null) _byteCount -= existing.length;
        if (result.length <= _maxBytes) {
          _cache[url] = result;
          _byteCount += result.length;
          while (_byteCount > _maxBytes || _cache.length > _maxEntries) {
            final key = _cache.keys.first;
            _byteCount -= _cache.remove(key)!.length;
          }
        }
        return result;
      } finally {
        _pending.remove(url);
      }
    });
  }

  static Future<Uint8List> fetch(String address) async {
    final uri = Uri.parse(address);
    if (uri.scheme != 'https' ||
        !const ['tw.linovelib.com', 'www.linovelib.com',
                'www.bilinovel.com'].contains(uri.host)) {
      throw const FormatException('封面網址來源不正確');
    }
    final client = HttpClient();
    client.connectionTimeout = const Duration(seconds: 15);
    try {
      final request = await client.getUrl(uri).timeout(const Duration(seconds: 16));
      request.headers.set(HttpHeaders.userAgentHeader,
          'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 '
          '(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36');
      request.headers.set(HttpHeaders.refererHeader,
          uri.host == 'www.bilinovel.com'
              ? 'https://www.bilinovel.com/'
              : 'https://tw.linovelib.com/');
      request.headers.set(HttpHeaders.acceptHeader, 'image/avif,image/webp,image/jpeg,image/png,image/*;q=0.8');
      final response = await request.close().timeout(const Duration(seconds: 20));
      if (response.statusCode != 200) {
        await response.drain<void>();
        throw HttpException('封面 HTTP ' + response.statusCode.toString(), uri: uri);
      }
      final buffer = BytesBuilder(copy: false);
      await for (final chunk in response.timeout(const Duration(seconds: 20))) {
        buffer.add(chunk);
        if (buffer.length > 8 * 1024 * 1024) {
          throw const FormatException('封面圖片過大');
        }
      }
      final data = buffer.takeBytes();
      if (data.isEmpty) throw const FormatException('封面圖片為空');
      return data;
    } finally {
      client.close(force: true);
    }
  }
}
