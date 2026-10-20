from pathlib import Path

def change(path, before, after, expected=1):
    p=Path(path)
    s=p.read_text()
    n=s.count(before)
    if n!=expected:
        raise RuntimeError(f"{path}: expected {expected} occurrence(s), found {n}: {before[:120]!r}")
    p.write_text(s.replace(before,after,expected))

Path("lib/source_selection_page.dart").write_text(
    Path("../tools/source_selection_page.dart").read_text()
)

change("lib/linovelib_repository.dart",
    "  BookSource selected;\n  void choose(BookSource choice) {",
    "  BookSource selected;\n"
    "  late final ValueNotifier<BookSource> changes = ValueNotifier(selected);\n"
    "  void choose(BookSource choice) {")
change("lib/linovelib_repository.dart",
    "    selected = choice;\n"
    "    unawaited(prefs.setString('source_choice', selected.name));",
    "    selected = choice;\n"
    "    changes.value = choice;\n"
    "    unawaited(prefs.setString('source_choice', selected.name));")

# The page identity stays fixed; order is [discovery,shelf,local,source,me].
# Index 3 remains My, index 4 is the new Source to protect existing routes.
change("lib/home_navigation.dart",
    "    if (localReader) return combinedShelf ? const [1, 3] : const [2, 3];\n"
    "    if (combinedShelf) return cachedShelf ? const [1, 3] : const [0, 1, 3];\n"
    "    return cachedShelf ? const [1, 2, 3] : const [0, 1, 2, 3];",
    "    if (localReader) return combinedShelf ? const [1, 4, 3] : const [2, 4, 3];\n"
    "    if (combinedShelf) return cachedShelf ? const [1, 4, 3] : const [0, 1, 4, 3];\n"
    "    return cachedShelf ? const [1, 2, 4, 3] : const [0, 1, 2, 4, 3];")

# The capsule gets a 52 dp slot per tab, so 5 items fit on compact cover screens.
change("lib/glass_dock.dart",
    "items.length >= 2 && items.length <= 4",
    "items.length >= 2 && items.length <= 5")
p=Path("lib/glass_dock.dart")
s=p.read_text()
assert s.count("184 / 3") == 7, s.count("184 / 3")
p.write_text(s.replace("184 / 3","52.0"))

change("lib/pages.dart",
    "import 'linovelib_repository.dart';",
    "import 'linovelib_repository.dart';\nimport 'source_selection_page.dart';")
change("lib/pages.dart",
    "    GlassDockItem(CupertinoIcons.folder, '本地'),\n"
    "    GlassDockItem(CupertinoIcons.person_crop_circle, '我的'),",
    "    GlassDockItem(CupertinoIcons.folder, '本地'),\n"
    "    GlassDockItem(CupertinoIcons.person_crop_circle, '我的'),\n"
    "    GlassDockItem(CupertinoIcons.globe, '书源'),")

change("lib/pages.dart",
    "      settings.buildHeader,\n    ];",
    "      settings.buildHeader,\n"
    "      (_) => const ArchiveMasthead(title: '书源', code: '04 / SOURCE LIBRARY'),\n"
    "    ];")

change("lib/pages.dart",
    "              settings,\n            ]\n            .map((page)",
    "              settings,\n"
    "              _dualSource == null\n"
    "                  ? const Center(child: AppText('当前未启用在线书源'))\n"
    "                  : SourceSelectionPage(\n"
    "                      sources: _dualSource!,\n"
    "                      onChoose: _chooseSource,\n"
    "                    ),\n"
    "            ]\n            .map((page)")

change("lib/pages.dart",
    "  void _chooseSource(BookSource choice) {\n"
    "    final sources = _dualSource;\n"
    "    if (sources == null || sources.selected == choice) return;\n"
    "    sources.choose(choice);\n"
    "    setState(() {});\n"
    "    _ensureCatalog();\n"
    "  }",
    "  void _chooseSource(BookSource choice) {\n"
    "    final sources = _dualSource;\n"
    "    if (sources == null) return;\n"
    "    if (sources.selected != choice) {\n"
    "      sources.choose(choice);\n"
    "      setState(() {});\n"
    "    }\n"
    "    // Tap on a source is actionable: return to its own discovery feed.\n"
    "    _selectTab(0);\n"
    "    _ensureCatalog();\n"
    "  }")

