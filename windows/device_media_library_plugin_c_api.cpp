#include "include/device_media_library/device_media_library_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "device_media_library_plugin.h"

void DeviceMediaLibraryPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  device_media_library::DeviceMediaLibraryPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
