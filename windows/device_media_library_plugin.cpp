#include "device_media_library_plugin.h"

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>
#include <flutter/standard_method_codec.h>
#include <flutter/event_channel.h>
#include <flutter/event_stream_handler.h>

#include <memory>
#include <sstream>
#include <windows.h>
#include <shobjidl.h>
#include <shlwapi.h>
#include <objbase.h>
#include <comdef.h>
#include <fileapi.h>
#include <atlstr.h>
#include <atlbase.h>
#include <fstream>
#include <vector>
#include <chrono>
#include <thread>
#include <wrl/client.h>
#include <algorithm>
#include <cctype>
#include <string>
#include <unordered_map>
#include <mutex>
#include <shellapi.h>

#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "oleaut32.lib")
#pragma comment(lib, "shell32.lib")

using namespace Microsoft::WRL;

namespace
{
    constexpr auto kChannelName = "device_media_library";
    constexpr auto kVideoChannelName = "device_media_library/video";
    constexpr auto kVideoEventChannelName = "device_media_library/video/events";

    class FileManager {
    private:
        static std::unordered_map<std::string, std::string> file_cache_;
        static std::unordered_map<std::string, std::string> object_url_cache_;
        static std::mutex cache_mutex_;

    public:
        static std::string GenerateFileId(const std::string& file_path) {
            return "win_" + std::to_string(GetTickCount64()) + "_" + std::to_string(std::hash<std::string>{}(file_path));
        }

        static void CacheFile(const std::string& file_id, const std::string& file_path) {
            std::lock_guard<std::mutex> lock(cache_mutex_);
            file_cache_[file_id] = file_path;
        }

        static std::string GetCachedFilePath(const std::string& file_id) {
            std::lock_guard<std::mutex> lock(cache_mutex_);
            auto it = file_cache_.find(file_id);
            if (it != file_cache_.end()) {
                return it->second;
            }
            return "";
        }

        static void CacheObjectUrl(const std::string& file_id, const std::string& object_url) {
            std::lock_guard<std::mutex> lock(cache_mutex_);
            object_url_cache_[file_id] = object_url;
        }

        static std::string GetCachedObjectUrl(const std::string& file_id) {
            std::lock_guard<std::mutex> lock(cache_mutex_);
            auto it = object_url_cache_.find(file_id);
            if (it != object_url_cache_.end()) {
                return it->second;
            }
            return "";
        }

        static void RemoveFile(const std::string& file_id) {
            std::lock_guard<std::mutex> lock(cache_mutex_);
            file_cache_.erase(file_id);
            object_url_cache_.erase(file_id);
        }

        static void ClearCache() {
            std::lock_guard<std::mutex> lock(cache_mutex_);
            file_cache_.clear();
            object_url_cache_.clear();
        }
    };

    std::unordered_map<std::string, std::string> FileManager::file_cache_;
    std::unordered_map<std::string, std::string> FileManager::object_url_cache_;
    std::mutex FileManager::cache_mutex_;

