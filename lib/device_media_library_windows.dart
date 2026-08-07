import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'device_media_library_platform_interface.dart';

class DeviceMediaLibraryWindows extends DeviceMediaLibraryPlatform {
  final MethodChannel _methodChannel =
      const MethodChannel('device_media_library');
  final MethodChannel _videoMethodChannel =
      const MethodChannel('device_media_library/video');
  final EventChannel _videoEventChannel =
      const EventChannel('device_media_library/video/events');

  Stream<Map<String, dynamic>>? _videoEventsStream;

  final Map<String, String> _objectUrlCache = {};

  @override
  Future<String?> getPlatformVersion() async {
    try {
      final version =
          await _methodChannel.invokeMethod<String>('getPlatformVersion');
      return 'Windows $version';
    } catch (e) {
      debugPrint('Error getting platform version: $e');
      return 'Windows';
    }
  }

  @override
  Future<bool> checkPermission() async {
    try {
      return await _methodChannel.invokeMethod('checkPermission') ?? true;
    } catch (e) {
      debugPrint('Error checking permission: $e');
      return true;
    }
  }

  @override
  Future<bool> requestPermission() async {
    try {
      return await _methodChannel.invokeMethod('requestPermission') ?? true;
    } catch (e) {
      debugPrint('Error requesting permission: $e');
      return true;
    }
  }

  @override
  Future<List<Map<String, dynamic>>?> pickFiles({
    bool multiple = true,
    List<String>? allowedFileTypes,
  }) async {
    try {
      final result = await _methodChannel.invokeMethod('pickFiles', {
        'multiple': multiple,
        'allowedFileTypes': allowedFileTypes ?? ['*'],
      });

      if (result is List) {
        return result
            .map<Map<String, dynamic>>(
              (e) => Map<String, dynamic>.from(e as Map),
            )
            .toList();
      }

      return [];
    } catch (e) {
      debugPrint('Error picking files on Windows: $e');
      return [];
    }
  }

  @override
  Future<List<Map<String, dynamic>>> pickFilesFromDrop(
    List<dynamic> files,
  ) async {
    try {
      final List<String> filePaths = files.whereType<String>().toList();

      final result =
          await _methodChannel.invokeMethod('pickFilesFromDrop', filePaths);

      if (result is List) {
        return result
            .map<Map<String, dynamic>>(
              (e) => Map<String, dynamic>.from(e as Map),
            )
            .toList();
      }

      return [];
    } catch (e) {
      debugPrint('Error processing drop files on Windows: $e');
      return [];
    }
  }

  @override
  Future<Uint8List?> getFileBytes(String uri) async {
    try {
      if (uri.startsWith('win_')) {
        return await readWebFile(uri);
      }

      final result = await _methodChannel.invokeMethod<Uint8List>(
        'readFileByUri',
        {'uri': uri},
      );
      return result;
    } catch (e) {
      debugPrint('Error reading file: $e');
      return null;
    }
  }

  @override
  Future<Uint8List> readWebFile(String fileId) async {
    try {
      final result = await _methodChannel.invokeMethod<Uint8List>(
        'readWebFile',
        {'fileId': fileId},
      );
      return result ?? Uint8List(0);
    } catch (e) {
      debugPrint('Error reading web file: $e');
      rethrow;
    }
  }

  @override
  Future<String> getWebFileUrl(String fileId) async {
    try {
      if (_objectUrlCache.containsKey(fileId)) {
        return _objectUrlCache[fileId]!;
      }

      final result = await _methodChannel.invokeMethod<String>(
        'getWebFileUrl',
        {'fileId': fileId},
      );

      if (result != null) {
        _objectUrlCache[fileId] = result;
        return result;
      }

      throw Exception('Failed to get file URL');
    } catch (e) {
      debugPrint('Error getting web file URL: $e');
      rethrow;
    }
  }

  @override
  void disposeWebFile(String fileId) {
    try {
      _methodChannel.invokeMethod('disposeWebFile', {'fileId': fileId});
      _objectUrlCache.remove(fileId);
    } catch (e) {
      debugPrint('Error disposing web file: $e');
    }
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

  @override
  Future<bool> initializeVideo({
    required String videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) async {
    if (videoPath.isEmpty && videoUri == null) {
      throw ArgumentError('Either videoPath or videoUri must be provided');
    }

    try {
      final bool? result =
          await _videoMethodChannel.invokeMethod('initialize', {
        'videoPath': videoPath,
        'videoUri': videoUri,
        'autoPlay': autoPlay,
        'volume': volume,
      });
      return result ?? false;
    } catch (e) {
      debugPrint('Error initializing video: $e');
      return false;
    }
  }

  @override
  Future<void> playVideo() async {
    try {
      await _videoMethodChannel.invokeMethod('play');
    } catch (e) {
      debugPrint('Error playing video: $e');
    }
  }

  @override
  Future<void> pauseVideo() async {
    try {
      await _videoMethodChannel.invokeMethod('pause');
    } catch (e) {
      debugPrint('Error pausing video: $e');
    }
  }

  @override
  Future<void> seekVideoTo(double position) async {
    try {
      await _videoMethodChannel.invokeMethod('seekTo', {'position': position});
    } catch (e) {
      debugPrint('Error seeking video: $e');
    }
  }

  @override
  Future<void> setVideoVolume(double volume) async {
    try {
      await _videoMethodChannel.invokeMethod('setVolume', {'volume': volume});
    } catch (e) {
      debugPrint('Error setting video volume: $e');
    }
  }

  @override
  Future<double?> getVideoDuration() async {
    try {
      return await _videoMethodChannel.invokeMethod<double>('getDuration');
    } catch (e) {
      debugPrint('Error getting video duration: $e');
      return null;
    }
  }

  @override
  Future<double?> getCurrentPosition() async {
    try {
      return await _videoMethodChannel
          .invokeMethod<double>('getCurrentPosition');
    } catch (e) {
      debugPrint('Error getting current position: $e');
      return null;
    }
  }

  @override
  Future<bool?> isVideoPlaying() async {
    try {
      return await _videoMethodChannel.invokeMethod<bool>('isPlaying');
    } catch (e) {
      debugPrint('Error checking if video is playing: $e');
      return null;
    }
  }

  @override
  Future<void> resetVideo() async {
    try {
      await _videoMethodChannel.invokeMethod('reset');
    } catch (e) {
      debugPrint('Error resetting video: $e');
    }
  }

  @override
  Future<void> disposeVideo() async {
    try {
      await _videoMethodChannel.invokeMethod('dispose');
    } catch (e) {
      debugPrint('Error disposing video: $e');
    }
  }

  @override
  Stream<Map<String, dynamic>> get videoEvents {
    _videoEventsStream ??= _videoEventChannel.receiveBroadcastStream().map(
        (event) => Map<String, dynamic>.from(event as Map<dynamic, dynamic>));
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
  Future<String?> getFilePath(String uri) async {
    if (uri.startsWith('file://')) {
      String path = uri.replaceFirst(RegExp(r'^file:///?'), '');
      path = Uri.decodeComponent(path);
      return path;
    }
    return null;
  }
}
