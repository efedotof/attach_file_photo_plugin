import 'dart:async';
import 'package:flutter/services.dart';
import 'device_media_library_platform_interface.dart';

class MethodChannelDeviceMediaLibrary extends DeviceMediaLibraryPlatform {
  final methodChannel = const MethodChannel('device_media_library');
  final videoMethodChannel = const MethodChannel('device_media_library/video');
  final videoEventChannel =
      const EventChannel('device_media_library/video/events');

  Stream<Map<String, dynamic>>? _videoEventsStream;

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<bool> checkPermission() async {
    final hasPermission = await methodChannel.invokeMethod<bool>(
      'checkPermission',
    );
    return hasPermission ?? false;
  }

  @override
  Future<bool> requestPermission() async {
    final hasPermission = await methodChannel.invokeMethod<bool>(
      'requestPermission',
    );
    return hasPermission ?? false;
  }

  @override
  Future<List<Map<String, dynamic>>?> getImages({
    int? limit,
    int? offset,
    String? albumId,
  }) async {
    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'getImages',
      {'limit': limit, 'offset': offset, 'albumId': albumId},
    );

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
    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'getVideos',
      {'limit': limit, 'offset': offset, 'albumId': albumId},
    );

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
    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'getAllMedia',
      {'limit': limit, 'offset': offset, 'albumId': albumId},
    );

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
    final result = await methodChannel.invokeMethod<List<dynamic>>('getAlbums');

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
    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'getMediaFromAlbum',
      {'albumId': albumId, 'limit': limit, 'offset': offset},
    );

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
    final result = await methodChannel.invokeMethod<Uint8List>(
      'readFileByUri',
      {'uri': uri},
    );
    return result;
  }

  @override
  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) async {
    final result = await methodChannel.invokeMethod<Uint8List>('getThumbnail', {
      'mediaId': mediaId,
      'mediaType': mediaType,
      'width': width,
      'height': height,
    });
    return result;
  }

  // Новые методы для видео-плеера

  @override
  Future<bool> initializeVideo({
    required String videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) async {
    final result = await videoMethodChannel.invokeMethod<bool>(
      'initialize',
      {
        'videoPath': videoPath,
        'videoUri': videoUri,
        'autoPlay': autoPlay,
        'volume': volume,
      },
    );
    return result ?? false;
  }

  @override
  Future<void> playVideo() async {
    await videoMethodChannel.invokeMethod('play');
  }

  @override
  Future<void> pauseVideo() async {
    await videoMethodChannel.invokeMethod('pause');
  }

  @override
  Future<void> seekVideoTo(double position) async {
    await videoMethodChannel.invokeMethod('seekTo', {'position': position});
  }

  @override
  Future<void> setVideoVolume(double volume) async {
    await videoMethodChannel.invokeMethod('setVolume', {'volume': volume});
  }

  @override
  Future<void> setVideoPlaybackSpeed(double speed) async {
    await videoMethodChannel.invokeMethod('setPlaybackSpeed', {'speed': speed});
  }

  @override
  Future<double?> getVideoDuration() async {
    return await videoMethodChannel.invokeMethod<double>('getDuration');
  }

  @override
  Future<void> disposeVideo() async {
    await videoMethodChannel.invokeMethod('dispose');
  }

  @override
  Stream<Map<String, dynamic>> get videoEvents {
    _videoEventsStream ??= videoEventChannel.receiveBroadcastStream().map(
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
}
