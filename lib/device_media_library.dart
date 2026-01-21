import 'dart:async';
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

  Future<List<Map<String, dynamic>>?> getImages(
      {int? limit, int? offset, String? albumId}) {
    return DeviceMediaLibraryPlatform.instance.getImages(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getVideos(
      {int? limit, int? offset, String? albumId}) {
    return DeviceMediaLibraryPlatform.instance.getVideos(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getAllMedia(
      {int? limit, int? offset, String? albumId}) {
    return DeviceMediaLibraryPlatform.instance.getAllMedia(
      limit: limit,
      offset: offset,
      albumId: albumId,
    );
  }

  Future<List<Map<String, dynamic>>?> getAlbums() {
    return DeviceMediaLibraryPlatform.instance.getAlbums();
  }

  Future<List<Map<String, dynamic>>?> getMediaFromAlbum(
      {required String albumId, int? limit, int? offset}) {
    return DeviceMediaLibraryPlatform.instance.getMediaFromAlbum(
      albumId: albumId,
      limit: limit,
      offset: offset,
    );
  }

  Future<Uint8List?> getFileBytes(String uri) async {
    return DeviceMediaLibraryPlatform.instance.getFileBytes(uri);
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

  Future<bool> initializeVideo({
    required String videoPath,
    String? videoUri,
    bool autoPlay = false,
    double volume = 1.0,
  }) {
    return DeviceMediaLibraryPlatform.instance.initializeVideo(
      videoPath: videoPath,
      videoUri: videoUri,
      autoPlay: autoPlay,
      volume: volume,
    );
  }

  Future<void> playVideo() {
    return DeviceMediaLibraryPlatform.instance.playVideo();
  }

  Future<void> pauseVideo() {
    return DeviceMediaLibraryPlatform.instance.pauseVideo();
  }

  Future<void> seekVideoTo(double position) {
    return DeviceMediaLibraryPlatform.instance.seekVideoTo(position);
  }

  Future<void> setVideoVolume(double volume) {
    return DeviceMediaLibraryPlatform.instance.setVideoVolume(volume);
  }

  Future<void> setVideoPlaybackSpeed(double speed) {
    return DeviceMediaLibraryPlatform.instance.setVideoPlaybackSpeed(speed);
  }

  Future<double?> getVideoDuration() {
    return DeviceMediaLibraryPlatform.instance.getVideoDuration();
  }

  Future<double?> getCurrentPosition() {
    return DeviceMediaLibraryPlatform.instance.getCurrentPosition();
  }

  Future<bool?> isVideoPlaying() {
    return DeviceMediaLibraryPlatform.instance.isVideoPlaying();
  }

  Future<void> resetVideo() {
    return DeviceMediaLibraryPlatform.instance.resetVideo();
  }

  Future<void> disposeVideo() {
    return DeviceMediaLibraryPlatform.instance.disposeVideo();
  }

  Stream<Map<String, dynamic>> get videoEvents {
    return DeviceMediaLibraryPlatform.instance.videoEvents;
  }

  Future<List<Map<String, dynamic>>?> pickFiles({
    bool multiple = true,
    List<String>? allowedFileTypes,
  }) {
    return DeviceMediaLibraryPlatform.instance.pickFiles(
      multiple: multiple,
      allowedFileTypes: allowedFileTypes,
    );
  }

  Future<List<Map<String, dynamic>>> pickFilesFromDrop(
    List<dynamic> files,
  ) {
    return DeviceMediaLibraryPlatform.instance.pickFilesFromDrop(files);
  }

  Future<Uint8List> readWebFile(String fileId) {
    return DeviceMediaLibraryPlatform.instance.readWebFile(fileId);
  }

  Future<String> getWebFileUrl(String fileId) {
    return DeviceMediaLibraryPlatform.instance.getWebFileUrl(fileId);
  }

  void disposeWebFile(String fileId) {
    DeviceMediaLibraryPlatform.instance.disposeWebFile(fileId);
  }
}
