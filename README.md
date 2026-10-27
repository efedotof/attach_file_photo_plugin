
# device_media_library

A Flutter plugin for accessing the device media library, the shared
Download directory, the Documents directory, and audio files on Android,
without invoking a system file picker.

The plugin implements platform channels for Android (Kotlin) and provides
a web implementation based on the HTML File API. A Dart-only Windows
implementation provides a partial fallback for desktop.

## Overview

The plugin exposes a unified Dart API that queries the Android MediaStore
for indexed media (images, video, audio) and reads the Download and
Documents directories through either the MediaStore or the java.io.File
API, depending on the permissions granted to the application.

Two permission models are supported:

1. The scoped permissions model (Android 10 and later), which provides
   access to media files through MediaStore and permits reading files
   created by the application itself.
2. The all-files access model (Android 11 and later), which permits
   direct filesystem access to the Download and Documents directories
   through the java.io.File API. This model requires the
   MANAGE_EXTERNAL_STORAGE permission, which Google Play reviews on a
   per-application basis.

## Features

- Retrieval of images, video and audio from MediaStore with pagination
  (limit and offset), album filtering and thumbnail generation.
- Enumeration of arbitrary files inside the Download directory through
  either the java.io.File API (full access) or MediaStore.Downloads
  (scoped access).
- Enumeration of arbitrary files inside the Documents directory. This
  functionality requires MANAGE_EXTERNAL_STORAGE on Android 11 and
  later.
- Reading file contents by URI, supporting both content:// and file://
  schemes.
- Streaming video playback through a PlatformView backed by
  android.media.MediaPlayer.
- Web implementation supporting file picking and drag-and-drop through
  the HTML File API.
- Capability to request and query both the scoped permissions
  (READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO) and the
  all-files access permission (MANAGE_EXTERNAL_STORAGE).

## Platform support

| Platform  | Status          | Notes                                               |
|-----------|-----------------|-----------------------------------------------------|
| Android   | Full            | Requires API 21 or later.                           |
| Web       | Partial         | File picking only. No access to system directories. |
| Windows   | Partial         | File picking only.                                  |
| iOS       | Not implemented | The platform stub returns UnimplementedError.       |

## Installation

Add the dependency to the pubspec.yaml file:

```yaml
dependencies:
  device_media_library:
    path: ../device_media_library
```

For a published package, replace the path with a version constraint.

## Android configuration

The following permissions must be declared in the application manifest
(android/app/src/main/AndroidManifest.xml):

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

The READ_EXTERNAL_STORAGE permission applies to Android 12 and earlier.
The READ_MEDIA_IMAGES, READ_MEDIA_VIDEO and READ_MEDIA_AUDIO permissions
apply to Android 13 and later. The MANAGE_EXTERNAL_STORAGE permission is
required only if direct filesystem access to the Download or Documents
directories is needed. The tools namespace must be declared in the
manifest element.

## Initialization

The plugin must be initialized before use:

```dart
import 'package:device_media_library/device_media_library.dart';

void main() {
  DeviceMediaLibrary.initialize();
  runApp(const MyApp());
}
```

The initialize method selects the appropriate platform implementation
based on defaultTargetPlatform. Calling ensureInitialized from within the
plugin methods is idempotent and safe to invoke repeatedly.

## Permissions

The plugin exposes four methods for permission management:

```dart
final lib = DeviceMediaLibrary();

// Scoped permissions (images, video, audio).
final bool hasBasic = await lib.checkPermission();
final bool granted  = await lib.requestPermission();

// All-files access (Android 11 and later).
final bool hasAll = await lib.checkAllFilesPermission();
await lib.requestAllFilesPermission();
```

The requestAllFilesPermission method opens the system settings screen
because Android does not expose a standard in-process dialog for this
permission. The method returns false immediately after launching the
settings screen. To determine whether the user actually granted the
permission, call checkAllFilesPermission after the application returns
to the foreground. This can be implemented using WidgetsBindingObserver
and the didChangeAppLifecycleState callback.

## Retrieving media from MediaStore

Images, video and audio are queried through the MediaStore content
provider. All methods support the limit and offset parameters for
pagination.

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

Each returned element is a map containing at least the following keys:
id, name, uri, dateAdded, size, type, width, height, albumId and
albumName. For video and audio entries, the map additionally contains a
duration key expressed in milliseconds. For audio entries, the map also
contains artist and album keys.

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

On Android 10 and later, image thumbnails are produced by
ContentResolver.loadThumbnail. Video thumbnails are produced by
MediaMetadataRetriever with a fallback to the default frame. Thumbnail
generation is not available for arbitrary files from Download or
Documents; for those files the caller should render an icon based on the
mimeType value.

## Reading files from Download and Documents

