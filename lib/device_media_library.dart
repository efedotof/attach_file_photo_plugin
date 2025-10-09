import 'dart:typed_data';
import 'device_media_library_platform_interface.dart';

class DeviceMediaLibrary {
  Future<String?> getPlatformVersion() {
    return DeviceMediaLibraryPlatform.instance.getPlatformVersion();
  }

  Future<bool> checkPermission() {
    return DeviceMediaLibraryPlatform.instance.checkPermission();
  }

  Future<bool> requestPermission() {
    return DeviceMediaLibraryPlatform.instance.requestPermission();
  }

  Future<List<Map<String, dynamic>>?> getImages({int? limit, int? offset, String? albumId}) {
    return DeviceMediaLibraryPlatform.instance.getImages(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getVideos({int? limit, int? offset, String? albumId}) {
    return DeviceMediaLibraryPlatform.instance.getVideos(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getAllMedia({int? limit, int? offset, String? albumId}) {
    return DeviceMediaLibraryPlatform.instance.getAllMedia(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getAlbums() {
    return DeviceMediaLibraryPlatform.instance.getAlbums();
  }

  Future<List<Map<String, dynamic>>?> getMediaFromAlbum({required String albumId, int? limit, int? offset}) {
    return DeviceMediaLibraryPlatform.instance.getMediaFromAlbum(
      albumId: albumId,
      limit: limit,
      offset: offset,
    );
  }

  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) {
    return DeviceMediaLibraryPlatform.instance.getThumbnail(
      mediaId: mediaId,
      mediaType: mediaType,
      width: width,
      height: height,
    );
  }
}