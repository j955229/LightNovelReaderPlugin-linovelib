from pathlib import Path

def edit(path, pairs):
    p = Path(path)
    text = p.read_text()
    for before, after in pairs:
        n = text.count(before)
        if n != 1:
            raise RuntimeError(f"{path}: expected 1 match, found {n}: {before[:110]!r}")
        text = text.replace(before, after, 1)
    p.write_text(text)

Path('lib/linovelib_repository.dart').write_text(
    Path('../tools/linovelib_repository.dart').read_text()
)

edit('lib/main.dart', [
    ("import 'legacy_wenku_repository.dart';",
     "import 'legacy_wenku_repository.dart';\nimport 'linovelib_repository.dart';"),
    ('final remote = LegacyWenkuRepository(', 'final legacy = LegacyWenkuRepository('),
    ("  final repository = defaultTargetPlatform == TargetPlatform.android",
     "  final remote = DualSourceRepository(\n"
     "    legacy,\n"
     "    LinovelibRepository(),\n"
     "    await SharedPreferences.getInstance(),\n"
     "  );\n"
     "  final repository = defaultTargetPlatform == TargetPlatform.android"),
    ('          publications: remote,', '          publications: legacy,'),
])

edit('lib/archive_design.dart', [
    ('    this.brand = false,\n', '    this.brand = false,\n    this.brandText = "W8",\n'),
    ('  final bool brand;\n', '  final bool brand;\n  final String brandText;\n'),
    ("                'W8',\n", '                brandText,\n'),
    ("  static String fileCode(int id) => 'WK-", 
     "  static String fileCode(int id) => id >= 1200000000\n"
     "      ? 'BL-' + (id - 1200000000).toString().padLeft(5, '0')\n"
     "      : 'WK-"),
])

edit('lib/pages.dart', [
    ("import 'novel_feed_controller.dart';",
     "import 'novel_feed_controller.dart';\nimport 'linovelib_repository.dart';"),
    ("  late final _searchFeed = NovelFeedController(widget.repository, search: true);",
     "  late final _searchFeed = NovelFeedController(widget.repository, search: true);\n"
     "  late final _linovelibCatalog = NovelFeedController(\n"
     "    widget.repository, initialItems: 60,\n"
     "  );\n"
     "  late final _linovelibSearch = NovelFeedController(widget.repository, search: true);\n"
     "  bool _linovelibStarted = false;\n"
     "  DualSourceRepository? get _dualSource {\n"
     "    final root = widget.repository;\n"
     "    final remote = root is OfflineNovelRepository ? root.remote : root;\n"
     "    return remote is DualSourceRepository ? remote : null;\n"
     "  }\n"),
    ("    _ => _searchFeed,\n  };",
     "    _ => _dualSource?.selected == BookSource.linovelib\n"
     "         ? _linovelibSearch : _searchFeed,\n  };"),
    ("    _searchFeed.addListener(_searchSucceeded);",
     "    _searchFeed.addListener(_searchSucceeded);\n"
     "    _linovelibSearch.addListener(_searchSucceeded);"),
    ("      feed: _catalog,\n      state: widget.state,",
     "      feed: _catalog,\n"
     "      alternativeFeed: _linovelibCatalog,\n"
     "      sources: _dualSource,\n"
     "      onChooseSource: _chooseSource,\n"
     "      state: widget.state,"),
    ("  void _ensureCatalog() {\n"
     "    if (_tab != 0 || !_visibleTabs.contains(0)) return;",
     "  void _ensureCatalog() {\n"
     "    if (_tab != 0 || !_visibleTabs.contains(0)) return;\n"
     "    if (_dualSource?.selected == BookSource.linovelib) {\n"
     "      if (!_linovelibStarted) {\n"
     "        _linovelibStarted = true;\n"
     "        unawaited(_linovelibCatalog.loadInitial());\n"
     "      } else if (_linovelibCatalog.items.isEmpty && _linovelibCatalog.error != null) {\n"
     "        unawaited(_linovelibCatalog.retry());\n"
     "      }\n"
     "      return;\n"
     "    }"),
    ("  void _navigationChanged() {",
     "  void _chooseSource(BookSource choice) {\n"
     "    final sources = _dualSource;\n"
     "    if (sources == null || sources.selected == choice) return;\n"
     "    sources.choose(choice);\n"
     "    setState(() {});\n"
     "    _ensureCatalog();\n"
     "  }\n\n"
     "  void _navigationChanged() {"),
    ("    _searchFeed.dispose();",
     "    _searchFeed.dispose();\n"
     "    _linovelibCatalog.dispose();\n"
     "    _linovelibSearch.dispose();"),
    ("        (_searchFeed.page == 0 && !_searchFeed.loading)) {",
     "        (feed.page == 0 && !feed.loading)) {"),
    ("      _searchFeed.loadInitial();",
     "      feed.loadInitial();"),
    ("  void _searchSucceeded() {\n"
     "    if (_searchScope != 0 ||\n"
     "        _searchFeed.loading ||\n"
     "        _searchFeed.error != null ||\n"
     "        _searchFeed.page < 1) {",
     "  void _searchSucceeded() {\n"
     "    final feed = _activeSearch;\n"
     "    if (_searchScope != 0 ||\n"
     "        feed.loading ||\n"
     "        feed.error != null ||\n"
     "        feed.page < 1) {"),
    ("    final query = _searchFeed.query.trim();",
     "    final query = feed.query.trim();"),
    ("    _searchFeed.searchFor(query);",
     "    _activeSearch.searchFor(query);"),
])