Two methods are provided for directory enumeration. Both accept limit,
offset and recursive parameters.

```dart
final downloads = await lib.getDownloads(limit: 100, offset: 0);
final documents = await lib.getDocuments(limit: 100, offset: 0);
```

The behaviour depends on the permissions granted to the application.

For Download:
- If MANAGE_EXTERNAL_STORAGE is granted or the Android version is below
  11, the plugin enumerates the physical Download directory through the
  java.io.File API and returns entries with file:// URIs.
- If MANAGE_EXTERNAL_STORAGE is not granted and the Android version is
  11 or later, the plugin falls back to MediaStore.Downloads and returns
  only those files that MediaStore exposes to the application.
- On Android 9 and earlier, the READ_EXTERNAL_STORAGE permission is
  sufficient for full enumeration.

For Documents:
- If MANAGE_EXTERNAL_STORAGE is granted or the Android version is below
  11, the plugin enumerates the physical Documents directory through the
  java.io.File API.
- Otherwise, the method returns a PERMISSION_DENIED error.

Each returned element contains the following keys: id, name, uri, path,
dateAdded, size, mimeType, type, category, width, height, albumName. The
type key is derived from the MIME type and may be one of image, video,
audio or file.

## Reading file contents

The getFileBytes method reads a file by URI and returns the raw bytes:

```dart
final Uint8List? bytes = await lib.getFileBytes(uri);
```

Both content:// and file:// URIs are supported. For content:// URIs the
plugin uses ContentResolver.openInputStream. For file:// URIs the plugin
reads the file directly through the java.io.File API. Reading file://
URIs requires that the application has permission to access the
corresponding path.

## Video playback

The plugin exposes a PlatformView for video playback based on
android.media.MediaPlayer. The platform view is registered under the
identifier device_media_library/video/view and must be created using
AndroidView in Dart code. Playback control is performed through the
following methods:

```dart
await lib.initializeVideo(videoPath: path, autoPlay: true);
await lib.playVideo();
await lib.pauseVideo();
await lib.seekVideoTo(seconds);
await lib.setVideoVolume(0.5);
await lib.setVideoPlaybackSpeed(1.5);
await lib.disposeVideo();
```

Playback events such as onPrepared, onPlaybackUpdate, onBufferingUpdate,
onCompleted, onError and onVideoSizeChanged are delivered through the
videoEvents stream.

## Web implementation

On web, only pickFiles, pickFilesFromDrop, readWebFile, getWebFileUrl
and disposeWebFile are implemented. Directory enumeration methods return
empty lists. File contents are read through the HTML FileReader API and
object URLs are created through URL.createObjectURL. The caller is
responsible for calling disposeWebFile to release the associated memory
when the file is no longer needed.

## Return types

Methods that return collections use the following signature:

```dart
Future<List<Map<String, dynamic>>?>
```

The nullable return type indicates that a null value may be returned when
the underlying platform channel yields no result. In practice, on Android
the value is always a non-null list, possibly empty.

## Error codes

Errors are reported through PlatformException. The following codes are
used by the Android implementation:

| Code                | Description                                     |
|---------------------|-------------------------------------------------|
| PERMISSION_DENIED   | The required permission has not been granted.   |
| QUERY_ERROR         | The MediaStore query failed.                    |
| ALBUM_ERROR         | Album enumeration failed.                       |
| THUMBNAIL_ERROR     | Thumbnail generation failed.                    |
| FILE_ERROR          | File access failed.                             |
| INVALID_ARGUMENT    | An argument is missing or invalid.              |
| NO_ACTIVITY         | The plugin is not attached to an Activity.      |
| REQUEST_IN_PROGRESS | A permission request is already in progress.    |

## Limitations

1. Full enumeration of the Download and Documents directories on Android
   11 and later requires MANAGE_EXTERNAL_STORAGE. Google Play restricts
   the availability of this permission to applications whose core
   functionality depends on filesystem access, for example file managers,
   backup utilities and antivirus applications. Applications that do not
   satisfy this requirement should use the Storage Access Framework
   dialog instead, which opens a system picker.

2. Reading arbitrary files from Download or Documents on Android 10
   without MANAGE_EXTERNAL_STORAGE is not possible through this plugin.
   The MediaStore.Downloads fallback exposes only a subset of files.

3. Thumbnail generation is available only for entries indexed by
   MediaStore. For arbitrary files from Download or Documents, the caller
   must render icons based on MIME type.

4. The Windows and web implementations do not expose directory
   enumeration. They support file picking only.

5. On iOS, no implementation is provided. Calls return
   UnimplementedError.

## Example application

See the example directory for a complete application demonstrating all
features of the plugin, including permission handling, media grid,
audio list and file listing.

## License

This project is distributed under the MIT License. See the LICENSE file
for details.
