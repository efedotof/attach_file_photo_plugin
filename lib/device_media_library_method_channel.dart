import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'device_media_library_platform_interface.dart';

class MethodChannelDeviceMediaLibrary extends DeviceMediaLibraryPlatform {
  final MethodChannel _methodChannel =
      const MethodChannel('device_media_library');
  final MethodChannel _videoMethodChannel =
      const MethodChannel('device_media_library/video');
  final EventChannel _videoEventChannel =
      const EventChannel('device_media_library/video/events');

  Stream<Map<String, dynamic>>? _videoEventsStream;

  @override
  Future<String?> getPlatformVersion() async {
    return _methodChannel.invokeMethod<String>('getPlatformVersion');
  }

  @override
  Future<bool> checkPermission() async {
    final bool? result = await _methodChannel.invokeMethod('checkPermission');
    return result ?? false;
  }

  @override
  Future<bool> requestPermission() async {
    final bool? result = await _methodChannel.invokeMethod('requestPermission');
    return result ?? false;
  }

  @override
  Future<List<Map<String, dynamic>>?> getImages({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    final List<dynamic>? result =
        await _methodChannel.invokeMethod('getImages', {
      'limit': limit,
      'offset': offset,
      'albumId': albumId,
    });

    if (result == null) return null;

    return result.map<Map<String, dynamic>>((item) {
      final map = Map<String, dynamic>.from(item as Map<dynamic, dynamic>);
      return {
        'id': map['id']?.toString() ?? '',
        'name': map['name']?.toString() ?? '',
        'uri': map['uri']?.toString() ?? '',
        'dateAdded': _parseInt(map['dateAdded']),
        'size': _parseInt(map['size']),
        'width': _parseInt(map['width']),
        'height': _parseInt(map['height']),
        'albumId': map['albumId']?.toString() ?? '',
        'albumName': map['albumName']?.toString() ?? '',
        'type': map['type']?.toString() ?? 'image',
      };
    }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>?> getVideos({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    final List<dynamic>? result =
        await _methodChannel.invokeMethod('getVideos', {
      'limit': limit,
      'offset': offset,
      'albumId': albumId,
    });

    if (result == null) return null;

    return result.map<Map<String, dynamic>>((item) {
      final map = Map<String, dynamic>.from(item as Map<dynamic, dynamic>);
      return {
        'id': map['id']?.toString() ?? '',
        'name': map['name']?.toString() ?? '',
        'uri': map['uri']?.toString() ?? '',
        'dateAdded': _parseInt(map['dateAdded']),
        'size': _parseInt(map['size']),
        'width': _parseInt(map['width']),
        'height': _parseInt(map['height']),
        'albumId': map['albumId']?.toString() ?? '',
        'albumName': map['albumName']?.toString() ?? '',
        'type': map['type']?.toString() ?? 'video',
        'duration': _parseInt(map['duration']),
      };
    }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>?> getAllMedia({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    final List<dynamic>? result =
        await _methodChannel.invokeMethod('getAllMedia', {
      'limit': limit,
      'offset': offset,
      'albumId': albumId,
    });

    if (result == null) return null;

    return result.map<Map<String, dynamic>>((item) {
      final map = Map<String, dynamic>.from(item as Map<dynamic, dynamic>);

      final mediaData = {
        'id': map['id']?.toString() ?? '',
        'name': map['name']?.toString() ?? '',
        'uri': map['uri']?.toString() ?? '',
        'dateAdded': _parseInt(map['dateAdded']),
        'size': _parseInt(map['size']),
        'width': _parseInt(map['width']),
        'height': _parseInt(map['height']),
        'albumId': map['albumId']?.toString() ?? '',
        'albumName': map['albumName']?.toString() ?? '',
        'type': map['type']?.toString() ?? 'image',
      };

      if (map['type'] == 'video') {
        mediaData['duration'] = _parseInt(map['duration']);
      }

      return mediaData;
    }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>?> getAlbums() async {
    final List<dynamic>? result =
        await _methodChannel.invokeMethod('getAlbums');

    if (result == null) return null;

    return result.map<Map<String, dynamic>>((item) {
      final map = Map<String, dynamic>.from(item as Map<dynamic, dynamic>);
      return {
        'id': map['id']?.toString() ?? '',
        'name': map['name']?.toString() ?? '',
        'coverPath': map['coverPath']?.toString() ?? '',
        'type': map['type']?.toString() ?? 'mixed',
      };
    }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>?> getMediaFromAlbum({
    required String albumId,
    int? limit,
    int? offset,
  }) async {
    final List<dynamic>? result =
        await _methodChannel.invokeMethod('getMediaFromAlbum', {
      'albumId': albumId,
      'limit': limit,
      'offset': offset,
    });

    if (result == null) return null;

    return result.map<Map<String, dynamic>>((item) {
      final map = Map<String, dynamic>.from(item as Map<dynamic, dynamic>);

      final mediaData = {
        'id': map['id']?.toString() ?? '',
        'name': map['name']?.toString() ?? '',
        'uri': map['uri']?.toString() ?? '',
        'dateAdded': _parseInt(map['dateAdded']),
        'size': _parseInt(map['size']),
        'width': _parseInt(map['width']),
        'height': _parseInt(map['height']),
        'albumId': map['albumId']?.toString() ?? '',
        'albumName': map['albumName']?.toString() ?? '',
        'type': map['type']?.toString() ?? 'image',
      };

      if (map['type'] == 'video') {
        mediaData['duration'] = _parseInt(map['duration']);
      }

      return mediaData;
    }).toList();
  }

  @override
  Future<Uint8List?> getFileBytes(String uri) async {
    final Uint8List? result =
        await _methodChannel.invokeMethod<Uint8List>('readFileByUri', {
      'uri': uri,
    });
    return result;
  }

  @override
  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) async {
    final Uint8List? result = await _methodChannel.invokeMethod<Uint8List>(
      'getThumbnail',
      {
        'mediaId': mediaId,
        'mediaType': mediaType,
        'width': width,
        'height': height,
      },
    );
    return result;
  }

  // ===================== Video Methods =====================

  @override
  Future<bool> initializeVideo({
    String? videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) async {
    if (videoPath == null && videoUri == null) {
      throw ArgumentError('Either videoPath or videoUri must be provided');
    }

    final bool? result = await _videoMethodChannel.invokeMethod('initialize', {
      'videoPath': videoPath,
      'videoUri': videoUri,
      'autoPlay': autoPlay,
      'volume': volume,
    });
    return result ?? false;
  }

  @override
  Future<void> playVideo() => _videoMethodChannel.invokeMethod('play');

  @override
  Future<void> pauseVideo() => _videoMethodChannel.invokeMethod('pause');

  @override
  Future<void> seekVideoTo(double position) =>
      _videoMethodChannel.invokeMethod('seekTo', {'position': position});

  @override
  Future<void> setVideoVolume(double volume) =>
      _videoMethodChannel.invokeMethod('setVolume', {'volume': volume});

  @override
  Future<double?> getVideoDuration() async =>
      await _videoMethodChannel.invokeMethod<double>('getDuration');

  @override
  Future<double?> getCurrentPosition() async =>
      await _videoMethodChannel.invokeMethod<double>('getCurrentPosition');

  @override
  Future<bool?> isVideoPlaying() async =>
      await _videoMethodChannel.invokeMethod<bool>('isPlaying');

  @override
  Future<void> resetVideo() => _videoMethodChannel.invokeMethod('reset');

  @override
  Future<void> disposeVideo() => _videoMethodChannel.invokeMethod('dispose');

  @override
  Stream<Map<String, dynamic>> get videoEvents {
    _videoEventsStream ??= _videoEventChannel
        .receiveBroadcastStream()
        .map((event) =>
            Map<String, dynamic>.from(event as Map<dynamic, dynamic>))
        .handleError((error) {
      if (kDebugMode) debugPrint('Video event error: $error');
    });
    return _videoEventsStream!;
  }

  int _parseInt(dynamic value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  @override
  Future<List<Map<String, dynamic>>?> pickFiles({
    bool multiple = true,
    List<String>? allowedFileTypes,
  }) async {
    if (defaultTargetPlatform == TargetPlatform.android) {
      return [];
    }
    throw UnimplementedError(
        'pickFiles() has not been implemented for this platform');
  }

  @override
  Future<List<Map<String, dynamic>>> pickFilesFromDrop(
    List<dynamic> files,
  ) async {
    // На Android это не поддерживается, возвращаем пустой список
    if (defaultTargetPlatform == TargetPlatform.android) {
      return [];
    }
    throw UnimplementedError(
        'pickFilesFromDrop() has not been implemented for this platform');
  }

  @override
  Future<Uint8List> readWebFile(String fileId) async {
    throw UnimplementedError(
        'readWebFile() has not been implemented for this platform');
  }

  @override
  Future<String> getWebFileUrl(String fileId) async {
    throw UnimplementedError(
        'getWebFileUrl() has not been implemented for this platform');
  }

  @override
  void disposeWebFile(String fileId) {
    // Ничего не делаем на Android
  }
}
