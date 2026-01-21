#ifndef FLUTTER_PLUGIN_DEVICE_MEDIA_LIBRARY_PLUGIN_H_
#define FLUTTER_PLUGIN_DEVICE_MEDIA_LIBRARY_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

namespace device_media_library {

class DeviceMediaLibraryPlugin : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  DeviceMediaLibraryPlugin();

  virtual ~DeviceMediaLibraryPlugin();

  // Disallow copy and assign.
  DeviceMediaLibraryPlugin(const DeviceMediaLibraryPlugin&) = delete;
  DeviceMediaLibraryPlugin& operator=(const DeviceMediaLibraryPlugin&) = delete;

  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

}  // namespace device_media_library

#endif  // FLUTTER_PLUGIN_DEVICE_MEDIA_LIBRARY_PLUGIN_H_
