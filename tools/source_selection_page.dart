import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import 'app_ui.dart';
import 'archive_design.dart';
import 'linovelib_repository.dart';

/// Independent fifth home tab. A tap selects a source and opens Discover.
class SourceSelectionPage extends StatelessWidget {
  const SourceSelectionPage({
    super.key,
    required this.sources,
    required this.onChoose,
  });

  final DualSourceRepository sources;
  final ValueChanged<BookSource> onChoose;

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<BookSource>(
    valueListenable: sources.changes,
    builder: (context, selected, _) => _sourceList(context, selected),
  );

  Widget _sourceList(BuildContext context, BookSource selected) {
    final scheme = Theme.of(context).colorScheme;
    return CustomScrollView(
      key: const PageStorageKey('source-tab'),
      slivers: [
        SliverPadding(
          padding: EdgeInsets.fromLTRB(
            24,
            MediaQuery.paddingOf(context).top + FrostedHeader.toolbarHeight + 25,
            24,
            MediaQuery.paddingOf(context).bottom + 120,
          ),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              sectionHeader(context, '选择书源', '04 / SOURCE LIBRARY'),
              const SizedBox(height: 20),
              for (final source in BookSource.values) ...[
                Material(
                  color: scheme.surface.withValues(alpha: .38),
                  borderRadius: BorderRadius.circular(18),
                  child: InkWell(
                    key: ValueKey('source-choice-${source.name}'),
                    onTap: () => onChoose(source),
                    borderRadius: BorderRadius.circular(18),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18, vertical: 19,
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 44, height: 48,
                            decoration: BoxDecoration(
                              color: scheme.onSurface,
                              borderRadius: BorderRadius.circular(3),
                            ),
                            alignment: Alignment.center,
                            child: Text(
                              source == BookSource.wenku8 ? 'W8' : 'BL',
                              style: TextStyle(
                                color: scheme.surface,
                                fontSize: 17,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                AppText(
                                  source.label,
                                  style: const TextStyle(
                                    fontSize: 19, fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 5),
                                ArchiveCode(source == BookSource.wenku8
                                    ? 'WENKU8' : 'LINOVELIB TW'),
                              ],
                            ),
                          ),
                          Icon(
                            selected == source
                                ? CupertinoIcons.checkmark_circle_fill
                                : CupertinoIcons.chevron_right,
                            color: selected == source
                                ? scheme.primary : scheme.onSurfaceVariant,
                            size: selected == source ? 25 : 19,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 14),
              ],
            ]),
          ),
        ),
      ],
    );
  }
}
