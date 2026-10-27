# device_media_library_example

An example application demonstrating the capabilities of the
device_media_library plugin.

## Overview

The application provides four sections, each corresponding to a distinct
source of data exposed by the plugin:

1. Media: images and video retrieved from MediaStore, displayed as a
   thumbnail grid.
2. Audio: audio files retrieved from MediaStore.Audio, displayed as a
   list.
3. Download: files enumerated from the Download directory through either
   the java.io.File API or MediaStore.Downloads.
4. Documents: files enumerated from the Documents directory. This section
   requires MANAGE_EXTERNAL_STORAGE on Android 11 and later.

Navigation between sections is performed through a ChoiceChip row in the
application bar. A refresh action reloads the data of the current
section. A linear progress indicator is displayed below the section
selector while data is being loaded.

## Permission handling

At startup, the application requests the scoped permissions required for
media access through the requestPermission method of the plugin. The
result is stored in the application state and used to determine whether
the media and audio sections may be populated.

If the currently selected section requires MANAGE_EXTERNAL_STORAGE and
the permission has not been granted, a banner is displayed at the top of
the content area. The banner contains a description of the missing
permission and a button that invokes the requestAllFilesPermission
method. This method opens the corresponding system settings screen,
where the user may grant the permission manually.

The application observes the application lifecycle through
WidgetsBindingObserver. When the application returns to the foreground
after the didChangeAppLifecycleState callback reports the resumed state,
the all-files permission status is re-evaluated through the
checkAllFilesPermission method. If the permission state has changed and
the currently selected section depends on it, the data of that section
is reloaded automatically.

## Data loading

Each section has an associated loading routine, selected by the current
value of the LibrarySection enumeration. The routines are as follows:

- Media: the getAllMedia method is invoked with a limit of 200 and an
  offset of 0. The returned entries are stored in the state and passed
  to the thumbnail preloader.
- Audio: the getAudio method is invoked with a limit of 200 and an
  offset of 0. The returned entries are stored in the state.
- Download: the getDownloads method is invoked with a limit of 200, an
  offset of 0 and the recursive parameter set to true. If the call
  raises an exception, the error is reported through a SnackBar.
- Documents: the getDocuments method is invoked with the same
  parameters. If the call raises an exception, the error is reported
  through a SnackBar.

The loading routine sets the isLoading state to true on entry and to
false on exit, regardless of whether an error occurred. The linear
progress indicator in the application bar reflects this state.

## Thumbnail handling

Thumbnails are loaded lazily and cached in memory. Two maps are
maintained:

- A cache of successfully loaded thumbnails, keyed by media identifier.
- A map of pending completers, keyed by media identifier, used to
  deduplicate concurrent requests for the same media item.

When the media grid is populated, the preload routine iterates over the
newly added entries and initiates a thumbnail request for each entry
that is not present in either map. The request is issued through the
getThumbnail method with a target size of 200 by 200 pixels.

The thumbnail future is resolved by the FutureBuilder widget in the grid
cell. If the future completes with a non-null value, the returned bytes
are rendered through Image.memory with a cacheWidth and cacheHeight
matching the target size. If the future completes with null or raises an
error, a placeholder container with an icon corresponding to the media
type is rendered instead.

## Interaction

Tapping an item opens a modal bottom sheet that displays the file
metadata. The displayed fields depend on the available keys in the
entry map and may include the following:

- Name
- Type
- MIME type
- Size, formatted as bytes, kilobytes, megabytes or gigabytes
- Duration, formatted as minutes and seconds or hours, minutes and
  seconds where applicable
- URI
- Absolute path

The following actions are available in the bottom sheet:

- Copy URI: copies the URI of the entry to the system clipboard through
  Clipboard.setData and displays a confirmation SnackBar.
- Read: invokes the getFileBytes method with the URI of the entry. If the
  returned bytes are non-null and the entry type is image, the bytes are
  displayed in a dialog through Image.memory inside an InteractiveViewer.
  Otherwise, a SnackBar reports the number of bytes read.

A long press on any item copies its URI to the system clipboard without
opening the bottom sheet. This behavior applies to all four sections.

## Running the example

The example targets Android primarily. On web and Windows the
application exposes the file picking functionality in place of the
directory enumeration sections, because those platforms do not provide
MediaStore or unrestricted filesystem access through the plugin.

The application is launched with the following commands:

```bash
flutter pub get
flutter run
```

## Testing on Android

To verify the Download and Documents sections on a device running
Android 11 or later, proceed as follows:

1. Launch the application.
2. Grant the media permissions when the system dialog appears.
3. Switch to the Download section. If the section remains empty or if
   the banner appears, tap the button in the banner to open the system
   settings.
4. Enable the all-files access permission for the application in the
   settings.
5. Return to the application. The section reloads automatically upon
   the resumed lifecycle state.

To verify behaviour under the scoped permissions model, revoke the
all-files access permission in the settings and observe the fallback to
MediaStore.Downloads. Under this model, only files created by the
application itself, or files exposed by MediaStore for other reasons,
are listed. Files placed in the Download directory by other applications
will not appear.

To verify behaviour on Android 10, launch the application on a device
running that version. The all-files access permission is not applicable
on that version, and the Download directory is enumerated through the
java.io.File API provided the READ_EXTERNAL_STORAGE permission has been
granted.

## Project structure

The application is contained in a single source file, lib/main.dart. The
file is organized as follows:

- The main function, which initializes the plugin and starts the
  application.
- The MyApp widget and the MyAppState state class.
- State fields for permission status, section selection, loaded data,
  thumbnail cache and loading state.
- The bootstrap routine, which requests permissions and triggers the
  initial data load.
- The refreshAllFilesPermission routine, which re-evaluates the
  all-files access permission when the application resumes.
- The loadCurrentSection routine, which dispatches to the appropriate
  loading routine based on the selected section.
- The thumbnail preloading and loading routines.
- Utility functions for formatting sizes and durations, selecting icons
  and colors by media type, and reading map values with type coercion.
- Builder widgets for each section: the media grid, the audio list and
  the file list.
- The file metadata bottom sheet and the associated actions.
- The permission banner.
- The build method, which assembles the application bar, the section
  selector, the banner and the content area.

## Dependencies

The example depends on the following packages:

- device_media_library, through a path dependency pointing to the parent
  directory.
- flutter, through the Flutter SDK.
- cupertino_icons, for the Cupertino icon set.

Development dependencies include flutter_test, integration_test and
flutter_lints.

## Limitations

1. The example does not implement video playback through the PlatformView
   exposed by the plugin. Only file enumeration, metadata display and
   byte reading are demonstrated.

2. The example does not implement paging through large collections. Each
   section loads up to 200 entries on activation and does not fetch
   further entries when the user scrolls. Applications that need to
   present larger collections should implement incremental loading with
   the limit and offset parameters.

3. The example does not implement file export, sharing or writing.
   Reading is performed exclusively in memory for the purpose of
   previewing the content.

4. On web and Windows, the directory enumeration sections are not
   displayed because the underlying plugin methods return empty lists on
   those platforms. The file picker remains available.

## License

The example is distributed under the same license as the plugin. See the
LICENSE file in the project root for details.
