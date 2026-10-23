import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';

import 'courier_api.dart';
import 'offline_queue.dart';

void main() => runApp(const CourierApp());

class CourierApp extends StatelessWidget {
  const CourierApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'Parcel Tracker Courier',
        theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
        home: const CourierHome(),
      );
}

class CourierHome extends StatefulWidget {
  const CourierHome({super.key});

  @override
  State<CourierHome> createState() => _CourierHomeState();
}

class _CourierHomeState extends State<CourierHome> with WidgetsBindingObserver {
  final _courierField = TextEditingController();
  final _passwordField = TextEditingController();
  final _api = CourierApi(const String.fromEnvironment(
    'API_URL',
    defaultValue: 'http://10.0.2.2:8080',
  ), OfflineQueue());
  List<Map<String, dynamic>> _deliveries = [];
  Map<String, dynamic> _earnings = {};
  StreamSubscription<Position>? _locationSubscription;
  bool _busy = false;
  String? _message;
  int _queued = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _restoreSession();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _locationSubscription?.cancel();
    _courierField.dispose();
    _passwordField.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _api.courierId != null) {
      _refresh();
    }
  }

  Future<void> _restoreSession() async {
    await _api.restoreCredentials();
    if (_api.courierId == null) return;
    _courierField.text = _api.courierId!;
    try {
      await _refresh();
      await _startLocationUpdates();
    } catch (error) {
      if (mounted) setState(() => _message = '$error');
    }
  }

  Future<void> _signIn() async {
    await _run(() async {
      await _api.signIn(_courierField.text, _passwordField.text);
      _passwordField.clear();
      await _refresh();
      await _startLocationUpdates();
    });
  }

  Future<void> _refresh() async {
    try {
      await _api.flushQueue();
    } on SocketException {
      // Keep queued updates until the server is reachable.
    } on http.ClientException {
      // Keep queued updates until the server is reachable.
    }
    late final List<Map<String, dynamic>> deliveries;
    late final Map<String, dynamic> earnings;
    try {
      deliveries = await _api.deliveries();
      earnings = await _api.earnings();
    } on SocketException {
      if (mounted) setState(() => _message = 'Offline. Previously loaded deliveries remain available.');
      return;
    } on http.ClientException {
      if (mounted) setState(() => _message = 'Offline. Previously loaded deliveries remain available.');
      return;
    }
    final queued = await _api.queue.length;
    if (mounted) {
      setState(() {
        _deliveries = deliveries;
        _earnings = earnings;
        _queued = queued;
        _message = null;
      });
    }
  }

  Future<void> _startLocationUpdates() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      if (mounted) setState(() => _message = 'Turn on location services to share delivery GPS.');
      return;
    }
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
      if (mounted) setState(() => _message = 'Location permission is needed to share delivery GPS.');
      return;
    }
    await _locationSubscription?.cancel();
    _locationSubscription = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 100,
      ),
    ).listen((position) async {
      for (final parcel in _deliveries.where(_canShareLocation)) {
        try {
          await _api.updateLocation('${parcel['id']}', position.latitude, position.longitude);
        } catch (_) {
          if (mounted) setState(() => _message = 'Location update saved for retry.');
        }
      }
    });
  }

  bool _canShareLocation(Map<String, dynamic> parcel) =>
      const {'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'}.contains(parcel['status']);

  Future<void> _run(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      await action();
    } catch (error) {
      if (mounted) setState(() => _message = '$error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _mutate(Future<void> Function() action) => _run(() async {
        try {
          await action();
        } on SocketException {
          final queued = await _api.queue.length;
          if (mounted) {
            setState(() {
              _queued = queued;
              _message = 'Saved securely and will sync when online.';
            });
          }
          return;
        } on http.ClientException {
          final queued = await _api.queue.length;
          if (mounted) {
            setState(() {
              _queued = queued;
              _message = 'Saved securely and will sync when online.';
            });
          }
          return;
        }
        await _refresh();
      });

  Future<void> _navigate(Map<String, dynamic> parcel) async {
    final latitude = parcel['destinationLatitude'];
    final longitude = parcel['destinationLongitude'];
    final destination = latitude != null && longitude != null
        ? '$latitude,$longitude'
        : Uri.encodeComponent('${parcel['destination'] ?? ''}');
    final uri = Uri.parse('https://www.google.com/maps/dir/?api=1&destination=$destination');
    if (!await launchUrl(uri, mode: LaunchMode.externalApplication) && mounted) {
      setState(() => _message = 'Could not open the maps app.');
    }
  }

  Future<void> _captureProof(Map<String, dynamic> parcel) async {
    final proof = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (_) => const _ProofSheet(),
    );
    if (proof == null) return;
    await _mutate(() => _api.complete('${parcel['id']}', proof));
  }

  Future<void> _signOut() async {
    await _locationSubscription?.cancel();
    await _api.signOut();
    if (mounted) {
      setState(() {
        _deliveries = [];
        _earnings = {};
        _queued = 0;
        _message = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final signedIn = _api.courierId != null;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Courier deliveries'),
        actions: [
          if (signedIn)
            IconButton(
              tooltip: 'Sign out',
              onPressed: _signOut,
              icon: const Icon(Icons.logout),
            ),
        ],
      ),
      body: _busy
          ? const Center(child: CircularProgressIndicator())
          : signedIn
              ? _deliveryList()
              : _signInForm(),
    );
  }

  Widget _signInForm() => Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: _courierField,
                  decoration: const InputDecoration(labelText: 'Courier ID'),
                  textInputAction: TextInputAction.next,
                ),
                TextField(
                  controller: _passwordField,
                  decoration: const InputDecoration(labelText: 'Password'),
                  obscureText: true,
                  onSubmitted: (_) => _signIn(),
                ),
                const SizedBox(height: 16),
                FilledButton(onPressed: _signIn, child: const Text('Sign in')),
                if (_message != null) Text(_message!),
              ],
            ),
          ),
        ),
      );

  Widget _deliveryList() => RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Courier ${_api.courierId} · completed ${_earnings['completedDeliveries'] ?? 0} · '
              'payouts \$${_earnings['payouts'] ?? '0.00'} · queued $_queued',
            ),
            if (_message != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(_message!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            for (final parcel in _deliveries) _deliveryCard(parcel),
          ],
        ),
      );

  Widget _deliveryCard(Map<String, dynamic> parcel) {
    final id = '${parcel['id']}';
    final status = '${parcel['status'] ?? ''}';
    final canComplete = status == 'OUT_FOR_DELIVERY';
    final nextStatus = switch (status) {
      'CREATED' => 'PICKED_UP',
      'ACCEPTED' => 'PICKED_UP',
      'PICKED_UP' => 'IN_TRANSIT',
      'IN_TRANSIT' => 'OUT_FOR_DELIVERY',
      _ => null,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${parcel['trackingNumber'] ?? id} · $status',
                style: Theme.of(context).textTheme.titleMedium),
            Text('To ${parcel['recipient'] ?? 'recipient'}'),
            Text('${parcel['destination'] ?? parcel['pickupAddress'] ?? ''}'),
            Wrap(
              spacing: 8,
              children: [
                if (status == 'CREATED') ...[
                  TextButton(
                    onPressed: () => _mutate(() => _api.accept(id, true)),
                    child: const Text('Accept'),
                  ),
                  TextButton(
                    onPressed: () => _mutate(() => _api.accept(id, false)),
                    child: const Text('Reject'),
                  ),
                ],
                if (nextStatus != null && status != 'CREATED')
                  TextButton(
                    onPressed: () => _mutate(() => _api.setStatus(id, nextStatus)),
                    child: Text('Mark ${nextStatus.replaceAll('_', ' ').toLowerCase()}'),
                  ),
                TextButton.icon(
                  onPressed: () => _navigate(parcel),
                  icon: const Icon(Icons.navigation),
                  label: const Text('Navigate'),
                ),
                if (canComplete)
                  FilledButton(
                    onPressed: () => _captureProof(parcel),
                    child: const Text('Complete delivery'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ProofSheet extends StatefulWidget {
  const _ProofSheet();

  @override
  State<_ProofSheet> createState() => _ProofSheetState();
}

class _ProofSheetState extends State<_ProofSheet> {
  final _signatureKey = GlobalKey();
  final List<Offset?> _points = [];
  final _barcode = TextEditingController();
  final _note = TextEditingController();
  XFile? _photo;
  bool _saving = false;

  @override
  void dispose() {
    _barcode.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _scanBarcode() async {
    final value = await showDialog<String>(
      context: context,
      builder: (dialogContext) => Dialog(
        child: SizedBox(
          height: 360,
          child: MobileScanner(
            onDetect: (capture) {
              final code = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue;
              if (code != null) Navigator.pop(dialogContext, code);
            },
          ),
        ),
      ),
    );
    if (value != null) setState(() => _barcode.text = value);
  }

  Future<void> _takePhoto() async {
    final image = await ImagePicker().pickImage(
      source: ImageSource.camera,
      imageQuality: 70,
      maxWidth: 1600,
      maxHeight: 1600,
    );
    if (image != null) setState(() => _photo = image);
  }

  Future<void> _submit() async {
    if (_points.whereType<Offset>().length < 2 &&
        _barcode.text.trim().isEmpty &&
        _photo == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Add a signature, scan a barcode, or take a photo.')),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      final signature = _points.map((point) => point == null ? null : [point.dx, point.dy]).toList();
      final proof = <String, dynamic>{
        if (signature.any((point) => point != null)) 'recipientSignature': jsonEncode(signature),
        if (_barcode.text.trim().isNotEmpty) 'barcode': _barcode.text.trim(),
        if (_note.text.trim().isNotEmpty) 'dropOffNote': _note.text.trim(),
        if (_photo != null) 'photoBase64': base64Encode(await _photo!.readAsBytes()),
      };
      if (mounted) Navigator.pop(context, proof);
    } catch (error) {
      if (mounted) {
        setState(() => _saving = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Could not prepare proof: $error')));
      }
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.fromLTRB(16, 20, 16, MediaQuery.viewInsetsOf(context).bottom + 20),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Proof of delivery', style: Theme.of(context).textTheme.titleLarge),
              const Text('Sign below, scan a barcode, or take a drop-off photo.'),
              const SizedBox(height: 12),
              GestureDetector(
                onPanUpdate: (event) {
                  final box = _signatureKey.currentContext!.findRenderObject() as RenderBox;
                  setState(() => _points.add(box.globalToLocal(event.globalPosition)));
                },
                onPanEnd: (_) => setState(() => _points.add(null)),
                child: Container(
                  key: _signatureKey,
                  height: 140,
                  decoration: BoxDecoration(border: Border.all(color: Colors.grey)),
                  child: CustomPaint(painter: _SignaturePainter(List.of(_points)), size: Size.infinite),
                ),
              ),
              TextButton(
                onPressed: () => setState(_points.clear),
                child: const Text('Clear signature'),
              ),
              TextField(
                controller: _barcode,
                decoration: InputDecoration(
                  labelText: 'Barcode (optional)',
                  suffixIcon: IconButton(onPressed: _scanBarcode, icon: const Icon(Icons.qr_code_scanner)),
                ),
              ),
              TextField(controller: _note, decoration: const InputDecoration(labelText: 'Drop-off note (optional)')),
              OutlinedButton.icon(
                onPressed: _takePhoto,
                icon: const Icon(Icons.camera_alt),
                label: Text(_photo == null ? 'Take drop-off photo' : 'Photo ready'),
              ),
              FilledButton(
                onPressed: _saving ? null : _submit,
                child: Text(_saving ? 'Preparing…' : 'Submit proof'),
              ),
            ],
          ),
        ),
      );
}

class _SignaturePainter extends CustomPainter {
  const _SignaturePainter(this.points);
  final List<Offset?> points;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.black
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    for (var index = 0; index < points.length - 1; index++) {
      final start = points[index];
      final end = points[index + 1];
      if (start != null && end != null) canvas.drawLine(start, end, paint);
    }
  }

  @override
  bool shouldRepaint(_SignaturePainter oldDelegate) => oldDelegate.points != points;
}
