import 'dart:typed_data';

import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'device_media_library_method_channel.dart';

abstract class DeviceMediaLibraryPlatform extends PlatformInterface {
  DeviceMediaLibraryPlatform() : super(token: _token);

  static final Object _token = Object();
  static DeviceMediaLibraryPlatform _instance =
      MethodChannelDeviceMediaLibrary();

  static DeviceMediaLibraryPlatform get instance => _instance;

  static set instance(DeviceMediaLibraryPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<bool> checkPermission() {
    throw UnimplementedError('checkPermission() has not been implemented.');
  }

  Future<bool> requestPermission() {
    throw UnimplementedError('requestPermission() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getImages({int? limit, int? offset, String? albumId}) {
    throw UnimplementedError('getImages() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getVideos({int? limit, int? offset, String? albumId}) {
    throw UnimplementedError('getVideos() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getAllMedia({int? limit, int? offset, String? albumId}) {
    throw UnimplementedError('getAllMedia() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getAlbums() {
    throw UnimplementedError('getAlbums() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getMediaFromAlbum({required String albumId, int? limit, int? offset}) {
    throw UnimplementedError('getMediaFromAlbum() has not been implemented.');
  }

  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) {
    throw UnimplementedError('getThumbnail() has not been implemented.');
  }
}