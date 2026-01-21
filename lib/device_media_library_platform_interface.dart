import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

abstract class DeviceMediaLibraryPlatform extends PlatformInterface {
  DeviceMediaLibraryPlatform() : super(token: _token);

  static final Object _token = Object();
  static DeviceMediaLibraryPlatform? _instance;

  static bool get isInitialized => _instance != null;

  static DeviceMediaLibraryPlatform get instance {
    if (_instance == null) {
      throw Exception(
        'DeviceMediaLibrary has not been initialized. '
        'Please call DeviceMediaLibrary.initialize()',
      );
    }
    return _instance!;
  }

  static set instance(DeviceMediaLibraryPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  static void initialize([DeviceMediaLibraryPlatform? platform]) {
    if (platform != null) {
      instance = platform;
    } else {}
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

  Future<List<Map<String, dynamic>>?> getImages({
    int? limit,
    int? offset,
    String? albumId,
  }) {
    throw UnimplementedError('getImages() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getVideos({
    int? limit,
    int? offset,
    String? albumId,
  }) {
    throw UnimplementedError('getVideos() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getAllMedia({
    int? limit,
    int? offset,
    String? albumId,
  }) {
    throw UnimplementedError('getAllMedia() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getAlbums() {
    throw UnimplementedError('getAlbums() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> getMediaFromAlbum({
    required String albumId,
    int? limit,
    int? offset,
  }) {
    throw UnimplementedError('getMediaFromAlbum() has not been implemented.');
  }

  Future<Uint8List?> getFileBytes(String uri) {
    throw UnimplementedError('getFileBytes() has not been implemented.');
  }

  Future<Uint8List?> getThumbnail({
    required String mediaId,
    required String mediaType,
    required int width,
    required int height,
  }) {
    throw UnimplementedError('getThumbnail() has not been implemented.');
  }

  Future<bool> initializeVideo({
    required String videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) {
    throw UnimplementedError('initializeVideo() has not been implemented.');
  }

  Future<void> playVideo() {
    throw UnimplementedError('playVideo() has not been implemented.');
  }

  Future<void> pauseVideo() {
    throw UnimplementedError('pauseVideo() has not been implemented.');
  }

  Future<void> seekVideoTo(double position) {
    throw UnimplementedError('seekVideoTo() has not been implemented.');
  }

  Future<void> setVideoVolume(double volume) {
    throw UnimplementedError('setVideoVolume() has not been implemented.');
  }

  Future<void> setVideoPlaybackSpeed(double speed) {
    throw UnimplementedError(
        'setVideoPlaybackSpeed() has not been implemented.');
  }

  Future<double?> getVideoDuration() {
    throw UnimplementedError('getVideoDuration() has not been implemented.');
  }

  Future<double?> getCurrentPosition() {
    throw UnimplementedError('getCurrentPosition() has not been implemented.');
  }

  Future<bool?> isVideoPlaying() {
    throw UnimplementedError('isVideoPlaying() has not been implemented.');
  }

  Future<void> resetVideo() {
    throw UnimplementedError('resetVideo() has not been implemented.');
  }

  Future<void> disposeVideo() {
    throw UnimplementedError('disposeVideo() has not been implemented.');
  }

  Stream<Map<String, dynamic>> get videoEvents {
    throw UnimplementedError('videoEvents getter has not been implemented.');
  }

  Future<List<Map<String, dynamic>>?> pickFiles({
    bool multiple = true,
    List<String>? allowedFileTypes,
  }) {
    throw UnimplementedError('pickFiles() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>> pickFilesFromDrop(
    List<dynamic> files,
  ) {
    throw UnimplementedError('pickFilesFromDrop() has not been implemented.');
  }

  Future<Uint8List> readWebFile(String fileId) {
    throw UnimplementedError('readWebFile() has not been implemented.');
  }

  Future<String> getWebFileUrl(String fileId) {
    throw UnimplementedError('getWebFileUrl() has not been implemented.');
  }

  void disposeWebFile(String fileId) {
    throw UnimplementedError('disposeWebFile() has not been implemented.');
  }
}
