# oneMind UI layer — pre-release audit

Review of `app/src/main/java/com/onemind/app/ui/**` and `MainActivity.kt` at `0f2f22b` (clean tree, `main`). 28 files, no diff — this is an audit of the shipping state rather than a change review.

The layer is coherent and unusually well-commented, and the search/feed composables get the Compose fundamentals right: every `LazyColumn` has stable keys, `remember` guards snippet extraction and date grouping, `collectAsStateWithLifecycle` is used consistently, and the search pipeline debounces and cancels correctly. The problems cluster elsewhere: the two commit paths (composer back, onboarding retry) have unhandled routes that lose work or dead-end, the provider-configuration flows mutate live state as a side effect of testing, and Settings ships two sections that cannot function because `ModelRegistry.generativeModels` is empty.

**Watch for:** the composer never commits on system back, so text typed within the 3s autosave window is silently lost (confirmed); onboarding's Retry button is inert on every reachable path, freezing the user on a dead download screen (confirmed); Settings' "Test" button deactivates the user's working AI provider when the test fails (confirmed); typing a single character or a stopword shows "No memories found" instead of the feed, contradicting the search layer's own documented contract (confirmed).

**Verdict**: NEEDS_CHANGES

## High-level view

The composer is the app's primary capture path and it has one commit trigger: the top-bar back arrow. There is no `BackHandler` anywhere in the codebase, so the system back button and the back gesture pop the NavHost directly, skipping `onLeaveComposer()`. Anything typed in the last three seconds is gone, the memory stays in `DRAFT` and is never handed to the enrichment pipeline, and an emptied draft is not cleaned up. Autosave also races itself: `saveMemory()` snapshots the attachment list, suspends to process images, then writes the snapshot back, so an attach or remove during that window is silently reverted.

Onboarding's download step is only ever reached by one route in the shipping configuration. `ModelRegistry.generativeModels` is `emptyList()`, so `localModelsAvailable` is false and every path through `DOWNLOADING` is the embedding-model-only path where `selectedModel` is null. `onRetryDownload()` delegates to `onStartDownload()`, which returns immediately on a null model — leaving the screen at `DOWNLOADING` with no error, no progress, and no way forward except Cancel. That same null also makes the header read "Downloading " with a blank name for every user who reaches it.

Provider configuration treats "Test" as a mutation. Both the onboarding and Settings versions call `activateCloud()` before probing, then `deactivate()` on failure. In Settings this means a failed test tears down whatever provider was working and leaves the app with none, while `CurrentProviderSection` continues to display the old one. On success the cloud provider is live even though the user never pressed "Use Cloud", so runtime state, persisted preference, and displayed state can all disagree. Settings also never reads back the stored cloud config that `OnboardingPreferences.getCloudConfig()` already exposes, so a configured user sees blank fields and must retype their whole API key to change a model name.

The feed's search mode is entered on `searchQuery.isNotBlank()` while results are computed from `FtsQuery.build()`, which returns null for anything under two characters or made entirely of stopwords. The two disagree, and the disagreement surfaces as a hard "No memories found" for queries like "a", "the", or "my" — the exact case `FtsQuery`'s own doc comment says should show the feed. Search results are also a one-shot snapshot that `observeAllMemories()` does not refresh, so a memory deleted from search results stays on screen and tapping it lands on a detail screen that spins forever, because null means both "loading" and "does not exist".

Deep linking works, but by accident and at a cost. `openMemoryId` is a plain `Long` captured once in `onCreate`; `onNewIntent` only calls `setIntent()`, which is not observable state, and the comment justifying this cites single-top launch mode that the manifest does not declare. Navigation happens only because the notification's `FLAG_ACTIVITY_CLEAR_TOP` against a `standard` activity recreates it. The intent is never consumed, so any later recreation — rotation, theme change, process-death restore — re-navigates to the memory and yanks the user out of wherever they were.

