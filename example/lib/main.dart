import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:device_media_library/device_media_library.dart';

void main() {
  runApp(MyApp());
}

enum MediaType { all, images, videos }

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final DeviceMediaLibrary _mediaLibrary = DeviceMediaLibrary();
  final List<Map<String, dynamic>> _mediaList = [];
  final Map<String, Uint8List> _thumbnailsCache = {};
  List<Map<String, dynamic>>? _albums;
  bool _hasPermission = false;
  bool _isLoading = true;
  bool _isLoadingMore = false;
  bool _isRequestingPermission = false;
  MediaType _selectedMediaType = MediaType.all;
  String? _selectedAlbumId;
  String? _selectedAlbumName;

  int _currentOffset = 0;
  static const int _pageSize = 30;
  bool _hasMoreItems = true;

  final ScrollController _scrollController = ScrollController();
  final Map<String, Completer<Uint8List?>> _thumbnailCompleters = {};

  @override
  void initState() {
    super.initState();
    _checkPermissionAndLoadData();
    _setupScrollListener();
  }

  void _setupScrollListener() {
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 200) {
        _loadMoreMedia();
      }
    });
  }

  Future<void> _checkPermissionAndLoadData() async {
    try {
      bool hasPermission = await _mediaLibrary.checkPermission();

      if (!hasPermission && !_isRequestingPermission) {
        setState(() {
          _isRequestingPermission = true;
        });

        hasPermission = await _mediaLibrary.requestPermission();

        setState(() {
          _isRequestingPermission = false;
        });
      }

      setState(() {
        _hasPermission = hasPermission;
      });

      if (hasPermission) {
        await _loadAlbums();
        await _loadMedia(reset: true);
      }
    } catch (e) {
      debugPrint('Error checking permission: $e');
      setState(() {
        _isRequestingPermission = false;
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _loadAlbums() async {
    try {
      final albums = await _mediaLibrary.getAlbums();
      setState(() {
        _albums = albums;
      });
    } catch (e) {
      debugPrint('Error loading albums: $e');
    }
  }

  Future<void> _loadMedia({bool reset = false}) async {
    if (reset) {
      setState(() {
        _currentOffset = 0;
        _mediaList.clear();
        _thumbnailsCache.clear();
        _thumbnailCompleters.clear();
        _hasMoreItems = true;
        _isLoading = true;
      });
    } else if (_isLoadingMore) {
      return;
    }

    setState(() {
      if (reset) {
        _isLoading = true;
      } else {
        _isLoadingMore = true;
      }
    });

    try {
      List<Map<String, dynamic>>? newMedia;

      switch (_selectedMediaType) {
        case MediaType.images:
          newMedia = await _mediaLibrary.getImages(
            limit: _pageSize,
            offset: _currentOffset,
            albumId: _selectedAlbumId,
          );
          break;
        case MediaType.videos:
          newMedia = await _mediaLibrary.getVideos(
            limit: _pageSize,
            offset: _currentOffset,
            albumId: _selectedAlbumId,
          );
          break;
        case MediaType.all:
          newMedia = await _mediaLibrary.getAllMedia(
            limit: _pageSize,
            offset: _currentOffset,
            albumId: _selectedAlbumId,
          );
          break;
      }

      if (newMedia != null && newMedia.isNotEmpty) {
        setState(() {
          _mediaList.addAll(newMedia!);
          _currentOffset += newMedia.length;
          _hasMoreItems = newMedia.length == _pageSize;
        });
        _preloadThumbnails(newMedia);
      } else {
        setState(() {
          _hasMoreItems = false;
        });
      }
    } catch (e) {
      debugPrint('Error loading media: $e');
    } finally {
      setState(() {
        _isLoading = false;
        _isLoadingMore = false;
      });
    }
  }

  Future<void> _loadMoreMedia() async {
    if (_hasMoreItems && !_isLoadingMore && !_isLoading) {
      await _loadMedia(reset: false);
    }
  }

  void _preloadThumbnails(List<Map<String, dynamic>> mediaList) {
    for (final media in mediaList) {
      final mediaId = _getString(media, 'id');
      final mediaType = _getString(media, 'type');

      if (!_thumbnailsCache.containsKey(mediaId) &&
          !_thumbnailCompleters.containsKey(mediaId)) {
        _loadThumbnail(mediaId, mediaType);
      }
    }
  }

  void _loadThumbnail(String mediaId, String mediaType) {
    if (_thumbnailCompleters.containsKey(mediaId)) {
      return;
    }

    final completer = Completer<Uint8List?>();
    _thumbnailCompleters[mediaId] = completer;

    _mediaLibrary
        .getThumbnail(
          mediaId: mediaId,
          mediaType: mediaType,
          width: 200,
          height: 200,
        )
        .then((thumbnail) {
          if (thumbnail != null) {
            _thumbnailsCache[mediaId] = thumbnail;
          }
          completer.complete(thumbnail);
          _thumbnailCompleters.remove(mediaId);
        })
        .catchError((error) {
          completer.complete(null);
          _thumbnailCompleters.remove(mediaId);
        });
  }

  Future<Uint8List?> _getThumbnailFuture(
    String mediaId,
    String mediaType,
  ) async {
    if (_thumbnailsCache.containsKey(mediaId)) {
      return _thumbnailsCache[mediaId];
    }

    if (_thumbnailCompleters.containsKey(mediaId)) {
      return await _thumbnailCompleters[mediaId]!.future;
    }

    _loadThumbnail(mediaId, mediaType);
    return await _thumbnailCompleters[mediaId]?.future;
  }

  void _onMediaTypeChanged(MediaType type) {
    setState(() {
      _selectedMediaType = type;
    });
    _loadMedia(reset: true);
  }

  void _onAlbumChanged(String? albumId, String? albumName) {
    setState(() {
      _selectedAlbumId = albumId;
      _selectedAlbumName = albumName;
    });
    _loadMedia(reset: true);
  }

  String _getString(Map<String, dynamic> map, String key) {
    return map[key]?.toString() ?? '';
  }

  int _getInt(Map<String, dynamic> map, String key) {
    final value = map[key];
    if (value == null) return 0;
    if (value is int) return value;
    if (value is double) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  String _formatDuration(int milliseconds) {
    if (milliseconds == 0) return '';

    final duration = Duration(milliseconds: milliseconds);
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds.remainder(60);

    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  Widget _buildThumbnail(
    String mediaId,
    String mediaType,
    int width,
    int height,
  ) {
    return FutureBuilder<Uint8List?>(
      future: _getThumbnailFuture(mediaId, mediaType),
      builder: (context, snapshot) {
        if (snapshot.hasData && snapshot.data != null) {
          return Image.memory(
            snapshot.data!,
            fit: BoxFit.cover,
            width: width.toDouble(),
            height: height.toDouble(),
            filterQuality: FilterQuality.medium,
            cacheWidth: width,
            cacheHeight: height,
          );
        }

        return Container(
          color: Colors.grey[300],
          width: width.toDouble(),
          height: height.toDouble(),
          child: Icon(
            mediaType == 'video' ? Icons.video_library : Icons.photo,
            color: Colors.grey[600],
            size: 30,
          ),
        );
      },
    );
  }

  // Новая функция для вывода пути файла
  void _printFilePath(Map<String, dynamic> media) {
    final mediaId = _getString(media, 'id');
    final mediaType = _getString(media, 'type');
    final filePath = _getString(media, 'filePath');
    final uri = _getString(media, 'uri');

    // Пробуем получить путь из разных возможных полей
    String path = filePath.isNotEmpty ? filePath : uri;

    if (path.isEmpty) {
      path = _getString(media, 'path');
    }

    debugPrint('=== File Information ===');
    debugPrint('Media ID: $mediaId');
    debugPrint('Media Type: $mediaType');
    debugPrint('File Path: $path');
    debugPrint('========================');

    // Также выводим в консоль для удобства копирования
    print('FILE_PATH: $path');
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: Text(_selectedAlbumName ?? 'Device Media Library'),
          actions: [
            if (_albums != null && _albums!.isNotEmpty)
              PopupMenuButton<String>(
                onSelected: (value) {
                  if (value == 'all') {
                    _onAlbumChanged(null, null);
                  } else {
                    final album = _albums!.firstWhere(
                      (album) => _getString(album, 'id') == value,
                    );
                    _onAlbumChanged(
                      _getString(album, 'id'),
                      _getString(album, 'name'),
                    );
                  }
                },
                itemBuilder: (context) => [
                  const PopupMenuItem(value: 'all', child: Text('Все альбомы')),
                  ..._albums!.map((album) {
                    return PopupMenuItem(
                      value: _getString(album, 'id'),
                      child: Text(_getString(album, 'name')),
                    );
                  }),
                ],
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      Icon(Icons.photo_album),
                      SizedBox(width: 4),
                      Text('Альбомы'),
                    ],
                  ),
                ),
              ),
          ],
        ),
        body: _isLoading && _mediaList.isEmpty
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const CircularProgressIndicator(),
                    if (_isRequestingPermission) ...[
                      const SizedBox(height: 20),
                      const Text('Requesting permission...'),
                    ],
                  ],
                ),
              )
            : !_hasPermission
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(
                      Icons.error_outline,
                      size: 64,
                      color: Colors.red,
                    ),
                    const SizedBox(height: 20),
                    const Text(
                      'Permission Required',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'This app needs access to your photos to display them.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 16),
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton(
                      onPressed: _checkPermissionAndLoadData,
                      child: const Text('Grant Permission'),
                    ),
                  ],
                ),
              )
            : Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Row(
                      children: [
                        Expanded(
                          child: SegmentedButton<MediaType>(
                            segments: const [
                              ButtonSegment<MediaType>(
                                value: MediaType.all,
                                label: Text('Все'),
                                icon: Icon(Icons.all_inclusive),
                              ),
                              ButtonSegment<MediaType>(
                                value: MediaType.images,
                                label: Text('Фото'),
                                icon: Icon(Icons.photo),
                              ),
                              ButtonSegment<MediaType>(
                                value: MediaType.videos,
                                label: Text('Видео'),
                                icon: Icon(Icons.video_library),
                              ),
                            ],
                            selected: {_selectedMediaType},
                            onSelectionChanged: (Set<MediaType> newSelection) {
                              _onMediaTypeChanged(newSelection.first);
                            },
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_selectedAlbumName != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.photo_album, size: 16),
                          const SizedBox(width: 4),
                          Text(
                            'Альбом: $_selectedAlbumName',
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey[600],
                            ),
                          ),
                          const Spacer(),
                          TextButton(
                            onPressed: () => _onAlbumChanged(null, null),
                            child: const Text('Показать все'),
                          ),
                        ],
                      ),
                    ),
                  Expanded(
                    child: _mediaList.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(
                                  Icons.photo_library,
                                  size: 64,
                                  color: Colors.grey,
                                ),
                                const SizedBox(height: 20),
                                const Text('No media found'),
                              ],
                            ),
                          )
                        : NotificationListener<ScrollNotification>(
                            onNotification: (scrollNotification) {
                              if (scrollNotification is ScrollEndNotification) {
                                final metrics = scrollNotification.metrics;
                                if (metrics.atEdge && metrics.pixels != 0) {
                                  _loadMoreMedia();
                                }
                              }
                              return false;
                            },
                            child: GridView.builder(
                              controller: _scrollController,
                              gridDelegate:
                                  const SliverGridDelegateWithFixedCrossAxisCount(
                                    crossAxisCount: 3,
                                    crossAxisSpacing: 2.0,
                                    mainAxisSpacing: 2.0,
                                  ),
                              itemCount:
                                  _mediaList.length + (_hasMoreItems ? 1 : 0),
                              itemBuilder: (context, index) {
                                if (index == _mediaList.length) {
                                  return Center(
                                    child: Padding(
                                      padding: const EdgeInsets.all(16.0),
                                      child: _isLoadingMore
                                          ? const CircularProgressIndicator()
                                          : const Text('No more items'),
                                    ),
                                  );
                                }

                                final media = _mediaList[index];
                                final mediaId = _getString(media, 'id');
                                final mediaType = _getString(media, 'type');
                                final duration = _getInt(media, 'duration');

                                return GestureDetector(
                                  onTap: () {
                                    _printFilePath(media);
                                  },
                                  child: Stack(
                                    children: [
                                      _buildThumbnail(
                                        mediaId,
                                        mediaType,
                                        200,
                                        200,
                                      ),
                                      if (mediaType == 'video')
                                        Positioned(
                                          bottom: 4,
                                          right: 4,
                                          child: Container(
                                            padding: const EdgeInsets.symmetric(
                                              horizontal: 4,
                                              vertical: 2,
                                            ),
                                            decoration: BoxDecoration(
                                              color: Colors.black54,
                                              borderRadius:
                                                  BorderRadius.circular(4),
                                            ),
                                            child: Row(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                const Icon(
                                                  Icons.play_arrow,
                                                  size: 12,
                                                  color: Colors.white,
                                                ),
                                                const SizedBox(width: 2),
                                                Text(
                                                  _formatDuration(duration),
                                                  style: const TextStyle(
                                                    color: Colors.white,
                                                    fontSize: 10,
                                                    fontWeight: FontWeight.bold,
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ),
                                      Positioned(
                                        top: 4,
                                        left: 4,
                                        child: Container(
                                          padding: const EdgeInsets.all(4),
                                          decoration: const BoxDecoration(
                                            color: Colors.black54,
                                            shape: BoxShape.circle,
                                          ),
                                          child: Icon(
                                            mediaType == 'video'
                                                ? Icons.videocam
                                                : Icons.photo,
                                            size: 12,
                                            color: Colors.white,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                );
                              },
                            ),
                          ),
                  ),
                ],
              ),
      ),
    );
  }
}
