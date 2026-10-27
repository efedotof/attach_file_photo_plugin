// ignore_for_file: deprecated_member_use

import 'dart:async';
import 'dart:html' as html;
import 'dart:typed_data';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';

import 'device_media_library_platform_interface.dart';

class DeviceMediaLibraryWeb extends DeviceMediaLibraryPlatform {
  static void registerWith(Registrar registrar) {
    DeviceMediaLibraryPlatform.instance = DeviceMediaLibraryWeb();
  }

  final Map<String, html.File> _fileCache = {};
  final Map<String, String> _objectUrlCache = {};

  @override
  Future<String?> getPlatformVersion() async {
    return html.window.navigator.userAgent;
  }

  @override
  Future<bool> checkPermission() async {
    return true;
  }

  @override
  Future<bool> requestPermission() async {
    return true;
  }

  @override
  Future<List<Map<String, dynamic>>?> pickFiles({
    bool multiple = true,
    List<String>? allowedFileTypes,
  }) async {
    final completer = Completer<List<Map<String, dynamic>>>();

    final input = html.InputElement(type: 'file');
    input.multiple = multiple;

    if (allowedFileTypes != null && allowedFileTypes.isNotEmpty) {
      input.accept = allowedFileTypes.join(',');
    } else {
      input.accept = 'image/*,video/*';
    }

    input.onChange.listen((event) {
      final files = input.files;
      if (files == null || files.isEmpty) {
        completer.complete([]);
        return;
      }

      final result = _processFileList(files);
      completer.complete(result);
    });

    input.click();

    return completer.future;
  }

  @override
  Future<List<Map<String, dynamic>>> pickFilesFromDrop(
    List<dynamic> files,
  ) async {
    final List<html.File> fileList = [];
    for (var file in files) {
      if (file is html.File) {
        fileList.add(file);
      }
    }
    return _processFileList(fileList);
  }

  List<Map<String, dynamic>> _processFileList(dynamic files) {
    final result = <Map<String, dynamic>>[];
    final int fileCount = files.length;

    for (int i = 0; i < fileCount; i++) {
      final file = files[i];
      final fileId = 'web_${DateTime.now().millisecondsSinceEpoch}_$i';

      _fileCache[fileId] = file;

      final mimeType = file.type;
      final fileType = _getFileTypeFromMime(mimeType);

      final fileInfo = {
        'id': fileId,
        'name': file.name,
        'size': file.size,
        'type': fileType,
        'mimeType': mimeType,
        'uri': fileId,
        'dateAdded': DateTime.now().millisecondsSinceEpoch,
        'width': 0,
        'height': 0,
        'duration': 0,
        'albumId': 'web',
        'albumName': 'Web Files',
        'isWebFile': true,
      };

      if (fileType == 'image') {
        _getImageDimensions(file).then((dimensions) {
          final index = result.indexWhere((item) => item['id'] == fileId);
          if (index != -1) {
            result[index]['width'] = dimensions['width'];
            result[index]['height'] = dimensions['height'];
          }
        });
      }

      if (fileType == 'video') {
        _getVideoDuration(file).then((duration) {
          final index = result.indexWhere((item) => item['id'] == fileId);
          if (index != -1) {
            result[index]['duration'] = duration;
          }
        });
      }

      result.add(fileInfo);
    }

    return result;
  }

  String _getFileTypeFromMime(String mimeType) {
    if (mimeType.startsWith('image/')) return 'image';
    if (mimeType.startsWith('video/')) return 'video';
    if (mimeType.startsWith('audio/')) return 'audio';
    return 'file';
  }

  Future<Map<String, int>> _getImageDimensions(html.File file) async {
    final completer = Completer<Map<String, int>>();

    final reader = html.FileReader();
    reader.readAsDataUrl(file);

    reader.onLoad.listen((event) {
      final img = html.ImageElement();
      img.src = reader.result as String;

      img.onLoad.listen((e) {
        completer.complete({
          'width': img.naturalWidth,
          'height': img.naturalHeight,
        });
      });

      img.onError.listen((e) {
        completer.complete({'width': 0, 'height': 0});
      });
    });

    reader.onError.listen((e) {
      completer.complete({'width': 0, 'height': 0});
    });

    return completer.future;
  }

  Future<int> _getVideoDuration(html.File file) async {
    final completer = Completer<int>();

    final video = html.VideoElement();
    final url = html.Url.createObjectUrlFromBlob(file);

    video.onLoadedMetadata.listen((event) {
      completer.complete(video.duration.round());
      html.Url.revokeObjectUrl(url);
    });

    video.onError.listen((event) {
      completer.complete(0);
      html.Url.revokeObjectUrl(url);
    });

    video.src = url;

    return completer.future;
  }

