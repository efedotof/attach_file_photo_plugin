# device_media_library

A Flutter plugin for accessing the device media library, the shared
Download directory, the Documents directory, and audio files on Android,
without invoking a system file picker.

The plugin implements platform channels for Android (Kotlin) and provides
a web implementation based on the HTML File API. A Dart-only Windows
implementation provides a partial fallback for desktop.

## Table of contents

- [Overview](#overview)
- [Features](#features)
- [Platform support](#platform-support)
- [Installation](#installation)
- [Android configuration](#android-configuration)
- [Initialization](#initialization)
- [Permissions](#permissions)
  - [Scoped media permissions](#scoped-media-permissions)
  - [All-files access](#all-files-access)
  - [Folder-specific access (SAF)](#folder-specific-access-saf)
- [Retrieving media from MediaStore](#retrieving-media-from-mediastore)
- [Audio](#audio)
- [Thumbnails](#thumbnails)
- [Reading files from Download and Documents](#reading-files-from-download-and-documents)
  - [Storage access modes](#storage-access-modes)
  - [Inspecting access with checkStorageAccess](#inspecting-access-with-checkstorageaccess)
  - [SAF-based folder access](#saf-based-folder-access)
  - [MANAGE_EXTERNAL_STORAGE fallback](#manage_external_storage-fallback)
- [Reading file contents](#reading-file-contents)
- [Getting file paths](#getting-file-paths)
- [File picking (web and Windows)](#file-picking-web-and-windows)
- [Video playback](#video-playback)
- [Web implementation](#web-implementation)
- [Return types](#return-types)
- [Error codes](#error-codes)
- [Limitations](#limitations)
- [Example application](#example-application)
- [License](#license)

## Overview

The plugin exposes a unified Dart API that queries the Android MediaStore
for indexed media (images, video, audio) and reads the Download and
Documents directories through either the MediaStore, the java.io.File API,
or the Storage Access Framework (SAF), depending on the permissions
granted to the application.

Three access models are supported:

1. **Scoped permissions** (Android 10 and later) — access to media files
   through MediaStore and reading of files created by the application
   itself.
2. **All-files access** (Android 11 and later) — direct filesystem access
   to the Download and Documents directories through the java.io.File API.
   Requires `MANAGE_EXTERNAL_STORAGE`, which Google Play reviews on a
   per-application basis.
3. **SAF folder access** (Android 11 and later) — per-folder access granted
   by the user through the system folder picker. Persists across restarts
   and does not require `MANAGE_EXTERNAL_STORAGE`.

## Features

- Retrieval of images, video and audio from MediaStore with pagination
  (`limit` / `offset`), album filtering and thumbnail generation.
- Enumeration of arbitrary files inside the Download directory through
  the `java.io.File` API (full access), SAF, or `MediaStore.Downloads`
  (scoped access).
- Enumeration of arbitrary files inside the Documents directory. Requires
  `MANAGE_EXTERNAL_STORAGE` or SAF access on Android 11 and later.
- Reading file contents by URI, supporting both `content://` and
  `file://` schemes.
- Conversion of a URI to an absolute file path via `getFilePath`.
- Streaming video playback through a PlatformView backed by
  `android.media.MediaPlayer`.
- Web implementation supporting file picking and drag-and-drop through
  the HTML File API.
- Windows fallback with file picking support.
- Capability to request and query scoped permissions
  (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`),
  all-files access (`MANAGE_EXTERNAL_STORAGE`), and per-folder SAF access.

## Platform support

| Platform  | Status          | Notes                                                |
|-----------|-----------------|------------------------------------------------------|
| Android   | Full            | Requires API 21 or later.                            |
| Web       | Partial         | File picking only. No access to system directories.  |
| Windows   | Partial         | File picking only.                                   |
| iOS       | Not implemented | The platform stub returns `UnimplementedError`.      |

## Installation

Add the dependency to `pubspec.yaml`:

```yaml
dependencies:
  device_media_library:
    path: ../device_media_library
```

For a published package, replace the path with a version constraint.

## Android configuration

Declare the following permissions in
`android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission
        android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />

    <application ...>
        ...
    </application>
</manifest>
```

- `READ_EXTERNAL_STORAGE` applies to Android 12 and earlier.
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` apply to
  Android 13 and later.
- `MANAGE_EXTERNAL_STORAGE` is only needed if direct filesystem access to
  Download or Documents is required. The `tools` namespace must be
  declared on the `manifest` element.

## Initialization

The plugin must be initialized before use:

```dart
import 'package:device_media_library/device_media_library.dart';

void main() {
  DeviceMediaLibrary.initialize();
  runApp(const MyApp());
}
```

`initialize` selects the appropriate platform implementation based on
`defaultTargetPlatform`. `ensureInitialized` is idempotent and is called
internally by every plugin method.

## Permissions

The plugin exposes several methods for permission management:

```dart
final lib = DeviceMediaLibrary();

// Scoped permissions (images, video, audio).
final bool hasBasic = await lib.checkPermission();
final bool granted  = await lib.requestPermission();

// All-files access (Android 11 and later).
final bool hasAll = await lib.checkAllFilesPermission();
await lib.requestAllFilesPermission();
```

### Scoped media permissions

`checkPermission` / `requestPermission` cover `READ_MEDIA_*` on
Android 13+ and `READ_EXTERNAL_STORAGE` on Android 12 and earlier.

### All-files access

`requestAllFilesPermission` opens the system settings screen because
Android does not expose a standard in-process dialog for this permission.
The method returns `false` immediately after launching the settings screen.
To determine whether the user actually granted the permission, call
`checkAllFilesPermission` after the application returns to the foreground.
This is typically done with `WidgetsBindingObserver` and
`didChangeAppLifecycleState`:

```dart
@override
void didChangeAppLifecycleState(AppLifecycleState state) {
  if (state == AppLifecycleState.resumed && !kIsWeb) {
    _refreshAllFilesPermission();
  }
}
```

### Folder-specific access (SAF)

On Android 11+ the plugin can grant access to the Download and Documents
folders individually through the Storage Access Framework, without
`MANAGE_EXTERNAL_STORAGE`. See
[SAF-based folder access](#saf-based-folder-access).

## Retrieving media from MediaStore

Images, video and audio are queried through the MediaStore content
provider. All methods support `limit` and `offset` for pagination.

```dart
final images = await lib.getImages(limit: 50, offset: 0);
final videos = await lib.getVideos(limit: 50, offset: 0);
final audio  = await lib.getAudio(limit: 50, offset: 0);
final all    = await lib.getAllMedia(limit: 50, offset: 0);

final albums    = await lib.getAlbums();
final fromAlbum = await lib.getMediaFromAlbum(
  albumId: albumId,
  limit: 50,
  offset: 0,
);
```

Each returned element contains at least the following keys:
`id`, `name`, `uri`, `dateAdded`, `size`, `type`, `width`, `height`,
`albumId`, `albumName`.

- Video and audio entries additionally contain `duration` (milliseconds).
- Audio entries also contain `artist`, `album` and `mimeType`.

## Audio

`getAudio(limit, offset)` returns audio tracks indexed by MediaStore.
Fields: `id`, `name`, `uri`, `dateAdded`, `size`, `duration`, `mimeType`,
`artist`, `album`, `albumId`, `albumName`, `type`, `width`, `height`.

## Thumbnails

Thumbnails are generated by the plugin for MediaStore entries and
returned as raw bytes:

```dart
final Uint8List? bytes = await lib.getThumbnail(
  mediaId: mediaId,
  mediaType: 'image', // or 'video'
  width: 200,
  height: 200,
);
```

On Android 10+ image thumbnails are produced by
`ContentResolver.loadThumbnail`. Video thumbnails use
`MediaMetadataRetriever` with a fallback to the default frame. Thumbnail
generation is not available for arbitrary files from Download or
Documents; render an icon based on `mimeType` for those.

## Reading files from Download and Documents

Two methods are provided for directory enumeration. Both accept `limit`,
`offset` and `recursive`.

```dart
final downloads = await lib.getDownloads(limit: 100, offset: 0);
final documents = await lib.getDocuments(limit: 100, offset: 0);
```

Each returned element contains the following keys:
`id`, `name`, `uri`, `path`, `dateAdded`, `size`, `mimeType`, `type`,
`category`, `width`, `height`, `albumName`.
The `type` key is derived from the MIME type and may be `image`, `video`,
`audio` or `file`.

### Storage access modes

The behaviour depends on the permissions granted to the application. Use
`checkStorageAccess` to inspect the current mode for a folder:

```dart
final support = await lib.checkStorageAccess(folder: 'download');
// {
//   'supported':     true,
//   'accessMode':    'manage' | 'saf' | 'none',
//   'hasAccess':     true,
//   'requiresSaf':   false,
//   'requiresManage': false,
//   'reason':        '',
// }
```

- `accessMode == 'manage'` — full filesystem access via
  `MANAGE_EXTERNAL_STORAGE`. Enumeration uses `java.io.File`.
- `accessMode == 'saf'` — per-folder access via Storage Access Framework.
  When `requiresSaf == true`, the user must be prompted to pick the folder
  (see below) before enumeration works.
- `accessMode == 'none'` — no access. On Android 11+ enumeration of
  Download falls back to `MediaStore.Downloads` (a partial subset);
  Documents returns a permission error.

### Inspecting access with checkStorageAccess

`checkStorageAccess(folder: 'download' | 'document')` returns a map with
the keys shown above. Use it whenever the section changes or the app
resumes, then drive the UI accordingly (banner, permission button, etc.).

### SAF-based folder access

On Android 11+ without `MANAGE_EXTERNAL_STORAGE`, you can request access
to a specific folder through the system picker:

```dart
// Download
final bool hasAccess  = await lib.hasDownloadsFolderAccess();
final bool granted    = await lib.requestDownloadsFolderAccess();
final bool revoked    = await lib.revokeDownloadsFolderAccess();

// Documents
final bool hasAccessD = await lib.hasDocumentsFolderAccess();
final bool grantedD   = await lib.requestDocumentsFolderAccess();
final bool revokedD   = await lib.revokeDocumentsFolderAccess();
```

`request*FolderAccess` opens the system folder picker and returns `true`
once the user grants access. Persist the result — SAF access survives
restarts, so the prompt only needs to be shown once per folder. After a
grant, re-run `checkStorageAccess` to refresh `accessMode`.

When enumeration is attempted without SAF access, the plugin throws a
`PlatformException` with code `SAF_REQUIRED` or `PERMISSION_REQUIRED`.
Catch these and re-inspect storage access:

```dart
try {
  final items = await lib.getDownloads(limit: 500, offset: 0) ?? [];
} on PlatformException catch (e) {
  if (e.code == 'SAF_REQUIRED' || e.code == 'PERMISSION_REQUIRED') {
    final support = await lib.checkStorageAccess(folder: 'download');
    // update UI: show the folder-picker banner
  } else {
    rethrow;
  }
}
```

### MANAGE_EXTERNAL_STORAGE fallback

When `requiresManage == true` and the permission has not been granted,
`requestAllFilesPermission()` opens the system settings screen. Combine
this with `WidgetsBindingObserver` (see [All-files access](#all-files-access))
so the list refreshes once the user returns to the app.

## Reading file contents

`getFileBytes` reads a file by URI and returns the raw bytes:

```dart
final Uint8List? bytes = await lib.getFileBytes(uri);
```

Both `content://` and `file://` URIs are supported. For `content://` URIs
the plugin uses `ContentResolver.openInputStream`. For `file://` URIs the
file is read directly through the `java.io.File` API. Reading `file://`
URIs requires permission for the corresponding path.

## Getting file paths

`getFilePath` converts a `file://` URI (or an already-resolved path) to an
absolute filesystem path:

```dart
final String? path = await lib.getFilePath(uri);
```

On Windows, `file://` URIs are decoded locally by the Dart
implementation. On Android, the call is delegated to the platform channel.

## File picking (web and Windows)

The web and Windows implementations expose file picking through the HTML
File API / native file dialog:

```dart
final List<Map<String, dynamic>>? picked = await lib.pickFiles(
  multiple: true,
  allowedFileTypes: ['image/*', 'video/*'],
);

final List<Map<String, dynamic>> dropped =
    await lib.pickFilesFromDrop(files);

final Uint8List bytes = await lib.readWebFile(fileId);
final String url      = await lib.getWebFileUrl(fileId);
lib.disposeWebFile(fileId); // release the object URL
```

Returned entries include `id`, `name`, `size`, `type`, `mimeType`, `uri`,
`dateAdded`, `width`, `height`, `duration`, `albumId`, `albumName` and
`isWebFile`. Image dimensions and video duration are resolved
asynchronously.

On web, remember to call `disposeWebFile` when the object URL is no longer
needed to release memory.

## Video playback

A PlatformView backed by `android.media.MediaPlayer` is registered under
`device_media_library/video/view` and must be embedded via `AndroidView`.
Playback control:

```dart
await lib.initializeVideo(videoPath: path, autoPlay: true);
await lib.playVideo();
await lib.pauseVideo();
await lib.seekVideoTo(seconds);
await lib.setVideoVolume(0.5);
await lib.setVideoPlaybackSpeed(1.5);
await lib.resetVideo();
await lib.disposeVideo();
```

State queries:

```dart
final double? duration = await lib.getVideoDuration();
final double? position = await lib.getCurrentPosition();
final bool?   playing  = await lib.isVideoPlaying();
```

Playback events (`onPrepared`, `onPlaybackUpdate`, `onBufferingUpdate`,
`onCompleted`, `onError`, `onVideoSizeChanged`) are delivered through
`lib.videoEvents`.

## Web implementation

On web only the following methods are implemented:

- `pickFiles`
- `pickFilesFromDrop`
- `readWebFile`
- `getWebFileUrl`
- `disposeWebFile`
- `getFileBytes` (for `web_*` IDs)
- `getThumbnail` (for `web_*` image IDs)

Directory enumeration methods return empty lists. MediaStore-based
methods return empty lists. File contents are read through the HTML
`FileReader` API and object URLs through `URL.createObjectURL`.

The caller is responsible for calling `disposeWebFile` to release the
associated memory when the file is no longer needed.

## Return types

Methods that return collections use the following signature:

```dart
Future<List<Map<String, dynamic>>?>
```

The nullable return type indicates that `null` may be returned when the
underlying platform channel yields no result. In practice, on Android the
value is always a non-null list, possibly empty.

## Error codes

Errors are reported through `PlatformException`. The following codes are
used by the Android implementation:

| Code                | Description                                       |
|---------------------|---------------------------------------------------|
| `PERMISSION_DENIED` | The required permission has not been granted.     |
| `PERMISSION_REQUIRED` | Access is required but not granted (used by the file-enumeration APIs). |
| `SAF_REQUIRED`      | SAF folder access has not been granted yet.       |
| `QUERY_ERROR`       | The MediaStore query failed.                      |
| `ALBUM_ERROR`       | Album enumeration failed.                         |
| `THUMBNAIL_ERROR`   | Thumbnail generation failed.                      |
| `FILE_ERROR`        | File access failed.                               |
| `INVALID_ARGUMENT`  | An argument is missing or invalid.                |
| `NO_ACTIVITY`       | The plugin is not attached to an Activity.        |
| `REQUEST_IN_PROGRESS` | A permission request is already in progress.    |

## Limitations

1. Full enumeration of the Download and Documents directories on
   Android 11+ requires either `MANAGE_EXTERNAL_STORAGE` or SAF folder
   access. Google Play restricts the availability of `MANAGE_EXTERNAL_STORAGE`
   to applications whose core functionality depends on filesystem access
   (file managers, backup utilities, antivirus applications). Applications
   that do not satisfy this requirement should use the SAF dialog.
2. Reading arbitrary files from Download or Documents on Android 10
   without full access is not possible through this plugin.
   `MediaStore.Downloads` exposes only a subset of files.
3. Thumbnail generation is available only for entries indexed by
   MediaStore. For arbitrary files from Download or Documents, render
   icons based on MIME type.
4. The Windows and web implementations do not expose directory
   enumeration. They support file picking only.
5. On iOS, no implementation is provided. Calls return
   `UnimplementedError`.

## Example application

A complete example application is included under `example/`. It
demonstrates:

- Permission handling (`checkPermission` / `requestPermission`,
  `checkAllFilesPermission`, `requestAllFilesPermission`).
- Storage access inspection with `checkStorageAccess`.
- SAF folder access for Download and Documents.
- A media grid with lazy thumbnail loading.
- An audio list.
- File listing for Download and Documents with URI/path display and a
  "read bytes" action.
- Lifecycle-aware permission refresh via `WidgetsBindingObserver`.

See `example/lib/main.dart` for the full implementation.

## License

This project is distributed under the MIT License. See the `LICENSE`
file for details.