p=Path('lib/pages.dart')
text=p.read_text()
start=text.index('class DiscoverPage extends StatelessWidget {')
end=text.index('/// Pull-to-refresh',start)
sub=text[start:end]
def replace(s, before, after):
    n=s.count(before)
    if n != 1:
        raise RuntimeError(f'DiscoverPage expected one, got {n}: {before[:130]!r}')
    return s.replace(before,after,1)

sub=replace(sub, '    this.feed,\n',
   '    this.feed,\n    this.alternativeFeed,\n    this.sources,\n    this.onChooseSource,\n')
sub=replace(sub, '  final NovelFeedController? feed;\n',
   '  final NovelFeedController? feed;\n'
   '  final NovelFeedController? alternativeFeed;\n'
   '  final DualSourceRepository? sources;\n'
   '  final ValueChanged<BookSource>? onChooseSource;\n'
   '  NovelFeedController? get _selectedFeed =>\n'
   '      sources?.selected == BookSource.linovelib ? alternativeFeed ?? feed : feed;\n')
sub=replace(sub,
   "        child: const ArchiveMasthead(\n"
   "          title: '轻小说文库',\n"
   "          code: 'WENKU8 / READING ARCHIVE',\n"
   "          brand: true,\n"
   "        ),",
   "        child: ArchiveMasthead(\n"
   "          title: sources?.selected == BookSource.linovelib\n"
   "              ? '嗶哩輕小說' : '轻小说文库',\n"
   "          code: sources?.selected == BookSource.linovelib\n"
   "              ? 'LINOVELIB / READING ARCHIVE' : 'WENKU8 / READING ARCHIVE',\n"
   "          brand: true,\n"
   "          brandText: sources?.selected == BookSource.linovelib ? 'BL' : 'W8',\n"
   "        ),")
sub=replace(sub,
   '      if (WenkuFeaturesScope.maybeOf(context) case final features?)',
   '      if (WenkuFeaturesScope.maybeOf(context) case final features?)\n'
   '        if (sources?.selected != BookSource.linovelib)')
sub=replace(sub,
   "      GlassIconButton(\n        icon: CupertinoIcons.search,\n        label: '搜索',",
   "      if (sources != null)\n"
   "        GlassIconButton(\n"
   "          icon: CupertinoIcons.arrow_left_right,\n"
   "          label: '切換書源',\n"
   "          onPressed: () => onChooseSource?.call(\n"
   "            sources!.selected == BookSource.wenku8\n"
   "                ? BookSource.linovelib : BookSource.wenku8,\n"
   "          ),\n"
   "        ),\n"
   "      GlassIconButton(\n        icon: CupertinoIcons.search,\n        label: '搜索',")

sub=replace(sub,
   '  Widget build(BuildContext context) => feed == null\n'
   '      ? _buildContent(context, books)\n'
   '      : ActiveListenableBuilder(\n'
   '          listenable: feed!,\n'
   '          builder: (context) => LoadedContentSequence(\n'
   '            generation: feed!.resultRevision,\n'
   '            firstIndex: feed!.resultStartIndex == 0\n'
   '                ? 0\n'
   '                : feed!.resultStartIndex + 2,\n'
   '            child: _buildContent(context, feed!.items),\n'
   '          ),\n'
   '        );',
   '  Widget build(BuildContext context) {\n'
   '    final current = _selectedFeed;\n'
   '    return current == null\n'
   '        ? _buildContent(context, books)\n'
   '        : ActiveListenableBuilder(\n'
   '            listenable: current,\n'
   '            builder: (context) => LoadedContentSequence(\n'
   '              generation: current.resultRevision,\n'
   '              firstIndex: current.resultStartIndex == 0\n'
   '                  ? 0 : current.resultStartIndex + 2,\n'
   '              child: _buildContent(context, current.items),\n'
   '            ),\n'
   '          );\n'
   '  }')
