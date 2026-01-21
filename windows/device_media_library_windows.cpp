#include "include/device_media_library/device_media_library_windows.h"
#include "device_media_library_plugin.h"

void DeviceMediaLibraryWindowsRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  device_media_library::DeviceMediaLibraryPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}