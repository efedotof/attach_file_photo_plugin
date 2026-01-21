import 'dart:async';
import 'package:flutter/foundation.dart';

import 'device_media_library_method_channel.dart';
import 'device_media_library_platform_interface.dart';
import 'device_media_library_windows.dart';

class DeviceMediaLibrary {
  static bool get isInitialized => DeviceMediaLibraryPlatform.isInitialized;

  static void initialize() {
    if (!isInitialized) {
      if (kIsWeb) {
      } else if (defaultTargetPlatform == TargetPlatform.windows) {
        DeviceMediaLibraryPlatform.instance = DeviceMediaLibraryWindows();
      } else {
        DeviceMediaLibraryPlatform.instance = MethodChannelDeviceMediaLibrary();
      }
    }
  }

  static void ensureInitialized() {
    if (!isInitialized) {
      initialize();
    }
  }

  Future<String?> getPlatformVersion() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getPlatformVersion();
  }

  Future<bool> checkPermission() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.checkPermission();
  }

  Future<bool> requestPermission() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.requestPermission();
  }

  Future<List<Map<String, dynamic>>?> getImages({
    int? limit,
    int? offset,
    String? albumId,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getImages(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getVideos({
    int? limit,
    int? offset,
    String? albumId,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getVideos(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getAllMedia({
    int? limit,
    int? offset,
    String? albumId,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getAllMedia(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getAlbums() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getAlbums();
  }

  Future<List<Map<String, dynamic>>?> getMediaFromAlbum({
    required String albumId,
    int? limit,
    int? offset,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getMediaFromAlbum(
      albumId: albumId,
      limit: limit,
      offset: offset,
    );
  }

  Future<Uint8List?> getFileBytes(String uri) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getFileBytes(uri);
  }

  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getThumbnail(
      mediaId: mediaId,
      mediaType: mediaType,
      width: width,
      height: height,
    );
  }

  Future<bool> initializeVideo({
    required String videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.initializeVideo(
      videoPath: videoPath,
      videoUri: videoUri,
      autoPlay: autoPlay,
      volume: volume,
    );
  }

  Future<void> playVideo() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.playVideo();
  }

  Future<void> pauseVideo() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.pauseVideo();
  }

  Future<void> seekVideoTo(double position) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.seekVideoTo(position);
  }

  Future<void> setVideoVolume(double volume) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.setVideoVolume(volume);
  }

  Future<void> setVideoPlaybackSpeed(double speed) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.setVideoPlaybackSpeed(speed);
  }

  Future<double?> getVideoDuration() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getVideoDuration();
  }

  Future<double?> getCurrentPosition() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getCurrentPosition();
  }

  Future<bool?> isVideoPlaying() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.isVideoPlaying();
  }

  Future<void> resetVideo() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.resetVideo();
  }

  Future<void> disposeVideo() {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.disposeVideo();
  }

  Stream<Map<String, dynamic>> get videoEvents {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.videoEvents;
  }

  Future<List<Map<String, dynamic>>?> pickFiles({
    bool multiple = true,
    List<String>? allowedFileTypes,
  }) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.pickFiles(
      multiple: multiple,
      allowedFileTypes: allowedFileTypes,
    );
  }

  Future<List<Map<String, dynamic>>> pickFilesFromDrop(
    List<dynamic> files,
  ) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.pickFilesFromDrop(files);
  }

  Future<Uint8List> readWebFile(String fileId) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.readWebFile(fileId);
  }

  Future<String> getWebFileUrl(String fileId) {
    ensureInitialized();
    return DeviceMediaLibraryPlatform.instance.getWebFileUrl(fileId);
  }

  void disposeWebFile(String fileId) {
    ensureInitialized();
    DeviceMediaLibraryPlatform.instance.disposeWebFile(fileId);
  }
}
