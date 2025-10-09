import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:device_media_library/device_media_library.dart';
import 'package:device_media_library/device_media_library_platform_interface.dart';
import 'package:device_media_library/device_media_library_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockDeviceMediaLibraryPlatform
    with MockPlatformInterfaceMixin
    implements DeviceMediaLibraryPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<List<Map<String, dynamic>>?> getImages({int? limit, int? offset}) {
    // TODO: implement getImages
    throw UnimplementedError();
  }

  @override
  Future<Uint8List?> getThumbnail({required String imageId, required int width, required int height}) {
    // TODO: implement getThumbnail
    throw UnimplementedError();
  }
}

void main() {
  final DeviceMediaLibraryPlatform initialPlatform = DeviceMediaLibraryPlatform.instance;

  test('$MethodChannelDeviceMediaLibrary is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelDeviceMediaLibrary>());
  });

  test('getPlatformVersion', () async {
    DeviceMediaLibrary deviceMediaLibraryPlugin = DeviceMediaLibrary();
    MockDeviceMediaLibraryPlatform fakePlatform = MockDeviceMediaLibraryPlatform();
    DeviceMediaLibraryPlatform.instance = fakePlatform;

    expect(await deviceMediaLibraryPlugin.getPlatformVersion(), '42');
  });
}