    std::wstring StringToWString(const std::string &str)
    {
        if (str.empty())
            return std::wstring();
        int size_needed = MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), NULL, 0);
        std::wstring wstrTo(size_needed, 0);
        MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), &wstrTo[0], size_needed);
        return wstrTo;
    }

    std::string WStringToString(const std::wstring &wstr)
    {
        if (wstr.empty())
            return std::string();
        int size_needed = WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), NULL, 0, NULL, NULL);
        std::string strTo(size_needed, 0);
        WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), &strTo[0], size_needed, NULL, NULL);
        return strTo;
    }

    int64_t GetFileSize(const std::string &file_path)
    {
        std::wstring wpath = StringToWString(file_path);
        HANDLE hFile = CreateFileW(wpath.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL,
                                   OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);

        if (hFile == INVALID_HANDLE_VALUE)
            return 0;

        LARGE_INTEGER size;
        if (!GetFileSizeEx(hFile, &size))
        {
            CloseHandle(hFile);
            return 0;
        }

        CloseHandle(hFile);
        return size.QuadPart;
    }

    int64_t GetFileModifiedTime(const std::string &file_path)
    {
        std::wstring wpath = StringToWString(file_path);
        HANDLE hFile = CreateFileW(wpath.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL,
                                   OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);

        if (hFile == INVALID_HANDLE_VALUE)
            return 0;

        FILETIME ftCreate, ftAccess, ftWrite;
        if (!GetFileTime(hFile, &ftCreate, &ftAccess, &ftWrite))
        {
            CloseHandle(hFile);
            return 0;
        }

        CloseHandle(hFile);

        ULARGE_INTEGER uli;
        uli.LowPart = ftWrite.dwLowDateTime;
        uli.HighPart = ftWrite.dwHighDateTime;

        const int64_t WINDOWS_TICK = 10000000;
        const int64_t SEC_TO_UNIX_EPOCH = 11644473600LL;

        int64_t unix_time = (uli.QuadPart / WINDOWS_TICK) - SEC_TO_UNIX_EPOCH;
        return unix_time * 1000;
    }

    std::string GetFileExtension(const std::string &file_path)
    {
        size_t dot_pos = file_path.find_last_of('.');
        if (dot_pos != std::string::npos)
        {
            return file_path.substr(dot_pos + 1);
        }
        return "";
    }

    std::string GetFileName(const std::string &file_path)
    {
        size_t slash_pos = file_path.find_last_of("\\/");
        if (slash_pos != std::string::npos)
        {
            return file_path.substr(slash_pos + 1);
        }
        return file_path;
    }

    std::string GetFileType(const std::string &extension)
    {
        std::string ext_lower = extension;
        std::transform(ext_lower.begin(), ext_lower.end(), ext_lower.begin(),
                       [](unsigned char c)
                       { return static_cast<char>(std::tolower(c)); });

        static const std::vector<std::string> image_extensions = {
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "ico"};

        static const std::vector<std::string> video_extensions = {
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "mpeg", "mpg", "3gp"};

        for (const auto &ext : image_extensions)
        {
            if (ext_lower == ext)
                return "image";
        }

        for (const auto &ext : video_extensions)
        {
            if (ext_lower == ext)
                return "video";
        }

        return "file";
    }

    std::string GetMimeType(const std::string &extension)
    {
        std::string ext_lower = extension;
        std::transform(ext_lower.begin(), ext_lower.end(), ext_lower.begin(),
                       [](unsigned char c)
                       { return static_cast<char>(std::tolower(c)); });

        if (ext_lower == "jpg" || ext_lower == "jpeg")
            return "image/jpeg";
        if (ext_lower == "png")
            return "image/png";
        if (ext_lower == "gif")
            return "image/gif";
        if (ext_lower == "bmp")
            return "image/bmp";
        if (ext_lower == "webp")
            return "image/webp";
        if (ext_lower == "ico")
            return "image/x-icon";
        if (ext_lower == "mp4")
            return "video/mp4";
        if (ext_lower == "avi")
            return "video/x-msvideo";
        if (ext_lower == "mkv")
            return "video/x-matroska";
        if (ext_lower == "mov")
            return "video/quicktime";
        if (ext_lower == "wmv")
            return "video/x-ms-wmv";

        return "application/octet-stream";
    }

    std::vector<uint8_t> ReadFileBytes(const std::string &file_path)
    {
        std::ifstream file(file_path, std::ios::binary | std::ios::ate);
        if (!file.is_open())
        {
            return {};
        }

        std::streamsize size = file.tellg();
        file.seekg(0, std::ios::beg);

        std::vector<uint8_t> buffer(static_cast<size_t>(size));
        if (!file.read(reinterpret_cast<char *>(buffer.data()), size))
        {
            return {};
        }

        return buffer;
    }

    std::string GetWindowsVersion()
    {
        std::ostringstream version_stream;
        version_stream << "Windows";

        HMODULE hNtDll = GetModuleHandleW(L"ntdll.dll");
        if (hNtDll)
        {
            typedef LONG(WINAPI * RtlGetVersionPtr)(PRTL_OSVERSIONINFOW);
            RtlGetVersionPtr RtlGetVersion = reinterpret_cast<RtlGetVersionPtr>(
                GetProcAddress(hNtDll, "RtlGetVersion"));

            if (RtlGetVersion)
            {
                RTL_OSVERSIONINFOW osvi = {0};
                osvi.dwOSVersionInfoSize = sizeof(osvi);
                if (RtlGetVersion(&osvi) == 0)
                {
                    version_stream << " " << osvi.dwMajorVersion << "." << osvi.dwMinorVersion;
                    if (osvi.dwBuildNumber != 0)
                    {
                        version_stream << "." << osvi.dwBuildNumber;
                    }
                    return version_stream.str();
                }
            }
        }

        OSVERSIONINFO osvi;
        ZeroMemory(&osvi, sizeof(OSVERSIONINFO));
        osvi.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);

