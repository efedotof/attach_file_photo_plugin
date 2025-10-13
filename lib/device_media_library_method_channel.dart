import 'package:flutter/services.dart';
import 'device_media_library_platform_interface.dart';

class MethodChannelDeviceMediaLibrary extends DeviceMediaLibraryPlatform {
  final methodChannel = const MethodChannel('device_media_library');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<bool> checkPermission() async {
    final hasPermission =
        await methodChannel.invokeMethod<bool>('checkPermission');
    return hasPermission ?? false;
  }

  @override
  Future<bool> requestPermission() async {
    final hasPermission =
        await methodChannel.invokeMethod<bool>('requestPermission');
    return hasPermission ?? false;
  }

 @override
Future<List<Map<String, dynamic>>?> getImages({int? limit, int? offset, String? albumId}) async {
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
      'path': map['uri']?.toString() ?? map['path']?.toString() ?? '', 
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
  Future<List<Map<String, dynamic>>?> getVideos(
      {int? limit, int? offset, String? albumId}) async {
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
        'path': map['path']?.toString() ?? '',
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
  Future<List<Map<String, dynamic>>?> getAllMedia(
      {int? limit, int? offset, String? albumId}) async {
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
        'path': map['path']?.toString() ?? '',
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
  Future<List<Map<String, dynamic>>?> getMediaFromAlbum(
      {required String albumId, int? limit, int? offset}) async {
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
        'path': map['path']?.toString() ?? '',
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
    final result =
        await methodChannel.invokeMethod<Uint8List>('readFileByUri', {
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
    final result = await methodChannel.invokeMethod<Uint8List>('getThumbnail', {
      'mediaId': mediaId,
      'mediaType': mediaType,
      'width': width,
      'height': height,
    });
    return result;
  }

  int _parseInt(dynamic value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }
}
