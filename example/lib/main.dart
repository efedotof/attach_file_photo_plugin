import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart'
    show Clipboard, ClipboardData, PlatformException;
import 'package:device_media_library/device_media_library.dart';

void main() {
  DeviceMediaLibrary.initialize();
  runApp(const MyApp());
}

enum LibrarySection { media, audio, downloads, documents }

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: MediaLibraryScreen(),
    );
  }
}

class MediaLibraryScreen extends StatefulWidget {
  const MediaLibraryScreen({super.key});

  @override
  State<MediaLibraryScreen> createState() => _MediaLibraryScreenState();
}

class _MediaLibraryScreenState extends State<MediaLibraryScreen>
    with WidgetsBindingObserver {
  final DeviceMediaLibrary _lib = DeviceMediaLibrary();

  LibrarySection _section = LibrarySection.media;

  bool _hasBasicPermission = false;
  bool _hasAllFilesPermission = false;

  Map<String, dynamic>? _storageSupport;

  final List<Map<String, dynamic>> _mediaList = [];
  final List<Map<String, dynamic>> _audioList = [];
  final List<Map<String, dynamic>> _downloadsList = [];
  final List<Map<String, dynamic>> _documentsList = [];

  final Map<String, Uint8List> _thumbs = {};
  final Map<String, Completer<Uint8List?>> _thumbCompleters = {};

  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _bootstrap();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !kIsWeb) {
      _refreshAllFilesPermission();
    }
  }

  Future<void> _refreshAllFilesPermission() async {
    final has = await _lib.checkAllFilesPermission();
    if (has != _hasAllFilesPermission) {
      if (!mounted) return;
      setState(() => _hasAllFilesPermission = has);
      if (has &&
          (_section == LibrarySection.downloads ||
              _section == LibrarySection.documents)) {
        await _refreshStorageSupport();
        await _loadCurrentSection();
      }
    }
  }

  Future<void> _refreshStorageSupport() async {
    if (kIsWeb) return;
    final folder = _folderForSection(_section);
    if (folder == null) {
      if (!mounted) return;
      setState(() => _storageSupport = null);
      return;
    }
    try {
      final support = await _lib.checkStorageAccess(folder: folder);
      if (!mounted) return;
      setState(() => _storageSupport = support);
    } catch (e) {
      debugPrint('checkStorageAccess error: $e');
    }
  }

  String? _folderForSection(LibrarySection s) {
    switch (s) {
      case LibrarySection.downloads:
        return 'download';
      case LibrarySection.documents:
        return 'document';
      default:
        return null;
    }
  }

  Future<void> _bootstrap() async {
    if (kIsWeb) {
      setState(() {
        _hasBasicPermission = true;
        _hasAllFilesPermission = true;
        _storageSupport = {
          'supported': false,
          'accessMode': 'none',
          'hasAccess': false,
          'requiresSaf': false,
          'requiresManage': false,
          'reason': 'Web',
        };
      });
      return;
    }

    bool hasBasic = await _lib.checkPermission();
    if (!hasBasic) {
      hasBasic = await _lib.requestPermission();
    }

    final bool hasAll = await _lib.checkAllFilesPermission();

    if (!mounted) return;
    setState(() {
      _hasBasicPermission = hasBasic;
      _hasAllFilesPermission = hasAll;
    });

    await _refreshStorageSupport();

    if (hasBasic) {
      await _loadCurrentSection();
    }
  }

  Future<void> _requestAllFilesAccess() async {
    if (kIsWeb) return;
    final ok = await _lib.requestAllFilesPermission();
    debugPrint('requestAllFilesPermission returned: $ok');
  }

  Future<void> _requestDownloadsFolderAccess() async {
    if (_isLoading) return;
    setState(() => _isLoading = true);
    try {
      final granted = await _lib.requestDownloadsFolderAccess();
      try {
        final support = await _lib.checkStorageAccess(folder: 'download');
        if (!mounted) return;
        setState(() => _storageSupport = support);
      } catch (e) {
        debugPrint('checkStorageAccess error: $e');
      }
      if (granted) {
        await _loadCurrentSection();
      } else {
        _showInfo('Доступ к папке Download не выдан');
      }
    } catch (e) {
      if (mounted) _showError('Не удалось открыть проводник: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _requestDocumentsFolderAccess() async {
    if (_isLoading) return;
    setState(() => _isLoading = true);
    try {
      final granted = await _lib.requestDocumentsFolderAccess();
      try {
        final support = await _lib.checkStorageAccess(folder: 'document');
        if (!mounted) return;
        setState(() => _storageSupport = support);
      } catch (e) {
        debugPrint('checkStorageAccess error: $e');
      }
      if (granted) {
        await _loadCurrentSection();
      } else {
        _showInfo('Доступ к папке Documents не выдан');
      }
    } catch (e) {
      if (mounted) _showError('Не удалось открыть проводник: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _loadCurrentSection() async {
    if (_isLoading) return;
    setState(() => _isLoading = true);

    try {
      switch (_section) {
        case LibrarySection.media:
          final items = await _lib.getAllMedia(limit: 200, offset: 0) ?? [];
          if (!mounted) return;
          setState(() {
            _mediaList
              ..clear()
              ..addAll(items);
          });
          _preloadThumbs(items);
          break;

        case LibrarySection.audio:
          final items = await _lib.getAudio(limit: 200, offset: 0) ?? [];
          if (!mounted) return;
          setState(() {
            _audioList
              ..clear()
              ..addAll(items);
          });
          break;

        case LibrarySection.downloads:
          final mode = _storageSupport?['accessMode']?.toString() ?? 'none';
          final requiresSaf = _storageSupport?['requiresSaf'] == true;
          if (mode == 'saf' && requiresSaf) {
            if (!mounted) return;
            setState(() => _downloadsList.clear());
            break;
          }
          try {
            final items = await _lib.getDownloads(limit: 500, offset: 0) ?? [];
            if (!mounted) return;
            setState(() {
              _downloadsList
                ..clear()
                ..addAll(items);
            });
          } on PlatformException catch (e) {
            debugPrint('getDownloads: ${e.code} ${e.message}');
            if (!mounted) return;
            if (e.code == 'SAF_REQUIRED' || e.code == 'PERMISSION_REQUIRED') {
              try {
                final support = await _lib.checkStorageAccess(
                  folder: 'download',
                );
                if (!mounted) return;
                setState(() => _storageSupport = support);
              } catch (_) {}
            } else {
              _showError('Не удалось загрузить Download: ${e.message}');
            }
          }
          break;

        case LibrarySection.documents:
          final mode = _storageSupport?['accessMode']?.toString() ?? 'none';
          final requiresSaf = _storageSupport?['requiresSaf'] == true;
          if (mode == 'saf' && requiresSaf) {
            if (!mounted) return;
            setState(() => _documentsList.clear());
            break;
          }
          try {
            final items = await _lib.getDocuments(limit: 500, offset: 0) ?? [];
            if (!mounted) return;
            setState(() {
              _documentsList
                ..clear()
                ..addAll(items);
            });
          } on PlatformException catch (e) {
            debugPrint('getDocuments: ${e.code} ${e.message}');
            if (!mounted) return;
            if (e.code == 'SAF_REQUIRED' || e.code == 'PERMISSION_REQUIRED') {
              try {
                final support = await _lib.checkStorageAccess(
                  folder: 'document',
                );
                if (!mounted) return;
                setState(() => _storageSupport = support);
              } catch (_) {}
            } else {
              _showError('Не удалось загрузить Documents: ${e.message}');
            }
          }
          break;
      }
    } catch (e) {
      debugPrint('Load error: $e');
      if (mounted) _showError('Ошибка загрузки: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showError(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  void _showInfo(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _onSectionChanged(LibrarySection s) async {
    setState(() => _section = s);
    if (!kIsWeb &&
        (s == LibrarySection.downloads || s == LibrarySection.documents)) {
      await _refreshStorageSupport();
    }
    await _loadCurrentSection();
  }

  void _preloadThumbs(List<Map<String, dynamic>> items) {
    for (final m in items) {
      final id = _str(m, 'id');
      final type = _str(m, 'type');
      if (!_thumbs.containsKey(id) && !_thumbCompleters.containsKey(id)) {
        _loadThumb(id, type);
      }
    }
  }

  void _loadThumb(String id, String type) {
    if (_thumbCompleters.containsKey(id)) return;
    final c = Completer<Uint8List?>();
    _thumbCompleters[id] = c;

    _lib
        .getThumbnail(mediaId: id, mediaType: type, width: 200, height: 200)
        .then((bytes) {
          if (bytes != null) _thumbs[id] = bytes;
          c.complete(bytes);
        })
        .catchError((_) {
          c.complete(null);
        })
        .whenComplete(() {
          _thumbCompleters.remove(id);
        });
  }

  Future<Uint8List?> _thumbFuture(String id, String type) async {
    if (_thumbs.containsKey(id)) return _thumbs[id];
    if (_thumbCompleters.containsKey(id)) {
      return _thumbCompleters[id]!.future;
    }
    _loadThumb(id, type);
    return _thumbCompleters[id]?.future;
  }

  String _str(Map<String, dynamic> m, String k) => m[k]?.toString() ?? '';

  int _int(Map<String, dynamic> m, String k) {
    final v = m[k];
    if (v is int) return v;
    if (v is double) return v.toInt();
    if (v is String) return int.tryParse(v) ?? 0;
    return 0;
  }

  String _fmtBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    }
    return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(2)} GB';
  }

  String _fmtDuration(int ms) {
    if (ms <= 0) return '';
    final d = Duration(milliseconds: ms);
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    if (d.inHours > 0) {
      return '${d.inHours}:$m:$s';
    }
    return '$m:$s';
  }

  IconData _iconForType(String type) {
    switch (type) {
      case 'image':
        return Icons.image;
      case 'video':
        return Icons.videocam;
      case 'audio':
        return Icons.music_note;
      default:
        return Icons.insert_drive_file;
    }
  }

  Color _colorForType(String type) {
    switch (type) {
      case 'image':
        return Colors.green;
      case 'video':
        return Colors.red;
      case 'audio':
        return Colors.purple;
      default:
        return Colors.blueGrey;
    }
  }

  Widget _buildMediaGrid() {
    if (_mediaList.isEmpty) {
      return const Center(child: Text('Нет медиафайлов'));
    }
    return GridView.builder(
      padding: const EdgeInsets.all(2),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 2,
        mainAxisSpacing: 2,
      ),
      itemCount: _mediaList.length,
      itemBuilder: (context, i) {
        final m = _mediaList[i];
        final id = _str(m, 'id');
        final type = _str(m, 'type');
        final dur = _int(m, 'duration');
        final name = _str(m, 'name');

        return InkWell(
          onTap: () => _showFileInfo(m),
          child: Stack(
            children: [
              Positioned.fill(
                child: FutureBuilder<Uint8List?>(
                  future: _thumbFuture(id, type),
                  builder: (context, snap) {
                    if (snap.hasData && snap.data != null) {
                      return Image.memory(snap.data!, fit: BoxFit.cover);
                    }
                    return Container(
                      color: _colorForType(type).withValues(alpha: 0.2),
                      child: Icon(
                        _iconForType(type),
                        size: 32,
                        color: _colorForType(type),
                      ),
                    );
                  },
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: Container(
                  color: Colors.black54,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 4,
                    vertical: 2,
                  ),
                  child: Text(
                    type == 'video' && dur > 0
                        ? '${_fmtDuration(dur)} · $name'
                        : name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white, fontSize: 9),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildAudioList() {
    if (_audioList.isEmpty) {
      return const Center(child: Text('Нет музыки'));
    }
    return ListView.separated(
      itemCount: _audioList.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final a = _audioList[i];
        final name = _str(a, 'name');
        final artist = _str(a, 'artist');
        final album = _str(a, 'album');
        final dur = _int(a, 'duration');
        final size = _int(a, 'size');

        return ListTile(
          leading: CircleAvatar(
            backgroundColor: Colors.purple.withValues(alpha: 0.15),
            child: const Icon(Icons.music_note, color: Colors.purple),
          ),
          title: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text(
            [
              if (artist.isNotEmpty && artist != '<unknown>') artist,
              if (album.isNotEmpty && album != '<unknown>') album,
              if (dur > 0) _fmtDuration(dur),
              _fmtBytes(size),
            ].join(' · '),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => _showFileInfo(a),
          onLongPress: () => _copyUri(a),
        );
      },
    );
  }

  Widget _buildFileList(List<Map<String, dynamic>> list, String emptyText) {
    if (list.isEmpty) {
      return Center(child: Text(emptyText));
    }
    return ListView.separated(
      itemCount: list.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final f = list[i];
        final name = _str(f, 'name');
        final type = _str(f, 'type');
        final mime = _str(f, 'mimeType');
        final size = _int(f, 'size');

        return ListTile(
          leading: CircleAvatar(
            backgroundColor: _colorForType(type).withValues(alpha: 0.15),
            child: Icon(_iconForType(type), color: _colorForType(type)),
          ),
          title: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text(
            '$mime · ${_fmtBytes(size)}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => _showFileInfo(f),
          onLongPress: () => _copyUri(f),
        );
      },
    );
  }

  Future<void> _showFileInfo(Map<String, dynamic> m) async {
    final name = _str(m, 'name');
    final type = _str(m, 'type');
    final uri = _str(m, 'uri');
    final path = _str(m, 'path');
    final mime = _str(m, 'mimeType');
    final size = _int(m, 'size');
    final dur = _int(m, 'duration');

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 12),
                _row('Тип', type),
                if (mime.isNotEmpty) _row('MIME', mime),
                _row('Размер', _fmtBytes(size)),
                if (dur > 0) _row('Длительность', _fmtDuration(dur)),
                if (uri.isNotEmpty) _row('URI', uri, mono: true),
                if (path.isNotEmpty) _row('Путь', path, mono: true),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.copy),
                        label: const Text('Копировать URI'),
                        onPressed: () {
                          Navigator.of(sheetContext).pop();
                          Clipboard.setData(ClipboardData(text: uri));
                          _showInfo('URI скопирован');
                        },
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ElevatedButton.icon(
                        icon: const Icon(Icons.download),
                        label: const Text('Прочитать'),
                        onPressed: () {
                          Navigator.of(sheetContext).pop();
                          _readBytes(m);
                        },
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _row(String k, String v, {bool mono = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 100, child: Text('$k:')),
          Expanded(
            child: Text(
              v,
              style: TextStyle(
                fontFamily: mono ? 'monospace' : null,
                fontSize: 13,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _copyUri(Map<String, dynamic> m) {
    final uri = _str(m, 'uri');
    if (uri.isEmpty) return;
    Clipboard.setData(ClipboardData(text: uri));
    _showInfo('URI скопирован в буфер обмена');
  }

  Future<void> _readBytes(Map<String, dynamic> m) async {
    final uri = _str(m, 'uri');
    if (uri.isEmpty) {
      _showError('URI отсутствует');
      return;
    }
    try {
      final bytes = await _lib.getFileBytes(uri);
      if (!mounted) return;
      if (bytes == null) {
        _showError('Не удалось прочитать файл');
        return;
      }
      _showInfo('Прочитано ${_fmtBytes(bytes.length)}');
      if (mounted) {
        final type = _str(m, 'type');
        if (type == 'image') {
          showDialog<void>(
            context: context,
            builder: (_) =>
                Dialog(child: InteractiveViewer(child: Image.memory(bytes))),
          );
        }
      }
    } catch (e) {
      if (mounted) _showError('Ошибка чтения: $e');
    }
  }

  Widget _buildPermissionBanner() {
    if (kIsWeb) return const SizedBox.shrink();

    if (!_hasBasicPermission) {
      return Container(
        color: Colors.orange.shade100,
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            const Icon(Icons.warning_amber, color: Colors.orange),
            const SizedBox(width: 8),
            const Expanded(
              child: Text(
                'Нужны разрешения на чтение медиа',
                style: TextStyle(color: Colors.black87),
              ),
            ),
            TextButton(onPressed: _bootstrap, child: const Text('Выдать')),
          ],
        ),
      );
    }

    final needsStorage =
        _section == LibrarySection.downloads ||
        _section == LibrarySection.documents;

    if (!needsStorage) return const SizedBox.shrink();

    final accessMode = _storageSupport?['accessMode']?.toString() ?? 'none';
    final requiresSaf = _storageSupport?['requiresSaf'] == true;
    final requiresManage = _storageSupport?['requiresManage'] == true;
    final supported = _storageSupport?['supported'] != false;

    if (!supported) {
      final reason = _storageSupport?['reason']?.toString() ?? '';
      return Container(
        color: Colors.grey.shade200,
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            const Icon(Icons.block, color: Colors.grey),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                reason.isNotEmpty
                    ? reason
                    : 'Доступ к этой папке не поддерживается на устройстве',
                style: const TextStyle(color: Colors.black87),
              ),
            ),
          ],
        ),
      );
    }

    if (accessMode == 'saf' && requiresSaf) {
      final folderName = _section == LibrarySection.downloads
          ? 'Download'
          : 'Documents';
      return Container(
        color: Colors.blue.shade50,
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            const Icon(Icons.folder_open, color: Colors.blue),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Выберите папку $folderName в проводнике. '
                'Это нужно сделать один раз — потом доступ сохранится.',
                style: const TextStyle(color: Colors.black87),
              ),
            ),
            TextButton(
              onPressed: _section == LibrarySection.downloads
                  ? _requestDownloadsFolderAccess
                  : _requestDocumentsFolderAccess,
              child: const Text('Выбрать'),
            ),
          ],
        ),
      );
    }

    if (requiresManage && !_hasAllFilesPermission) {
      return Container(
        color: Colors.red.shade50,
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            const Icon(Icons.lock_outline, color: Colors.red),
            const SizedBox(width: 8),
            const Expanded(
              child: Text(
                'Для Download/Documents нужен полный доступ к файлам',
                style: TextStyle(color: Colors.black87),
              ),
            ),
            TextButton(
              onPressed: _requestAllFilesAccess,
              child: const Text('Открыть настройки'),
            ),
          ],
        ),
      );
    }

    return const SizedBox.shrink();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titleForSection()),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Обновить',
            onPressed: _loadCurrentSection,
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(96),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _chip('Медиа', Icons.photo_library, LibrarySection.media),
                      const SizedBox(width: 8),
                      _chip('Музыка', Icons.music_note, LibrarySection.audio),
                      const SizedBox(width: 8),
                      _chip(
                        'Download',
                        Icons.download,
                        LibrarySection.downloads,
                      ),
                      const SizedBox(width: 8),
                      _chip(
                        'Documents',
                        Icons.folder,
                        LibrarySection.documents,
                      ),
                    ],
                  ),
                ),
              ),
              if (_isLoading) const LinearProgressIndicator(minHeight: 2),
            ],
          ),
        ),
      ),
      body: Column(
        children: [
          _buildPermissionBanner(),
          Expanded(child: _buildCurrentSection()),
        ],
      ),
    );
  }

  Widget _chip(String label, IconData icon, LibrarySection s) {
    final selected = _section == s;
    return ChoiceChip(
      label: Text(label),
      avatar: Icon(icon, size: 18),
      selected: selected,
      onSelected: (_) => _onSectionChanged(s),
    );
  }

  Widget _buildCurrentSection() {
    if (kIsWeb) {
      return const Center(child: Text('Веб-версия не поддерживает эти методы'));
    }
    switch (_section) {
      case LibrarySection.media:
        return _buildMediaGrid();
      case LibrarySection.audio:
        return _buildAudioList();
      case LibrarySection.downloads:
        return _buildFileList(_downloadsList, 'Нет файлов в Download');
      case LibrarySection.documents:
        return _buildFileList(_documentsList, 'Нет файлов в Documents');
    }
  }

  String _titleForSection() {
    switch (_section) {
      case LibrarySection.media:
        return 'Медиатека';
      case LibrarySection.audio:
        return 'Музыка (${_audioList.length})';
      case LibrarySection.downloads:
        return 'Download (${_downloadsList.length})';
      case LibrarySection.documents:
        return 'Documents (${_documentsList.length})';
    }
  }
}
