import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

import 'offline_queue.dart';

class CourierApi {
  CourierApi(String baseUrl, this.queue)
      : baseUrl = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;

  final String baseUrl;
  final OfflineQueue queue;
  final _client = http.Client();
  final _storage = const FlutterSecureStorage();
  String? _courierId;
  String? _password;

  String? get courierId => _courierId;

  Future<void> restoreCredentials() async {
    _courierId = await _storage.read(key: 'courier-id');
    _password = await _storage.read(key: 'courier-password');
    if (_courierId != null) queue.useCourier(_courierId!);
  }

  Future<void> signIn(String courierId, String password) async {
    _courierId = courierId.trim();
    _password = password;
    try {
      final result = await _send('GET', '/api/couriers/${_segment(_courierId!)}/me');
      if (result['courierId'] != _courierId) {
        throw const HttpException('The server returned a different courier identity.');
      }
    } catch (_) {
      _courierId = null;
      _password = null;
      rethrow;
    }
    queue.useCourier(_courierId!);
    await _storage.write(key: 'courier-id', value: _courierId);
    await _storage.write(key: 'courier-password', value: _password);
  }

  Future<void> signOut() async {
    _courierId = null;
    _password = null;
    await _storage.delete(key: 'courier-id');
    await _storage.delete(key: 'courier-password');
  }

  Future<List<Map<String, dynamic>>> deliveries() async {
    final data = await _send('GET', '/api/couriers/${_segment(_requireId())}/deliveries');
    return (data as List).cast<Map<String, dynamic>>();
  }

  Future<Map<String, dynamic>> earnings() =>
      _send('GET', '/api/couriers/${_segment(_requireId())}/earnings');

  Future<void> accept(String parcelId, bool accepted) => _mutate(
        'PUT',
        '/api/couriers/${_segment(_requireId())}/deliveries/${_segment(parcelId)}/decision',
        {'accepted': accepted},
      );

  Future<void> setStatus(String parcelId, String status) => _mutate(
        'PUT',
        '/api/couriers/${_segment(_requireId())}/deliveries/${_segment(parcelId)}/status',
        {'status': status},
      );

  Future<void> updateLocation(String parcelId, double latitude, double longitude) => _mutate(
        'POST',
        '/api/couriers/${_segment(_requireId())}/deliveries/${_segment(parcelId)}/location',
        {
          'latitude': latitude,
          'longitude': longitude,
          'timestamp': DateTime.now().toUtc().toIso8601String(),
        },
      );

  Future<void> complete(String parcelId, Map<String, dynamic> proof) => _mutate(
        'POST',
        '/api/couriers/${_segment(_requireId())}/deliveries/${_segment(parcelId)}/proof',
        proof,
      );

  Future<void> flushQueue() async {
    while (await queue.length > 0) {
      final request = (await queue.read()).first;
      await _send(request['method'] as String, request['path'] as String,
          request['body'] as Map<String, dynamic>?);
      await queue.removeFirst();
    }
  }

  Future<void> _mutate(String method, String path, Map<String, dynamic> body) async {
    try {
      await _send(method, path, body);
    } on SocketException {
      await queue.enqueue({'method': method, 'path': path, 'body': body});
      rethrow;
    } on http.ClientException {
      await queue.enqueue({'method': method, 'path': path, 'body': body});
      rethrow;
    }
  }

  Future<dynamic> _send(String method, String path, [Map<String, dynamic>? body]) async {
    final id = _requireId();
    final credential = base64Encode(utf8.encode('$id:${_password ?? ''}'));
    final uri = Uri.parse('$baseUrl$path');
    final headers = {'Authorization': 'Basic $credential', 'Content-Type': 'application/json'};
    final request = http.Request(method, uri)
      ..headers.addAll(headers)
      ..body = body == null ? '' : jsonEncode(body);
    final response = await _client.send(request).then(http.Response.fromStream);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('Request failed (${response.statusCode}): ${response.body}', uri: uri);
    }
    return response.body.isEmpty ? null : jsonDecode(response.body);
  }

  String _requireId() {
    final id = _courierId;
    if (id == null || id.isEmpty || _password == null) {
      throw const HttpException('Sign in to continue.');
    }
    return id;
  }

  String _segment(String value) => Uri.encodeComponent(value);
}