  @override
  Future<Uint8List> readWebFile(String fileId) async {
    final file = _fileCache[fileId];
    if (file == null) {
      throw Exception('File not found: $fileId');
    }

    final completer = Completer<Uint8List>();
    final reader = html.FileReader();

    reader.readAsArrayBuffer(file);

    reader.onLoad.listen((event) {
      final result = reader.result;
      if (result is ByteBuffer) {
        final bytes = Uint8List.view(result);
        completer.complete(bytes);
      } else {
        completer
            .completeError(Exception('Failed to read file as ArrayBuffer'));
      }
    });

    reader.onError.listen((event) {
      completer.completeError(Exception('Failed to read file'));
    });

    return completer.future;
  }

  @override
  Future<String> getWebFileUrl(String fileId) async {
    final file = _fileCache[fileId];
    if (file == null) {
      throw Exception('File not found: $fileId');
    }

    if (!_objectUrlCache.containsKey(fileId)) {
      final objectUrl = html.Url.createObjectUrlFromBlob(file);
      _objectUrlCache[fileId] = objectUrl;
    }

    return _objectUrlCache[fileId]!;
  }

  @override
  void disposeWebFile(String fileId) {
    if (_objectUrlCache.containsKey(fileId)) {
      html.Url.revokeObjectUrl(_objectUrlCache[fileId]!);
      _objectUrlCache.remove(fileId);
    }
    _fileCache.remove(fileId);
  }

  @override
  Future<List<Map<String, dynamic>>?> getImages({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    return [];
  }

  @override
  Future<List<Map<String, dynamic>>?> getVideos({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    return [];
  }

  @override
  Future<List<Map<String, dynamic>>?> getAllMedia({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    return [];
  }

  @override
  Future<List<Map<String, dynamic>>?> getAlbums() async {
    return [];
  }

  @override
  Future<List<Map<String, dynamic>>?> getMediaFromAlbum({
    required String albumId,
    int? limit,
    int? offset,
  }) async {
    return [];
  }

  @override
  Future<Uint8List?> getFileBytes(String uri) async {
    if (uri.startsWith('web_')) {
      return readWebFile(uri);
    }
    return null;
  }

  @override
  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) async {
    if (mediaId.startsWith('web_')) {
      final file = _fileCache[mediaId];
      if (file == null) return null;

      final completer = Completer<Uint8List?>();

      if (mediaType == 'image') {
        final reader = html.FileReader();
        reader.readAsDataUrl(file);

        reader.onLoad.listen((event) {
          final img = html.ImageElement();
          img.src = reader.result as String;

          img.onLoad.listen((e) {
            final canvas = html.CanvasElement()
              ..width = width
              ..height = height;

            final context = canvas.context2D;

            context.drawImageScaled(
              img,
              0,
              0,
              width.toDouble(),
              height.toDouble(),
            );

            final dataUrl = canvas.toDataUrl('image/jpeg', 0.85);
            final bytes = _dataUrlToBytes(dataUrl);
            completer.complete(bytes);
          });
        });

        reader.onError.listen((event) {
          completer.complete(null);
        });
      }

      return completer.future;
    }
    return null;
  }

  Uint8List _dataUrlToBytes(String dataUrl) {
    final parts = dataUrl.split(',');
    if (parts.length != 2) return Uint8List(0);

    final byteString = html.window.atob(parts[1]);
    final bytes = Uint8List(byteString.length);

    for (var i = 0; i < byteString.length; i++) {
      bytes[i] = byteString.codeUnitAt(i);
    }

    return bytes;
  }

  @override
  Future<bool> initializeVideo({
    required String videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) async {
    return false;
  }

  @override
  Future<void> playVideo() async {}

  @override
  Future<void> pauseVideo() async {}

  @override
  Future<void> seekVideoTo(double position) async {}

  @override
  Future<void> setVideoVolume(double volume) async {}

  @override
  Future<double?> getVideoDuration() async => null;

  @override
  Future<double?> getCurrentPosition() async => null;

  @override
  Future<bool?> isVideoPlaying() async => null;

  @override
  Future<void> resetVideo() async {}

  @override
  Future<void> disposeVideo() async {}

  @override
  Stream<Map<String, dynamic>> get videoEvents => const Stream.empty();

  @override
  Future<List<Map<String, dynamic>>?> getAudio({
    int? limit,
    int? offset,
  }) async =>
      [];

  @override
  Future<List<Map<String, dynamic>>?> getDownloads({
    int? limit,
    int? offset,
    bool recursive = true,
  }) async =>
      [];

  @override
  Future<List<Map<String, dynamic>>?> getDocuments({
    int? limit,
    int? offset,
    bool recursive = true,
  }) async =>
      [];

  @override
  Future<bool> checkAllFilesPermission() async => true;

  @override
  Future<bool> requestAllFilesPermission() async => true;
}