Accessibility gaps are narrow but real: long-press is the only way to delete a memory and carries no `onLongClickLabel`, so the action is invisible to TalkBack and undiscoverable to everyone else. Two 24dp touch targets (card Retry, image remove) sit under the 48dp minimum. On the performance side, `SourceRow` re-rasterizes the source app's icon on every recomposition — the `remember` covers the `Drawable` lookup but not the `toBitmap()` call — inside `LazyColumn` items, on the main thread.

<details>
<summary>Issues (28)</summary>

1. **Composer loses data on system back** — `ComposerScreen.kt:61-66` wires commit only to the top-bar arrow; no `BackHandler` exists. Add one that calls `viewModel.onLeaveComposer()`, or move the commit into `ViewModel.onCleared()`/a `DisposableEffect`.
2. **Onboarding Retry is inert on every reachable path** — `OnboardingViewModel.kt:180-183` retries via `onStartDownload()`, which bails at `:75` on a null `selectedModel`. Route retry to `downloadEmbeddingModelThenFinish()` when no generative model was selected.
3. **Test connection tears down the working provider** — `SettingsViewModel.kt:154-162` calls `activateCloud()` then `deactivate()` on failure, leaving no provider. Probe without mutating `ProviderManager`, or restore the previous provider on failure.
4. **Test success activates cloud without confirmation** — same block; the provider is live before "Use Cloud" is pressed, diverging from the persisted preference and from `CurrentProviderSection`.
5. **Single-character and stopword queries show "No memories found"** — `FeedUiState.kt:47` gates search mode on `searchQuery.isNotBlank()` while results come from `FtsQuery.build()`. Derive `isSearchActive` from `FtsQuery.build(searchQuery) != null`.
6. **Settings' local-model section cannot work** — `ModelRegistry.kt:35` returns no models, so "Change local model" (`SettingsScreen.kt:180-185`) opens an empty dialog. Hide the section behind `hasLocalGenerativeModels`.
7. **Settings' storage section is permanently inert** — always "Cached models: 0 MB" with a disabled button; hide it with the section above.
8. **Deep link re-fires on every activity recreation** — `MainActivity.kt:20-25` + `OneMindApp.kt:56-63` never consume the intent, so rotation re-navigates to the memory. Clear the extra after handling, or hold it in a `MutableState` the effect nulls out.
9. **`onNewIntent` does not navigate** — `MainActivity.kt:27-35`; `setIntent()` is not observable and the manifest declares no `launchMode` despite the comment. Hoist the pending memory id into `MutableState` set by both `onCreate` and `onNewIntent`.
10. **Autosave clobbers concurrent attachment edits** — `ComposerViewModel.kt:191-203` writes back a pre-suspension snapshot. Merge by identity, or re-read state after processing.
11. **Failed image processing silently drops the image** — `ComposerViewModel.kt:283-292` swallows the error, `:207-217` skips the block, and the composer keeps showing the thumbnail. Surface the failure and mark the attachment.
12. **Removed images leak files** — `ComposerViewModel.kt:102-106` drops the attachment without deleting canonical/thumbnail files, unlike `FeedViewModel.confirmDelete`.
13. **Detail screen spins forever for a missing memory** — `MemoryDetailScreen.kt:70` treats null as loading. Add a distinct not-found state.
14. **Search results go stale after delete** — `FeedViewModel` never refreshes `searchResults` from `observeAllMemories()`, so deleted memories stay tappable and lead into issue 13.
15. **Source filter chips never refresh** — `FeedViewModel.kt:131` is a one-shot query in `init`; new sources never appear and counts go stale. Observe the counts as a Flow.
16. **Settings cloud fields never pre-populate** — `SettingsViewModel.kt:35-59` ignores the existing `OnboardingPreferences.getCloudConfig()`.
17. **Settings download dies silently on navigation** — `SettingsViewModel.kt:94-118` runs in `viewModelScope`; leaving the screen cancels a multi-GB download with no warning.
18. **Settings cancel orphans the partial download** — `SettingsViewModel.kt:117-120` never calls `modelDownloadManager.cancelDownload()` (onboarding does at `:180-183`), leaving the `.tmp` file and a stale `storageUsedBytes`.
19. **Onboarding navigates during composition** — `OnboardingScreen.kt:19` calls `onOnboardingComplete()` in the composition body; move it into a `LaunchedEffect`.
20. **Two mechanisms race on the onboarding-to-feed transition** — issue 19 plus `OneMindApp.kt:41-49` recomputing `startDestination`, which reassigns the NavHost graph. Drive the transition from one source.
21. **System back exits the app mid-onboarding** — steps are ViewModel state with no `BackHandler`, so back on `CLOUD_CONFIG` or `DOWNLOADING` closes the app and discards a part-typed API key.
22. **Delete is long-press only and invisible to TalkBack** — `MemoryCard.kt:45-48` and `SearchResultCard.kt:62` pass no `onLongClickLabel`, and `MemoryDetailScreen` offers no delete at all. Add a delete action to the detail screen and a long-click label.
23. **Extracted links are not openable** — `MemoryDetailScreen.kt:180` and `:392-404` style URLs as links with no click handling.
24. **App icons re-rasterize on every recomposition** — `SourceDisplay.kt:42` calls `toBitmap()` outside `remember`, inside `LazyColumn` items; also 14 raw px into a 14.dp box renders blurry above 1x density.
25. **Two touch targets under 48dp** — `MemoryCard.kt:198` (Retry) and `ComposerScreen.kt:220` (remove image).
26. **Non-functional `SuggestionChip`s** — `MemoryDetailScreen.kt:113`, `:212`, `ModelSelectionScreen.kt:199` render tappable chips with empty `onClick`.
27. **Model picker dialog cannot scroll** — `SettingsScreen.kt:337` uses a plain `Column` in the `AlertDialog` text slot; rows past the dialog height become unreachable once models exist.
28. **Zero UI tests** — no Compose test rule anywhere in `app/src/test` or `app/src/androidTest`; every issue above is unguarded against regression.