#pragma warning(push)
#pragma warning(disable : 4996)
        if (GetVersionEx(&osvi))
        {
            version_stream << " " << osvi.dwMajorVersion << "." << osvi.dwMinorVersion;
        }
#pragma warning(pop)

        return version_stream.str();
    }

    std::string GetFileUrlForWindows(const std::string& file_path) {
        std::string result = "file:///";
        
        for (char c : file_path) {
            if (c == '\\') {
                result += '/';
            } else if (c == ' ') {
                result += "%20";
            } else {
                result += c;
            }
        }
        
        return result;
    }

    class FilePicker
    {
    public:
        static std::vector<std::string> PickFiles(bool multiple, const std::vector<std::string> &filters)
        {
            std::vector<std::string> selected_files;

            HRESULT hr = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
            if (FAILED(hr))
                return selected_files;

            ComPtr<IFileOpenDialog> file_dialog;
            hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_ALL,
                                  IID_PPV_ARGS(&file_dialog));

            if (SUCCEEDED(hr))
            {
                DWORD options;
                file_dialog->GetOptions(&options);
                file_dialog->SetOptions(options | FOS_FORCEFILESYSTEM | FOS_FILEMUSTEXIST | FOS_PATHMUSTEXIST);

                if (multiple)
                {
                    file_dialog->SetOptions(options | FOS_ALLOWMULTISELECT);
                }

                if (!filters.empty())
                {
                    std::vector<COMDLG_FILTERSPEC> filter_specs;
                    std::vector<std::wstring> filter_strings;

                    // Add "All files" filter
                    COMDLG_FILTERSPEC all_spec;
                    all_spec.pszName = L"All files";
                    all_spec.pszSpec = L"*.*";
                    filter_specs.push_back(all_spec);

                    for (const auto &filter : filters)
                    {
                        if (filter == "*" || filter == "*.*") {
                            continue; // Already added
                        }
                        
                        std::wstring wfilter = StringToWString(filter);
                        COMDLG_FILTERSPEC spec;
                        
                        // Set display name
                        std::wstring display_name = L"Files (*." + wfilter + L")";
                        filter_strings.push_back(display_name);
                        spec.pszName = filter_strings.back().c_str();

                        // Set pattern
                        std::wstring pattern = L"*." + wfilter;
                        filter_strings.push_back(pattern);
                        spec.pszSpec = filter_strings.back().c_str();

                        filter_specs.push_back(spec);
                    }

                    if (!filter_specs.empty())
                    {
                        file_dialog->SetFileTypes((UINT)filter_specs.size(), filter_specs.data());
                    }
                }

                hr = file_dialog->Show(NULL);

                if (SUCCEEDED(hr))
                {
                    ComPtr<IShellItemArray> items;
                    hr = file_dialog->GetResults(&items);

                    if (SUCCEEDED(hr))
                    {
                        DWORD count;
                        items->GetCount(&count);

                        for (DWORD i = 0; i < count; i++)
                        {
                            ComPtr<IShellItem> item;
                            if (SUCCEEDED(items->GetItemAt(i, &item)))
                            {
                                PWSTR file_path = nullptr;
                                if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &file_path)))
                                {
                                    std::wstring wpath(file_path);
                                    std::string path = WStringToString(wpath);
                                    selected_files.push_back(path);
                                    CoTaskMemFree(file_path);
                                }
                            }
                        }
                    }
                }

                file_dialog.Reset();
            }

            CoUninitialize();
            return selected_files;
        }

        static std::vector<std::string> ProcessDroppedFiles(HDROP hDrop) {
            std::vector<std::string> files;
            
            if (!hDrop) return files;
            
            UINT file_count = DragQueryFileW(hDrop, 0xFFFFFFFF, NULL, 0);
            
            for (UINT i = 0; i < file_count; i++) {
                WCHAR file_path[MAX_PATH];
                if (DragQueryFileW(hDrop, i, file_path, MAX_PATH)) {
                    // Check if it's a directory
                    DWORD attribs = GetFileAttributesW(file_path);
                    if (attribs != INVALID_FILE_ATTRIBUTES && (attribs & FILE_ATTRIBUTE_DIRECTORY)) {
                        // Skip directories for now
                        continue;
                    }
                    
                    files.push_back(WStringToString(file_path));
                }
            }
            
            DragFinish(hDrop);
            return files;
        }
    };

    std::vector<flutter::EncodableMap> ProcessFileList(const std::vector<std::string>& file_paths) {
        std::vector<flutter::EncodableMap> result;
        
        for (const auto& file_path : file_paths) {
            std::string file_id = FileManager::GenerateFileId(file_path);
            FileManager::CacheFile(file_id, file_path);
            
            std::string extension = GetFileExtension(file_path);
            std::string file_type = GetFileType(extension);
            std::string mime_type = GetMimeType(extension);
            std::string file_name = GetFileName(file_path);
            int64_t file_size = GetFileSize(file_path);
            int64_t modified_time = GetFileModifiedTime(file_path);
            
            flutter::EncodableMap file_info = {
                {flutter::EncodableValue("id"), flutter::EncodableValue(file_id)},
                {flutter::EncodableValue("name"), flutter::EncodableValue(file_name)},
                {flutter::EncodableValue("size"), flutter::EncodableValue(static_cast<int64_t>(file_size))},
                {flutter::EncodableValue("type"), flutter::EncodableValue(file_type)},
                {flutter::EncodableValue("mimeType"), flutter::EncodableValue(mime_type)},
                {flutter::EncodableValue("uri"), flutter::EncodableValue(file_id)},
                {flutter::EncodableValue("path"), flutter::EncodableValue(file_path)},
                {flutter::EncodableValue("dateAdded"), flutter::EncodableValue(modified_time)},
                {flutter::EncodableValue("width"), flutter::EncodableValue(0)},
                {flutter::EncodableValue("height"), flutter::EncodableValue(0)},
                {flutter::EncodableValue("duration"), flutter::EncodableValue(0)},
                {flutter::EncodableValue("albumId"), flutter::EncodableValue("windows")},
                {flutter::EncodableValue("albumName"), flutter::EncodableValue("Windows Files")},
                {flutter::EncodableValue("isWindowsFile"), flutter::EncodableValue(true)}
            };
            
            result.push_back(file_info);
        }
        
        return result;
    }
}

