import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';

class OfflineQueue {
  static const _storage = FlutterSecureStorage();
  static final _cipher = AesGcm.with256bits();
  String? _courierId;
  File? _file;

  void useCourier(String courierId) {
    if (_courierId == courierId) return;
    _courierId = courierId;
    _file = null;
  }

  String get _keyName => 'offline-queue-key-${base64Url.encode(utf8.encode(_requireCourier()))}';

  Future<File> get _queueFile async {
    final file = _file;
    if (file != null) return file;
    final directory = await getApplicationSupportDirectory();
    final courierKey = base64Url.encode(utf8.encode(_requireCourier()));
    _file = File('${directory.path}/courier-queue-$courierKey.enc');
    return _file!;
  }

  String _requireCourier() => _courierId ?? (throw StateError('Courier is not signed in.'));

  Future<SecretKey> _key() async {
    var encodedKey = await _storage.read(key: _keyName);
    if (encodedKey == null) {
      final random = Random.secure();
      encodedKey = base64Encode(List<int>.generate(32, (_) => random.nextInt(256)));
      await _storage.write(key: _keyName, value: encodedKey);
    }
    return SecretKey(base64Decode(encodedKey));
  }

  Future<List<Map<String, dynamic>>> read() async {
    final file = await _queueFile;
    if (!await file.exists()) return [];
    final encrypted = base64Decode(await file.readAsString());
    final box = SecretBox.fromConcatenation(encrypted, nonceLength: 12, macLength: 16);
    final clearText = await _cipher.decrypt(box, secretKey: await _key());
    return (jsonDecode(utf8.decode(clearText)) as List)
        .cast<Map<String, dynamic>>();
  }

  Future<void> enqueue(Map<String, dynamic> request) async {
    final requests = await read();
    requests.add(request);
    await _write(requests);
  }

  Future<void> removeFirst() async {
    final requests = await read();
    if (requests.isEmpty) return;
    requests.removeAt(0);
    await _write(requests);
  }

  Future<int> get length async => (await read()).length;

  Future<void> _write(List<Map<String, dynamic>> requests) async {
    final box = await _cipher.encrypt(
      utf8.encode(jsonEncode(requests)),
      secretKey: await _key(),
    );
    final file = await _queueFile;
    await file.writeAsString(base64Encode(box.concatenation()));
  }

  Future<void> clear() async {
    final file = await _queueFile;
    if (await file.exists()) await file.delete();
    await _storage.delete(key: _keyName);
  }
}