Lower-priority items not counted above: the delete dialog binds `memory` at `FeedScreen.kt:138` and never uses it, so it cannot say which memory is being deleted; `ComposerViewModel.kt:168` shows "Saved" even when `saveMemory()` returned null; `ComposerScreen.kt:194` uses `itemsIndexed` with no key despite a stable `sourceUri`; `DateGrouping.kt:34` captures `Instant.now()` inside a `remember`, so "Today" goes stale across midnight; the Paste button is always enabled and does nothing on an empty clipboard; `SettingsScreen.kt:306` integer-divides to "0 MB" while the delete button is enabled; `downloadError` is never cleared once shown; `SourceDisplay.kt:37`'s "Source app icon" description duplicates the adjacent label; `FeedViewModel.searchMemories` (`:88`), `ComposerUiState.isCommitted`, and `ModelSelectionScreen`'s `onSkip` parameter are dead; `ComposerViewModel.kt:288` leaks the temp file when `saveImage` throws.

</details>

<details>
<summary>Details</summary>

### Composer commit is reachable by only one of two back gestures

`ComposerScreen.kt:61-66` defines the commit path:

```kotlin
val handleBack: () -> Unit = {
    viewModel.onLeaveComposer()
    onNavigateBack()
}
```

`handleBack` is passed to exactly one caller — the `TopAppBar` navigation icon. `BackHandler` appears nowhere in `app/src/main/java`, so the system back button and the predictive-back gesture pop the NavHost entry directly and `onLeaveComposer()` never runs.

`onLeaveComposer()` is where a blank draft gets deleted, where `DRAFT` transitions to `SAVED`, and where `processingScheduler.enqueue()` is called. Skipping it means a memory that was autosaved sits in `DRAFT` forever — never summarised, never categorised, never embedded, so it also never appears in semantic search. And because autosave is debounced 3000ms (`ComposerViewModel.kt:33`), a user who types a short note and immediately swipes back loses the text outright: the `delay` never elapsed and no memory was ever created.