# Preserve the existing four page indices; fifth source is ordered by tabs.
# Rebuild Discover on source selection, since the retained HomeShell tab
# intentionally reuses the same widget instance between transitions.
p=Path("lib/pages.dart")
s=p.read_text()
st=s.index("class DiscoverPage extends StatelessWidget {")
ed=s.index("/// Pull-to-refresh",st)
d=s[st:ed]
before=("  Widget build(BuildContext context) {\n"
        "    final current = _selectedFeed;\n")
after=("  Widget build(BuildContext context) => sources == null\n"
       "      ? _buildSelected(context)\n"
       "      : ValueListenableBuilder<BookSource>(\n"
       "          valueListenable: sources!.changes,\n"
       "          builder: (context, _, __) => _buildSelected(context),\n"
       "        );\n\n"
       "  Widget _buildSelected(BuildContext context) {\n"
       "    final current = _selectedFeed;\n")
assert d.count(before)==1, "Discover build method not found"
d=d.replace(before,after,1)

# Remove the two incorrect controls between the bookshelf and Archive Index.
st2=d.index("                        if (sources != null) ...[")
ed2=d.index("                        if (books.length > featuredCount ||",st2)
d=d[:st2]+d[ed2:]

# Remove the redundant little header switch; dedicated dock is the sole entry.
st3=d.index("      if (sources != null)\n        GlassIconButton(")
ed3=d.index("      GlassIconButton(\n        icon: CupertinoIcons.search,",st3)
d=d[:st3]+d[ed3:]
s=s[:st]+d+s[ed:]
p.write_text(s)

# Support brand on discovery and ensure its active status is reflected in Search.
# The new fifth page can only be reached from the dock, not an extra discovery control.
change("pubspec.yaml","version: 0.4.3+92","version: 0.4.4+94")
change("android/app/build.gradle.kts",
       'applicationIdSuffix = ".fold_reader_v2"',
       'applicationIdSuffix = ".fold_reader_v3"')
change("android/app/build.gradle.kts",
       '"轻小说文库·双书源"','"轻小说文库·书源导航"')

Path('test/source_tab_test.dart').write_text(r"""
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:wenku8_flutter/catalog.dart';
import 'package:wenku8_flutter/glass_dock.dart';
import 'package:wenku8_flutter/linovelib_repository.dart';
import 'package:wenku8_flutter/source_selection_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('source cards can be tapped and selected independently',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final dual = DualSourceRepository(
        const DemoNovelRepository(), LinovelibRepository(), prefs);
    BookSource? chosen;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: SourceSelectionPage(
        sources: dual,
        onChoose: (choice) {
          chosen = choice;
          dual.choose(choice);
        },
      )),
    ));
    expect(find.byKey(const ValueKey('source-choice-wenku8')), findsOneWidget);
    expect(find.byKey(const ValueKey('source-choice-linovelib')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('source-choice-linovelib')));
    await tester.pump();
    expect(chosen, BookSource.linovelib);
    expect(dual.selected, BookSource.linovelib);
    expect(prefs.getString('source_choice'), 'linovelib');
    await tester.tap(find.byKey(const ValueKey('source-choice-wenku8')));
    await tester.pump();
    expect(chosen, BookSource.wenku8);
  });

  testWidgets('dock shows five hit-testable tabs including source',
      (tester) async {
    var selected = -1;
    final items = [
      const GlassDockItem(CupertinoIcons.house, '发现'),
      const GlassDockItem(CupertinoIcons.book, '书架'),
      const GlassDockItem(CupertinoIcons.folder, '本地'),
      const GlassDockItem(CupertinoIcons.globe, '书源'),
      const GlassDockItem(CupertinoIcons.person_crop_circle, '我的'),
    ];
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: Center(
          child: GlassDock(
            selectedIndex: 0,
            reduceMotion: true,
            onSelected: (index) => selected = index,
            items: items,
          ),
        ),
      ),
    ));
    await tester.tap(find.text('书源'));
    await tester.pump();
    expect(selected, 3);
  });
}
""")
print('Five-tab source navigation patch applied')
