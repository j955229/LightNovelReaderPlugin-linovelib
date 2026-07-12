# Linovelib Search, Cache, and EPUB Stability Design

## Goal

Improve the Linovelib plugin without modifying the LightNovelReader app:

- return books when the website redirects a text search directly to a book page;
- route protected Linovelib and Readpai images through the installed plugin provider;
- let request throttling react promptly to coroutine cancellation;
- prevent duplicate offline-monitor jobs from competing with cache and export requests;
- keep enough in-memory chapter entries for a medium-to-large book to be reused during the same app session.

## Verified Root Causes

### Direct search redirects

Searching for `男性禁入` returns HTTP 302 with `Location: /novel/3768.html`. Jsoup follows the redirect and returns a book-detail document. The current search provider parses only list documents, so the result becomes empty.

### EPUB image failure

The 2026-07-12 export log shows all chapters completed, followed by 33 image tasks. Task 29 failed with HTTP 403 for:

`https://img3.readpai.com/2/2013/235372/256528.jpg`

The data source emitted the original HTTPS URI because `resolveContentProvider` was called with the host app context and could not discover the external plugin provider under Android package-visibility rules.

### Slow and cancellation-insensitive requests

The request coordinator uses `synchronized` and `Thread.sleep`. This blocks a dispatcher thread and does not observe coroutine cancellation while waiting. The log shows an EPUB WorkManager job being cancelled while chapter requests continued.

Repeated calls to `onLoad` can also launch multiple permanent offline-monitor loops. The log contains several health requests competing with chapter requests and monitor-contention warnings.

### Limited reusable chapter cache

The app wraps the data source with its API `Cache`. The current limit of 64 chapter entries and five-minute timeout cannot retain all 225 chapters of book 3768. Increasing this in-memory cache can speed an immediate export after reading or caching, while keeping it temporary and process-local.

## Design

### Search response model

Introduce a small search response value containing:

- final response URL;
- response HTML.

The guarded search loader will return this value after the ticket request. The search provider will:

1. inspect the final URL for a book ID;
2. fall back to an `og:url` or equivalent canonical book URL in the HTML;
3. parse and emit the direct book when an ID is found;
4. otherwise parse the normal search list as before.

This preserves multi-result search and adds support for the website's single-result redirect behavior.

### Image provider routing

For allowed HTTPS hosts, always emit the known `content://io.nightfish.lightnovelreader.plugin.linovelib.images/...` URI. Direct component access does not need a package-manager discovery query when the provider authority is already known.

The provider will continue to:

- allow only Linovelib, Bilinovel, and Readpai hosts;
- send the required User-Agent, Referer, and Accept headers;
- cache downloaded files inside the plugin app cache;
- reject writes and unsupported hosts.

The supported installation mode remains the installed external-plugin APK. Importing code without installing its Android provider is outside this change.

### Cancellable request coordinator

Convert request helpers to suspend functions and replace the Java monitor with a coroutine `Mutex`. Use cancellable `delay` for minimum intervals and cooldowns, plus cancellation checks before and after network operations.

Jsoup's blocking `execute` call will run on `Dispatchers.IO`. Cancellation can stop queued waits immediately; an already-running socket call can finish until its configured timeout.

All HTTP retry, cookie, search-guard, and diagnostic behavior remains unchanged.

### Single offline monitor

Store the offline-monitor `Job`. `onLoad` will return when an existing monitor is active. This prevents duplicate loops and reduces health-check competition with cache or export traffic.

### Session cache

Configure the API cache with:

- `maxCountEachType = 256`;
- `timeout = 21_600_000` milliseconds (six hours).

The cache remains memory-only, clears when the process exits, and expires automatically. Persistent chapter data belongs to the LightNovelReader database. Deleting that persistent data is available through the app's 1.2.1 local-book manager and cannot be implemented through the current plugin API.

## Error Handling

- Direct-search parsing failure falls back to normal list parsing.
- Missing or invalid search guard tokens keep producing a diagnostic error and terminal search error.
- Proxy image download errors remain visible to the app as file-open failures with the HTTP status in the cause.
- HTTP 429 and retryable server errors keep the existing bounded retry policy.
- Cancellation exceptions are always rethrown and never converted to empty book or chapter data.

## Test Plan

Add failing tests before production edits for:

1. a guarded search response redirected to `/novel/3768.html` emits book 3768;
2. a direct book-detail HTML fallback identifies the canonical book ID;
3. allowed image hosts always produce provider content URIs;
4. unsupported image hosts remain unchanged;
5. request-slot waiting is cancellable;
6. repeated offline-monitor starts create one active monitor;
7. plugin cache configuration retains at least 225 chapter entries for six hours.

Then run:

- focused unit tests for each change;
- the complete plugin unit-test suite;
- release APK build;
- a live guarded search for `男性禁入`;
- a protected Readpai image request through the provider headers;
- existing multi-page chapter diagnostics.

## Out of Scope

The plugin cannot add or change LightNovelReader UI and WorkManager controls. The following require an app change:

- tapping the cache button again to cancel;
- adding a cancel action to the EPUB notification;
- deleting the host app's persistent cached chapters from the book-detail screen.

