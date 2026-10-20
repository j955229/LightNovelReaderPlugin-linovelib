import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'app_ui.dart';

/// Hosted Linovelib pages share one Android WebView cookie store.
/// Authentication remains within the official Linovelib origin.
class LinovelibBrowserPage extends StatefulWidget {
  const LinovelibBrowserPage({
    super.key,
    required this.title,
    required this.url,
  });

  final String title;
  final String url;

  static const loginUrl =
      'https://tw.linovelib.com/login.php?jumpurl=https%3A%2F%2Ftw.linovelib.com%2Fuser.php';

  static String reviewUrl(int id) =>
      'https://tw.linovelib.com/reviews_' + (id - 1200000000).toString() + '_1.html';

  static String bookUrl(int id) =>
      'https://tw.linovelib.com/novel/' + (id - 1200000000).toString() + '.html';

  @override
  State<LinovelibBrowserPage> createState() => _LinovelibBrowserPageState();
}

class _LinovelibBrowserPageState extends State<LinovelibBrowserPage> {
  late final WebViewController controller;
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.white)
      ..setNavigationDelegate(NavigationDelegate(
        onPageStarted: (_) {
          if (mounted) setState(() { loading = true; error = null; });
        },
        onPageFinished: (_) {
          if (mounted) setState(() => loading = false);
        },
        onWebResourceError: (failure) {
          if (!failure.isForMainFrame) return;
          if (mounted) setState(() {
            loading = false;
            error = '網頁載入失敗：' + failure.description;
          });
        },
        onNavigationRequest: (navigation) {
          final url = Uri.tryParse(navigation.url);
          if (url == null || url.scheme != 'https' ||
              !{'tw.linovelib.com', 'www.bilinovel.com',
                  'www.linovelib.com'}.contains(url.host)) {
            return NavigationDecision.prevent;
          }
          return NavigationDecision.navigate;
        },
      ))
      ..loadRequest(Uri.parse(widget.url));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: AppText(widget.title),
      leading: IconButton(
        icon: const Icon(CupertinoIcons.back),
        onPressed: () => Navigator.of(context).pop(),
      ),
      actions: [
        IconButton(
          tooltip: '重新整理',
          onPressed: () => controller.reload(),
          icon: const Icon(CupertinoIcons.refresh),
        ),
      ],
    ),
    body: Stack(
      children: [
        Positioned.fill(child: WebViewWidget(controller: controller)),
        if (loading)
          const Align(
            alignment: Alignment.topCenter,
            child: LinearProgressIndicator(minHeight: 2),
          ),
        if (error case final message?)
          Align(
            alignment: Alignment.center,
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AppText(message, textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: controller.reload,
                    child: const AppText('重試'),
                  ),
                ],
              ),
            ),
          ),
      ],
    ),
  );
}