namespace device_media_library
{
    void DeviceMediaLibraryPlugin::RegisterWithRegistrar(
        flutter::PluginRegistrarWindows *registrar)
    {
        auto plugin = std::make_unique<DeviceMediaLibraryPlugin>(registrar);

        auto method_channel =
            std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
                registrar->messenger(), kChannelName,
                &flutter::StandardMethodCodec::GetInstance());

        auto video_method_channel =
            std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
                registrar->messenger(), kVideoChannelName,
                &flutter::StandardMethodCodec::GetInstance());

        method_channel->SetMethodCallHandler(
            [plugin_pointer = plugin.get()](const auto &call, auto result)
            {
                plugin_pointer->HandleMethodCall(call, std::move(result));
            });

        video_method_channel->SetMethodCallHandler(
            [plugin_pointer = plugin.get()](const auto &call, auto result)
            {
                plugin_pointer->HandleVideoMethodCall(call, std::move(result));
            });

        registrar->AddPlugin(std::move(plugin));
    }

    DeviceMediaLibraryPlugin::DeviceMediaLibraryPlugin(flutter::PluginRegistrarWindows *registrar)
        : registrar_(registrar) {}

    DeviceMediaLibraryPlugin::~DeviceMediaLibraryPlugin() {}

    void DeviceMediaLibraryPlugin::HandleMethodCall(
        const flutter::MethodCall<flutter::EncodableValue> &method_call,
        std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result)
    {
        const std::string &method_name = method_call.method_name();

        if (method_name == "getPlatformVersion")
        {
            std::string version = GetWindowsVersion();
            result->Success(flutter::EncodableValue(version));
        }
        else if (method_name == "pickFiles")
        {
            bool multiple = true;
            std::vector<std::string> filters;

            const auto *arguments = std::get_if<flutter::EncodableMap>(method_call.arguments());
            if (arguments)
            {
                auto multiple_it = arguments->find(flutter::EncodableValue("multiple"));
                if (multiple_it != arguments->end())
                {
                    multiple = std::get<bool>(multiple_it->second);
                }

                auto filters_it = arguments->find(flutter::EncodableValue("allowedFileTypes"));
                if (filters_it != arguments->end())
                {
                    const auto *filters_list = std::get_if<flutter::EncodableList>(&filters_it->second);
                    if (filters_list)
                    {
                        for (const auto &filter : *filters_list)
                        {
                            if (const auto *filter_str = std::get_if<std::string>(&filter))
                            {
                                filters.push_back(*filter_str);
                            }
                        }
                    }
                }
            }

            std::vector<std::string> selected_files = FilePicker::PickFiles(multiple, filters);
            auto processed_files = ProcessFileList(selected_files);

            flutter::EncodableList files_list;
            for (const auto& file_info : processed_files) {
                files_list.push_back(flutter::EncodableValue(file_info));
            }

            result->Success(flutter::EncodableValue(files_list));
        }
        else if (method_name == "pickFilesFromDrop")
        {
            const auto *arguments = std::get_if<flutter::EncodableList>(method_call.arguments());
            if (!arguments)
            {
                result->Error("INVALID_ARGUMENTS", "Expected list of file paths");
                return;
            }

            std::vector<std::string> file_paths;
            for (const auto &arg : *arguments)
            {
                if (const auto *file_path = std::get_if<std::string>(&arg))
                {
                    file_paths.push_back(*file_path);
                }
            }

            auto processed_files = ProcessFileList(file_paths);
            
            flutter::EncodableList files_list;
            for (const auto& file_info : processed_files) {
                files_list.push_back(flutter::EncodableValue(file_info));
            }

            result->Success(flutter::EncodableValue(files_list));
        }
        else if (method_name == "readWindowsFile")
        {
            const auto *arguments = std::get_if<flutter::EncodableMap>(method_call.arguments());
            if (!arguments)
            {
                result->Error("INVALID_ARGUMENTS", "Expected file path");
                return;
            }

            auto file_path_it = arguments->find(flutter::EncodableValue("filePath"));
            if (file_path_it == arguments->end())
            {
                result->Error("INVALID_ARGUMENTS", "filePath parameter is required");
                return;
            }

            const auto *file_path = std::get_if<std::string>(&file_path_it->second);
            if (!file_path)
            {
                result->Error("INVALID_ARGUMENTS", "filePath must be a string");
                return;
            }

            std::vector<uint8_t> file_bytes = ReadFileBytes(*file_path);
            if (file_bytes.empty())
            {
                result->Error("FILE_READ_ERROR", "Failed to read file");
                return;
            }

            result->Success(flutter::EncodableValue(file_bytes));
        }
        else if (method_name == "readWebFile")
        {
            const auto *arguments = std::get_if<flutter::EncodableMap>(method_call.arguments());
            if (!arguments)
            {
                result->Error("INVALID_ARGUMENTS", "Expected file id");
                return;
            }

            auto file_id_it = arguments->find(flutter::EncodableValue("fileId"));
            if (file_id_it == arguments->end())
            {
                result->Error("INVALID_ARGUMENTS", "fileId parameter is required");
                return;
            }

            const auto *file_id = std::get_if<std::string>(&file_id_it->second);
            if (!file_id)
            {
                result->Error("INVALID_ARGUMENTS", "fileId must be a string");
                return;
            }

            std::string file_path = FileManager::GetCachedFilePath(*file_id);
            if (file_path.empty())
            {
                result->Error("FILE_NOT_FOUND", "File not found in cache");
                return;
            }

            std::vector<uint8_t> file_bytes = ReadFileBytes(file_path);
            if (file_bytes.empty())
            {
                result->Error("FILE_READ_ERROR", "Failed to read file");
                return;
            }

            result->Success(flutter::EncodableValue(file_bytes));
        }
        else if (method_name == "getWebFileUrl")
        {
            const auto *arguments = std::get_if<flutter::EncodableMap>(method_call.arguments());
            if (!arguments)
            {
                result->Error("INVALID_ARGUMENTS", "Expected file id");
                return;
            }

            auto file_id_it = arguments->find(flutter::EncodableValue("fileId"));
            if (file_id_it == arguments->end())
            {
                result->Error("INVALID_ARGUMENTS", "fileId parameter is required");
                return;
            }

            const auto *file_id = std::get_if<std::string>(&file_id_it->second);
            if (!file_id)
            {
                result->Error("INVALID_ARGUMENTS", "fileId must be a string");
                return;
            }

            std::string file_path = FileManager::GetCachedFilePath(*file_id);
            if (file_path.empty())
            {
                result->Error("FILE_NOT_FOUND", "File not found in cache");
                return;
            }

            std::string file_url = GetFileUrlForWindows(file_path);
            result->Success(flutter::EncodableValue(file_url));
        }
        else if (method_name == "disposeWebFile")
        {
            const auto *arguments = std::get_if<flutter::EncodableMap>(method_call.arguments());
            if (!arguments)
            {
                result->Error("INVALID_ARGUMENTS", "Expected file id");
                return;
            }

            auto file_id_it = arguments->find(flutter::EncodableValue("fileId"));
            if (file_id_it == arguments->end())
            {
                result->Error("INVALID_ARGUMENTS", "fileId parameter is required");
                return;
            }

            const auto *file_id = std::get_if<std::string>(&file_id_it->second);
            if (!file_id)
            {
                result->Error("INVALID_ARGUMENTS", "fileId must be a string");
                return;
            }

            FileManager::RemoveFile(*file_id);
            result->Success();
        }
        else if (method_name == "checkPermission")
        {
            result->Success(flutter::EncodableValue(true));
        }
        else if (method_name == "requestPermission")
        {
            result->Success(flutter::EncodableValue(true));
        }
        else if (method_name == "getImages" ||
                 method_name == "getVideos" ||
                 method_name == "getAllMedia" ||
                 method_name == "getAlbums" ||
                 method_name == "getMediaFromAlbum" ||
                 method_name == "getThumbnail" ||
                 method_name == "readFileByUri")
        {
            result->Success(flutter::EncodableValue(flutter::EncodableList()));
        }
        else
        {
            result->NotImplemented();
        }
    }

    void DeviceMediaLibraryPlugin::HandleVideoMethodCall(
        const flutter::MethodCall<flutter::EncodableValue> &method_call,
        std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result)
    {
        result->NotImplemented();
    }
}