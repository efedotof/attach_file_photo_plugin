import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:device_media_library/device_media_library.dart';

// Для web используем dart:html
import 'dart:html' as html;

void main() {
  runApp(const MyApp());
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
  bool _isDragging = false;

  int _dragCounter = 0;

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

    if (kIsWeb) {
      _setupWebDrop();
    }
  }

  void _setupWebDrop() {
    // Используем dynamic для избежания проблем с типами
    html.document.addEventListener('dragover', (event) {
      final e = event;
      e.preventDefault();

      // Используем as dynamic для доступа к свойствам DragEvent
      final dynamic dragEvent = e;
      if (dragEvent.dataTransfer != null) {
        dragEvent.dataTransfer.dropEffect = 'copy';
      }
    });

    html.document.addEventListener('dragenter', (event) {
      event.preventDefault();
      _dragCounter++;
      if (_dragCounter == 1) {
        setState(() {
          _isDragging = true;
        });
      }
    });

    html.document.addEventListener('dragleave', (event) {
      event.preventDefault();
      _dragCounter--;
      if (_dragCounter == 0) {
        setState(() {
          _isDragging = false;
        });
      }
    });

    html.document.addEventListener('drop', (event) async {
      event.preventDefault();
      _dragCounter = 0;
      setState(() {
        _isDragging = false;
      });

      final dynamic dragEvent = event;
      final files = dragEvent.dataTransfer?.files;

      if (files == null || files.isEmpty) return;

      debugPrint('WEB DROP: ${files.length} files');

      try {
        final fileList = <dynamic>[];
        for (var i = 0; i < files.length; i++) {
          fileList.add(files[i]);
        }

        final result = await _mediaLibrary.pickFilesFromDrop(fileList);

        if (result.isNotEmpty) {
          setState(() {
            _mediaList.addAll(result);
          });
          _preloadThumbnails(result);
        }
      } catch (e) {
        debugPrint('Error processing dropped files: $e');
      }
    }, true);

    html.document.body?.style.overflow = 'hidden';

    final style = html.document.createElement('style') as html.StyleElement;
    style.text = '''
      * {
        -webkit-user-drag: none;
        user-drag: none;
      }
      .drop-zone {
        pointer-events: none;
      }
    ''';
    html.document.head?.append(style);
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
      bool hasPermission = kIsWeb
          ? true
          : await _mediaLibrary.checkPermission();

      if (!hasPermission && !_isRequestingPermission && !kIsWeb) {
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
        if (!kIsWeb) {
          await _loadAlbums();
          await _loadMedia(reset: true);
        } else {
          setState(() {
            _isLoading = false;
          });
        }
      }
    } catch (e) {
      debugPrint('Error checking permission: $e');
    } finally {
      if (!kIsWeb) {
        setState(() {
          _isLoading = false;
        });
      }
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
    if (_isLoading || _isLoadingMore) return;

    if (reset) {
      setState(() {
        _currentOffset = 0;
        _mediaList.clear();
        _thumbnailsCache.clear();
        _thumbnailCompleters.clear();
        _hasMoreItems = true;
      });
    }

    setState(() {
      _isLoading = true;
    });

    try {
      if (kIsWeb) {
        debugPrint('WEB: opening file picker...');

        final newMedia = await _mediaLibrary.pickFiles(multiple: true);

        debugPrint('WEB: picked ${newMedia?.length ?? 0} files');

        if (newMedia != null && newMedia.isNotEmpty) {
          for (final file in newMedia) {
            debugPrint(
              'WEB FILE -> name=${file['name']} '
              'size=${file['size']} '
              'type=${file['type']} '
              'mime=${file['mimeType']} '
              'id=${file['id']}',
            );
          }

          setState(() {
            _mediaList.addAll(newMedia);
            _hasMoreItems = false;
          });

          _preloadThumbnails(newMedia);
        }
      } else {
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
          _hasMoreItems = false;
        }
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
    if (_hasMoreItems && !_isLoadingMore && !_isLoading && !kIsWeb) {
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
    if (kIsWeb) {
      _loadMedia(reset: true);
    } else {
      setState(() {
        _selectedMediaType = type;
      });
      _loadMedia(reset: true);
    }
  }

  void _onAlbumChanged(String? albumId, String? albumName) {
    if (!kIsWeb) {
      setState(() {
        _selectedAlbumId = albumId;
        _selectedAlbumName = albumName;
      });
      _loadMedia(reset: true);
    }
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

  void _printFilePath(Map<String, dynamic> media) {
    final mediaId = _getString(media, 'id');
    final mediaType = _getString(media, 'type');
    final filePath = _getString(media, 'filePath');
    final uri = _getString(media, 'uri');

    String path = filePath.isNotEmpty ? filePath : uri;

    if (path.isEmpty) {
      path = _getString(media, 'path');
    }

    debugPrint('=== File Information ===');
    debugPrint('Media ID: $mediaId');
    debugPrint('Media Type: $mediaType');
    debugPrint('File Path: $path');
    debugPrint('========================');

    debugPrint('FILE_PATH: $path');
  }

  Future<void> _pickFilesOnWeb() async {
    if (_isLoading) return;
    await _loadMedia(reset: false);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  Widget _buildBodyContent() {
    return _isLoading && !kIsWeb && _mediaList.isEmpty
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
        : !_hasPermission && !kIsWeb
        ? Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 64, color: Colors.red),
                const SizedBox(height: 20),
                const Text(
                  'Permission Required',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
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
        : _buildGrid();
  }

  Widget _buildGrid() {
    return Column(
      children: [
        if (!kIsWeb)
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
        if (!kIsWeb && _selectedAlbumName != null)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                const Icon(Icons.photo_album, size: 16),
                const SizedBox(width: 4),
                Text(
                  'Альбом: $_selectedAlbumName',
                  style: TextStyle(fontSize: 14, color: Colors.grey[600]),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => _onAlbumChanged(null, null),
                  child: const Text('Показать все'),
                ),
              ],
            ),
          ),
        if (kIsWeb && _mediaList.isEmpty)
          Expanded(
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.cloud_upload, size: 64, color: Colors.grey),
                  const SizedBox(height: 20),
                  const Text(
                    'Drop files here or click the button',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'Drag and drop files anywhere on the page',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton(
                    onPressed: _pickFilesOnWeb,
                    child: const Text('Select Files'),
                  ),
                ],
              ),
            ),
          )
        else if (_mediaList.isEmpty)
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.photo_library, size: 64, color: Colors.grey),
                const SizedBox(height: 20),
                const Text('No media found'),
              ],
            ),
          )
        else
          Expanded(
            child: NotificationListener<ScrollNotification>(
              onNotification: (scrollNotification) {
                if (!kIsWeb && scrollNotification is ScrollEndNotification) {
                  final metrics = scrollNotification.metrics;
                  if (metrics.atEdge && metrics.pixels != 0) {
                    _loadMoreMedia();
                  }
                }
                return false;
              },
              child: GridView.builder(
                controller: kIsWeb ? null : _scrollController,
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 3,
                  crossAxisSpacing: 2.0,
                  mainAxisSpacing: 2.0,
                ),
                itemCount:
                    _mediaList.length + (!kIsWeb && _hasMoreItems ? 1 : 0),
                itemBuilder: (context, index) {
                  if (!kIsWeb && index == _mediaList.length) {
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
                  final fileName = _getString(media, 'name');

                  return GestureDetector(
                    onTap: () {
                      _printFilePath(media);
                      if (kIsWeb) {
                        showDialog(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: Text(fileName),
                            content: SingleChildScrollView(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text('Type: $mediaType'),
                                  Text('Size: ${_getInt(media, 'size')} bytes'),
                                  if (duration > 0)
                                    Text(
                                      'Duration: ${_formatDuration(duration)}',
                                    ),
                                  const SizedBox(height: 20),
                                  SizedBox(
                                    height: 300,
                                    child: _buildThumbnail(
                                      mediaId,
                                      mediaType,
                                      300,
                                      300,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(context),
                                child: const Text('Close'),
                              ),
                            ],
                          ),
                        );
                      }
                    },
                    onLongPress: () {
                      if (kIsWeb) {
                        showDialog(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: const Text('File Options'),
                            content: const Text(
                              'What would you like to do with this file?',
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(context),
                                child: const Text('Cancel'),
                              ),
                              TextButton(
                                onPressed: () {
                                  Navigator.pop(context);
                                  _printFilePath(media);
                                },
                                child: const Text('View Details'),
                              ),
                            ],
                          ),
                        );
                      }
                    },
                    child: Stack(
                      children: [
                        _buildThumbnail(mediaId, mediaType, 200, 200),
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
                                borderRadius: BorderRadius.circular(4),
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
                        if (kIsWeb)
                          Positioned(
                            bottom: 4,
                            left: 4,
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 4,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: Colors.black54,
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                fileName.length > 10
                                    ? '${fileName.substring(0, 10)}...'
                                    : fileName,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 8,
                                  fontWeight: FontWeight.bold,
                                ),
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
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: Text(
            kIsWeb
                ? 'Web Media Library'
                : (_selectedAlbumName ?? 'Device Media Library'),
          ),
          actions: [
            if (!kIsWeb && _albums != null && _albums!.isNotEmpty)
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
            if (kIsWeb)
              IconButton(
                icon: const Icon(Icons.add_photo_alternate),
                onPressed: _pickFilesOnWeb,
                tooltip: 'Pick files',
              ),
          ],
        ),
        body: Stack(
          children: [
            _buildBodyContent(),
            if (kIsWeb && _isDragging)
              Positioned.fill(
                child: Container(
                  color: Color.fromRGBO(0, 0, 0, 0.7),
                  child: const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.cloud_upload, size: 72, color: Colors.white),
                        SizedBox(height: 20),
                        Text(
                          'Drop files here',
                          style: TextStyle(
                            fontSize: 32,
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        SizedBox(height: 10),
                        Text(
                          'Release to upload files',
                          style: TextStyle(fontSize: 16, color: Colors.white70),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
        floatingActionButton: kIsWeb && _mediaList.isNotEmpty
            ? FloatingActionButton(
                onPressed: _pickFilesOnWeb,
                tooltip: 'Add more files',
                child: const Icon(Icons.add),
              )
            : null,
      ),
    );
  }
}