Most Android users navigate back by gesture, not by reaching for the top-left arrow. This is the app's core capture flow.

### Onboarding's download step has no working retry

In the shipping configuration `ModelRegistry.generativeModels` is `emptyList()` (`ModelRegistry.kt:35`), so `hasLocalGenerativeModels` is false, `ModelSelectionScreen` always renders `NoLocalModelsScreen`, and the only two ways out are "Configure a provider" and "Skip for now". Both converge on `downloadEmbeddingModelThenFinish()`, which sets `step = DOWNLOADING` while `selectedModel` is still null.

Two consequences follow from that null. First, `DownloadScreen` is called with `modelName = uiState.selectedModel?.displayName ?: ""` (`OnboardingScreen.kt:45`), so the header renders "Downloading " with nothing after it — for every user, since this is the only reachable path. Second, and worse:

```kotlin
fun onRetryDownload() {
    _uiState.update { it.copy(downloadError = null) }
    onStartDownload()          // :75 → val model = _uiState.value.selectedModel ?: return
}
```

Retry clears the error and then returns without doing anything. The screen is left at `step = DOWNLOADING`, `isDownloading = false`, `downloadError = null` — which renders the progress branch: 0%, no byte counter, a Cancel button, and nothing happening. If the ~115MB embedding download fails (flaky connection, metered network, server hiccup), the user's only working option is Cancel, back to model selection, and start over. `onCancelDownload` also can't clean up here: it calls `modelDownloadManager.cancelDownload(it.id)` only inside `model?.let`, so the partial `.tmp` file for the embedding model is orphaned.

### Testing a provider mutates the live provider

Both `OnboardingViewModel.kt:275-296` and `SettingsViewModel.kt:144-166` implement the test identically:

```kotlin
providerManager.activateCloud(config)
val result = providerManager.getProvider()?.generateText("Say hello in one word.")
if (result?.isSuccess == true) {
    _uiState.update { it.copy(cloudTestResult = CloudTestResult.SUCCESS) }
} else {
    providerManager.deactivate()
    _uiState.update { it.copy(cloudTestResult = CloudTestResult.FAILED) }
}
```

In onboarding there is nothing to lose — no provider is configured yet. In Settings there is. A user with a working provider who mistypes an API key and presses Test ends up with `deactivate()` called and no provider at all, while `CurrentProviderSection` still shows the old one because `loadCurrentState()` was not re-run. Enrichment stops until the app restarts, and nothing on screen says so. The `catch` branch is worse still: it skips `deactivate()`, so a thrown exception leaves the bad cloud config installed as the live provider.

The success path has the mirror problem. The cloud provider is active in memory but `setActiveCloudProvider()` is only called from `onConfirmCloudConfig()`, so a user who tests successfully and then navigates away has a live cloud provider that no preference records and no UI reflects.

### Search mode and search capability disagree

`FeedUiState.kt:47`:

```kotlin
val isSearchActive: Boolean get() = searchQuery.isNotBlank()
```

`FeedScreen.kt:92` uses that flag to swap the whole body over to `SearchResultsSection`. But results come from `FtsQuery.build()`, which returns null for any query with no usable terms — terms shorter than two characters, or entirely in the stopword list. That list includes "my", "the", "show", "find", "what", "all", "stuff". `FeedViewModel.kt:70-77` handles the null case by emitting an empty result list, commented "Emit no results and let the feed show". The feed does not show, because `isSearchActive` was computed from the raw text. `SearchResultsSection` falls through to its second branch and renders "No memories found / Try describing it differently".

`FtsQuery`'s own doc comment states the intended contract: *"A user who has typed only punctuation should see their feed, not 'no results'."* The domain layer holds up its end; the UI layer's derived flag breaks it. Reachable on the first keystroke of nearly any query.

### Deleting from search results leads to a screen that lies

`FeedViewModel.searchResults` is a snapshot returned by `searchOrchestrator.search()`. `observeMemories()` keeps `memories` live but touches nothing in the search path, and `confirmDelete()` updates neither. So long-pressing a search result and confirming delete leaves the card on screen, and tapping it navigates to `MemoryDetailScreen` with a dead id.

