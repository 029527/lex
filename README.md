# Lex

A local-only text editor for Android, built with Kotlin and Jetpack Compose.
It opens Markdown, HTML and JSON files through the Storage Access Framework,
shows them read-only by default, and lets you switch into an editing mode when
you actually mean to change something.

The interface is native Android — Material 3 with dynamic colour and the
platform's own predictive back — and ships in English and Simplified Chinese.

No cloud, no account, and your documents never leave the device. The one network
call in the whole app asks GitHub for the latest version number, only when you
tap Check for Updates in Settings.

## Features

- **Storage Access Framework only** — no `MANAGE_EXTERNAL_STORAGE`. Grant one
  folder, browse it, and Lex remembers it across launches via a persisted URI
  permission.
- **Read-only by default.** Editing is an explicit toggle in the top bar, so a
  stray tap on a phone keyboard cannot silently modify a file.
- **Select all / copy** with a clipboard size guard — Android's clipboard rides
  a Binder transaction and throws on large payloads, so Lex refuses instead of
  crashing.
- **Formatting**
  - JSON: a lossless re-indenter that preserves key order and numeric literals
    (`1.50` stays `1.50`), plus a minify pass. Malformed input is reported with
    a line and column, never silently mangled.
  - HTML: pretty-printed with jsoup; a fragment stays a fragment.
  - Markdown: conservative tidy only (trailing whitespace, excess blank lines).
    Markdown reformatting is lossy, so Lex does not restructure your document.
- **Preview** for Markdown and HTML in a WebView with JavaScript disabled.
- **Encoding and line endings are preserved.** Lex detects UTF-8/UTF-16/GBK,
  remembers the BOM and the CRLF/LF/CR terminator, edits internally in LF, and
  writes the original form back.
- **Safe saves.** Writes truncate the target explicitly, so saving a shorter
  document cannot leave the tail of the old one behind.

## Interface

Material 3, with dynamic colour on Android 12 and later and a hand-picked
fallback palette below that. Theme can be left automatic or forced light or dark
in Settings. Navigation relies on the platform's predictive back rather than a
bespoke transition.

An earlier version of this app reimplemented Apple's design language on top of
Compose. It was removed. Copying another platform's appearance is
straightforward; its *feel* is not — the spring curves, the rubber-banding, the
haptics, the interruptible system transitions and the system typeface all come
from the OS, so a reimplementation has to rebuild them from nothing and lands in
the uncanny valley. The native toolkit gets all of it for free.

## Languages

English and 简体中文.

Translations follow Apple's zh-Hans glossary rather than generic equivalents, so
Save is 存储 and Copy is 拷贝. Locale resources use a BCP-47 script qualifier
(`values-b+zh+Hans`) so Hong Kong, Macau and Singapore locales resolve sensibly.

The app declares `android:localeConfig`, so Android 13 and later list Lex in the
system per-app language picker; there is also an in-app picker under Settings.

The in-app picker provides a separate composition local for string lookup rather
than overriding `LocalContext`. That distinction matters:
`createConfigurationContext` returns a context whose base chain never reaches the
Activity, and androidx locals such as `LocalActivityResultRegistryOwner` recover
their owner by walking that chain — so overriding `LocalContext` made
`rememberLauncherForActivityResult` throw the moment the language changed.

## Updates

Settings has a Check for Updates row and a link to the releases page.

The check reads `version.json` from the default branch over
`raw.githubusercontent.com` rather than calling the GitHub API: no
authentication, no 60-requests-per-hour limit, and it is CDN-cached. It runs only
when tapped, never on launch, and it stops at comparing version numbers.

Lex does **not** download or install an APK itself. That would need
`REQUEST_INSTALL_PACKAGES`, a sensitive permission and a well-worn path for
sideloaded malware, so the download and the install are left to the browser and
the system installer. Bump `versionCode` and `versionName` in `version.json`
alongside `app/build.gradle.kts` when cutting a release.

If you would rather not have the app touch the network at all, remove the
`INTERNET` permission and use [Obtainium](https://github.com/ImranR98/Obtainium),
which watches GitHub releases and installs updates for you.

## Build

Requires JDK 17.

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Run the unit tests (encoding detection, JSON/HTML formatting, Markdown
rendering) with:

```bash
./gradlew testDebugUnitTest
```

## CI

- `.github/workflows/build.yml` runs the unit tests and builds a debug APK on
  every push and pull request, and uploads the APK as a workflow artifact.
- `.github/workflows/release.yml` builds and publishes a release APK when you
  push a `v*` tag.

`app/debug.keystore` is committed deliberately and is safe to publish. It signs
**debug** builds only, so a freshly built debug APK installs over an older one
instead of failing on a signature mismatch. Its password is the conventional
`android`.

Release builds never use it. The release signing key exists only as repository
secrets — `LEX_KEYSTORE_BASE64`, `LEX_KEYSTORE_PASSWORD`, `LEX_KEY_ALIAS`,
`LEX_KEY_PASSWORD` — and the release workflow fails if they are missing rather
than falling back to a key that anyone can read out of this repository. It also
runs `apksigner verify` before publishing and refuses to attach an APK that is
unsigned or signed with the debug certificate. Android identifies an app by its
signing key, so a release signed with a public key would let anyone build an APK
that installs over an existing Lex as an update.


## Architecture

```
model/    Shared contracts: FileType, LineEnding, DocumentMeta, FormatResult
data/     SAF access, encoding detection, safe load/save, settings
format/   JSON / HTML / Markdown formatting and preview documents
ui/       Compose screens: browser, editor, status bar, preview, settings
```

Directory listing goes through a single `DocumentsContract` cursor query rather
than `DocumentFile.listFiles()`, which issues one IPC round trip per child and
takes seconds on a folder with a few hundred files.

## Known limitations

- Files above 4 MB are refused. The editor holds the whole document in memory
  and uses the classic Compose `TextFieldValue` API, which is not built for
  very large buffers.
- No syntax highlighting. Adding it for arbitrary code would mean replacing the
  Compose text field with a dedicated editor view.
- No search and replace yet.
- `minSdk` is 26 (Android 8.0).
- A few diagnostic details are English-only: a JSON parse error's line and column
  message, and the underlying reason attached to a failed open or save. The
  surrounding UI is translated.