sub=replace(sub,
   '  Widget _buildContent(BuildContext context, List<Novel> books) {\n'
   '    final featuredCount',
   '  Widget _buildContent(BuildContext context, List<Novel> books) {\n'
   '    final feed = _selectedFeed;\n'
   '    final featuredCount')
sub=replace(sub,
   "                        if (books.length > featuredCount ||\n",
   "                        if (sources != null) ...[\n"
   "                          const SizedBox(height: 12),\n"
   "                          Align(\n"
   "                            alignment: Alignment.centerRight,\n"
   "                            child: ConstrainedBox(\n"
   "                              constraints: const BoxConstraints(maxWidth: 425),\n"
   "                              child: Row(\n"
   "                                children: [\n"
   "                                  const AppText('書源', style: TextStyle(fontSize: 12)),\n"
   "                                  const SizedBox(width: 12),\n"
   "                                  Expanded(child: AppControls.segments<BookSource>(\n"
   "                                    context,\n"
   "                                    groupValue: sources!.selected,\n"
   "                                    children: {\n"
   "                                      for (final item in BookSource.values)\n"
   "                                        item: AppText(item.label,\n"
   "                                          style: const TextStyle(fontSize: 12)),\n"
   "                                    },\n"
   "                                    onValueChanged: (value) {\n"
   "                                      if (value != null) onChooseSource?.call(value);\n"
   "                                    },\n"
   "                                  )),\n"
   "                                ],\n"
   "                              ),\n"
   "                            ),\n"
   "                          ),\n"
   "                        ],\n"
   "                        if (books.length > featuredCount ||\n")
sub=replace(sub,
   '  Widget _categoryHeader(BuildContext context) => Row(\n',
   '  Widget _categoryHeader(BuildContext context) {\n'
   '    final feed = _selectedFeed;\n'
   '    return Row(\n')
idx=sub.index('  Future<void> _chooseSort(')
before=sub[:idx]
assert before.rstrip().endswith(');')
before=before.rstrip()[:-2]+');\n  }\n\n'
sub=before+sub[idx:]
sub=replace(sub,
   '  Future<void> _chooseSort(BuildContext context) async {\n',
   '  Future<void> _chooseSort(BuildContext context) async {\n'
   '    final feed = _selectedFeed;\n')
text=text[:start]+sub+text[end:]
p.write_text(text)

edit('pubspec.yaml', [('version: 0.4.2+90', 'version: 0.4.3+92')])
edit('android/app/build.gradle.kts', [
  ('applicationIdSuffix = ".fold_reader"',
   'applicationIdSuffix = ".fold_reader_v2"'),
  ('"轻小说文库·折叠屏"', '"轻小说文库·双书源"'),
])

Path('test/linovelib_source_test.dart').write_text(r"""
import 'package:flutter_test/flutter_test.dart';
import 'package:wenku8_flutter/linovelib_repository.dart';
import 'package:wenku8_flutter/catalog.dart';

void main() {
  test('Linovelib catalog cards retain exact external titles and source IDs', () {
    final repo = LinovelibRepository();
    final books = repo.parseList('''
       <ol class="book-ol"><li class="book-li">
        <a class="book-layout" href="/novel/3095.html">
          <img data-src="/covers/3095.jpg" alt="敗北女角太多了！">
          <span class="book-title">敗北女角太多了！</span>
          <span class="book-author">雨森焚火</span>
        </a></li></ol>''');
    expect(books, hasLength(1));
    expect(books.first.title, '敗北女角太多了！');
    expect(books.first.id, 1200003095);
    expect(books.first.coverUrl, 'https://tw.linovelib.com/covers/3095.jpg');
  });
  test('ID ranges isolate Wenku8 and Linovelib novels', () {
    expect(LinovelibRepository.owns(3095), isFalse);
    expect(LinovelibRepository.owns(1200003095), isTrue);
    expect(BookSource.linovelib.label, '嗶哩輕小說');
  });
}
""")
print('Dual-source changes complete.')
