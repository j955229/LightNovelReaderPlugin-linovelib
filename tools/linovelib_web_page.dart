import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'app_ui.dart';
import 'feature_pages.dart';

/// Official website session stays inside Android WebView's cookie store.
class LinovelibWebPage extends StatefulWidget {
  const LinovelibWebPage({
    super.key,
    required this.url,
    required this.title,
  });

  static String reviews(int id) =>
      'https://tw.linovelib.com/reviews_${id - 1200000000}_1.html';
  static String login() => 'https://tw.linovelib.com/login.php';
  static String chapter(int id, int cid) =>
      'https://tw.linovelib.com/novel/${id - 1200000000}/$cid.html';

  final String title;
  final String url;

  @override
  State<LinovelibWebPage> createState() => _LinovelibWebPageState();
}

class _LinovelibWebPageState extends State<LinovelibWebPage> {
  late final WebViewController controller;
  bool loading = true;
  String? failure;

  static bool allowed(Uri uri) =>
      uri.scheme == 'https' &&
      const {
        'tw.linovelib.com', 'www.linovelib.com',
        'www.bilinovel.com', 'w.linovelib.com',
        'm.linovelib.com',
      }.contains(uri.host);

  @override
  void initState() {
    super.initState();
    final address = Uri.parse(widget.url);
    if (!allowed(address)) {
      failure = '不支持的书源网址';
      return;
    }
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(NavigationDelegate(
        onNavigationRequest: (request) {
          final uri = Uri.tryParse(request.url);
          return uri != null && allowed(uri)
              ? NavigationDecision.navigate : NavigationDecision.prevent;
        },
        onPageStarted: (_) {
          if (mounted) setState(() {
            loading = true;
            failure = null;
          });
        },
        onPageFinished: (_) {
          if (mounted) setState(() => loading = false);
        },
        onWebResourceError: (error) {
          if (error.isForMainFrame == true && mounted) {
            setState(() {
              loading = false;
              failure = '页面载入失败：${error.description}';
            });
          }
        },
      ))
      ..loadRequest(address);
  }

  @override
  Widget build(BuildContext context) => ArchiveFeaturePage(
    title: widget.title,
    code: 'LINOVELIB / WEB',
    actions: [
      if (failure != '不支持的书源网址')
        GlassIconButton(
          key: const ValueKey('linovelib-web-refresh'),
          icon: CupertinoIcons.refresh,
          label: '刷新网页',
          onPressed: () => controller.reload(),
        ),
    ],
    child: Stack(children: [
      if (failure == '不支持的书源网址')
        Center(child: AppText(failure!))
      else
        WebViewWidget(controller: controller),
      if (loading && failure == null)
        const Align(
          alignment: Alignment.topCenter,
          child: LinearProgressIndicator(minHeight: 2),
        ),
      if (failure != null && failure != '不支持的书源网址')
        Positioned(
          bottom: 12, left: 16, right: 16,
          child: Material(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: AppText(failure!, maxLines: 3),
            ),
          ),
        ),
    ]),
  );
}