`MemoryDetailScreen.kt:68-79` has one branch for null:

```kotlin
when (val m = memory) {
    null -> { /* CircularProgressIndicator */ }
    else -> { MemoryDetailContent(...) }
}
```

`getMemoryById` returns null for a missing row, so loading and not-found are the same state and the user gets an indefinite spinner. Back works, so it is not a trap, but the screen claims to be fetching something that will never arrive. The same path is reachable from a notification for a memory deleted before the tap.

### Deep-link handling depends on activity recreation

`MainActivity.kt:20-25` reads the memory id once and passes it as a plain value into `setContent`. `onNewIntent` stores the new intent and comments that navigation "handles it via the recomposition triggered by the new intent" — there is no such trigger, since a captured `Long` is not snapshot state and `setIntent()` writes no observable. The comment's premise is also wrong: it says "single-top", but `AndroidManifest.xml:27-36` declares no `launchMode`, so `MainActivity` is `standard`.

Deep links do currently work, because `CaptureNotifier.kt:119` sets `FLAG_ACTIVITY_CLEAR_TOP` and a `standard` activity under `CLEAR_TOP` is destroyed and recreated rather than receiving `onNewIntent`. That is an accident worth not relying on: every notification tap rebuilds the entire UI, discarding feed scroll position, search state, and any composer text in flight.

The defect independent of launch mode is that the intent is never consumed. `LaunchedEffect(openMemoryId)` keys on a value that stays constant for the life of the activity, and `getIntent()` keeps returning the notification intent. A user who taps a notification, reads the memory, presses back to the feed, and then rotates the device is thrown straight back into the memory detail screen. Same for a theme change or a restore after process death.

### Onboarding completion is driven from two places at once

`OnboardingScreen.kt:18-22` performs navigation as a composition side effect:

```kotlin
if (uiState.step == OnboardingStep.COMPLETE) {
    onOnboardingComplete()
    return
}
```

That callback runs `navController.navigate(FEED) { popUpTo(ONBOARDING) { inclusive = true } }`. Any recomposition while `step == COMPLETE` re-invokes it, and after the first call has popped `ONBOARDING`, `popUpTo` matches nothing, so a second call pushes a second `FEED` entry — back from the feed then goes to the feed.

Meanwhile `OneMindApp.kt:41-49` computes `startDestination` from `isOnboardingComplete`, which flips to `true` at the same moment via DataStore. `NavHost` keys its graph on `startDestination`, so a change there builds a new graph and assigns it to the controller, resetting the back stack. Two independent mechanisms are trying to perform the same transition on the same frame. Whichever ordering wins on a given device, only one of them should exist — and the composition-body navigation should go regardless.

### Source icons are re-rasterized per card, per recomposition

`SourceDisplay.kt:31-46` looks correct at a glance because `resolveSource` wraps the `PackageManager` work in `remember`. The rasterization is outside it:

```kotlin
sourceInfo.icon?.let { icon ->
    Image(
        bitmap = icon.toBitmap(width = 14, height = 14).asImageBitmap(),
        ...
    )
}
```

`toBitmap()` allocates a bitmap and draws the drawable into it — for adaptive icons, compositing layers — on the main thread, on every recomposition of every visible card. The file's comment also asserts that "PackageManager calls are cheap (metadata only, no I/O)". `getApplicationInfo` and `getApplicationIcon` are binder IPC into `PackageManagerService`, and icon retrieval reads resources out of the target APK. `remember` limits that to once per item composition, but `LazyColumn` composes items as they scroll into view, so the cost recurs throughout a scroll.

Separately, `width = 14, height = 14` are raw pixels rendered into a `14.dp` box. On a 3x-density screen that is a 14px bitmap upscaled to 42px.

### Checklist items that came back clean

