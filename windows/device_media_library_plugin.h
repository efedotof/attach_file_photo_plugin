#ifndef FLUTTER_PLUGIN_DEVICE_MEDIA_LIBRARY_PLUGIN_H_
#define FLUTTER_PLUGIN_DEVICE_MEDIA_LIBRARY_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>
#include <flutter/event_channel.h>

#include <memory>

namespace device_media_library
{

    class DeviceMediaLibraryPlugin : public flutter::Plugin
    {
    public:
        static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

        explicit DeviceMediaLibraryPlugin(flutter::PluginRegistrarWindows *registrar);
        virtual ~DeviceMediaLibraryPlugin();

        DeviceMediaLibraryPlugin(const DeviceMediaLibraryPlugin &) = delete;
        DeviceMediaLibraryPlugin &operator=(const DeviceMediaLibraryPlugin &) = delete;

    private:
        void HandleMethodCall(
            const flutter::MethodCall<flutter::EncodableValue> &method_call,
            std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);

        void HandleVideoMethodCall(
            const flutter::MethodCall<flutter::EncodableValue> &method_call,
            std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);

        flutter::PluginRegistrarWindows *registrar_;
    };

}

#endif