Every `LazyColumn` in `FeedScreen` supplies stable keys, including the timeline's `stickyHeader(key = group.name)` alongside `items(key = { it.id })`; the only keyless list is the composer's `LazyRow` of attachments. The UI layer contains exactly two `!!` (`MemoryCard.kt:65`, `SearchResultCard.kt:78`), both on a `thumbnailPath` the enclosing `firstOrNull` predicate already proved non-null. The one piece of index arithmetic — `SearchResultCard.kt:161-172` building highlight spans — coerces both bounds before every `substring`, and highlights use bold *and* colour rather than colour alone. `FeedViewModel`'s search flow debounces and uses `flatMapLatest`, so a slow early query cannot overwrite a fast later one. `MemoryDetailScreen`'s `LaunchedEffect(memoryId)` does re-fire after an edit, because navigation-compose disposes the outgoing destination and recomposes it on return.

</details>

<details>
<summary>File map</summary>

| File | Findings |
|---|---|
| `MainActivity.kt` | intent never consumed; `onNewIntent` does not navigate |
| `ui/OneMindApp.kt` | `startDestination` change races onboarding's own navigation; `LaunchedEffect` keyed on a constant |
| `ui/AppViewModel.kt` | clean |
| `ui/navigation/OneMindNavHost.kt`, `NavRoutes.kt` | clean |
| `ui/feed/FeedScreen.kt` | delete dialog does not name the memory; otherwise correct Compose usage |
| `ui/feed/FeedUiState.kt` | `isSearchActive` disagrees with `FtsQuery.build` |
| `ui/feed/FeedViewModel.kt` | one-shot source counts; stale search results after delete; dead `searchMemories` |
| `ui/feed/MemoryCard.kt` | 24dp Retry target; long-press unlabelled |
| `ui/feed/SearchResultCard.kt` | long-press unlabelled |
| `ui/feed/SourceDisplay.kt` | bitmap conversion outside `remember`; hardcoded px; redundant description |
| `ui/feed/MemoryDetailScreen.kt` | null conflates loading with not-found; no delete; inert chips; unopenable links |
| `ui/feed/MemoryDetailViewModel.kt` | cannot express not-found |
| `ui/feed/DateGrouping.kt` | `Instant.now()` captured in `remember` |
| `ui/feed/SourceFilterRow.kt` | clean |
| `ui/composer/ComposerScreen.kt` | no `BackHandler`; 24dp remove target; keyless `itemsIndexed`; always-enabled Paste |
| `ui/composer/ComposerViewModel.kt` | autosave clobbers concurrent edits; silent image-processing failure; file leaks; false "Saved" |
| `ui/composer/ComposerUiState.kt` | dead `isCommitted` |
| `ui/settings/SettingsScreen.kt` | dead local-model and storage sections; unscrollable picker; integer MB |
| `ui/settings/SettingsViewModel.kt` | destructive Test; no cloud pre-population; download cancelled by navigation; orphaned `.tmp` |
| `ui/onboarding/OnboardingScreen.kt` | navigation in composition body |
| `ui/onboarding/OnboardingViewModel.kt` | broken Retry; destructive Test; no back handling |
| `ui/onboarding/DownloadScreen.kt` | blank model name on the only reachable path |
| `ui/onboarding/ModelSelectionScreen.kt` | inert "Recommended" chip; unused `onSkip` in the local branch |
| `ui/onboarding/CloudConfigScreen.kt`, `WelcomeScreen.kt`, `OnboardingUiState.kt` | clean |
| `ui/theme/OneMindTheme.kt` | not reviewed in depth |

Cross-layer facts relied on: `ModelRegistry.kt:35` (`generativeModels = emptyList()`), `FtsQuery.kt:118-127` (`build` returns null), `OnboardingPreferences.kt:70` (`getCloudConfig` exists, unused by Settings), `ModelDownloadManager.kt:112-115` (`cancelDownload` deletes the `.tmp`), `CaptureNotifier.kt:116-128` (`CLEAR_TOP`, no `SINGLE_TOP`), `AndroidManifest.xml:27` (no `launchMode`).

</details>
