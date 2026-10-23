// ==============================================================================
// TOKO YOGA - FLUTTER MAIN ENTRYPOINT & LANDSCAPE MOBLO STUDIO UI
// Dikonfigurasi otomatis berjalan dalam mode LANDSCAPE (Horizontal)
// ==============================================================================

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'assembly_engine.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Memaksa aplikasi langsung berjalan dalam mode Landscape secara otomatis
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  runApp(const TokoYogaApp());
}

class TokoYogaApp extends StatelessWidget {
  const TokoYogaApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Toko Yoga 3D Studio',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF090D16),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFD97706),
          secondary: Color(0xFF059669),
          surface: Color(0xFF131B2A),
        ),
      ),
      home: const MobloStudioLandscapeScreen(),
    );
  }
}

/// Tampilan Utama Horizontal (Landscape) Standar Moblo 3D Studio
class MobloStudioLandscapeScreen extends StatefulWidget {
  const MobloStudioLandscapeScreen({Key? key}) : super(key: key);

  @override
  State<MobloStudioLandscapeScreen> createState() => _MobloStudioLandscapeScreenState();
}

class _MobloStudioLandscapeScreenState extends State<MobloStudioLandscapeScreen> {
  // Daftar objek batang aluminium dalam scene perakitan
  List<AssemblyPart> _parts = [];
  String? _selectedPartId;
  int _activeInspectorTab = 0; // 0: Dimensi, 1: Posisi, 2: Rotasi

  @override
  void initState() {
    super.initState();
    // Default: Preset Kusen Jendela (4 batang)
    _parts = AssemblyEngine.createKusenJendelaPreset(lebarMm: 800, tinggiMm: 1200);
    _selectedPartId = _parts.firstOrNull?.id;
  }

  AssemblyPart? get _selectedPart =>
      _parts.where((p) => p.id == _selectedPartId).firstOrNull;

  @override
  Widget build(BuildContext context) {
    final activePart = _selectedPart;

    return Scaffold(
      body: SafeArea(
        child: Row(
          children: [
            // =================================================================
            // 1. KOLOM KIRI: PANEL KOMPONEN & INVENTARIS RANGKA
            // =================================================================
            Container(
              width: 300,
              color: const Color(0xFF131B2A),
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Header
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Komponen Rangka',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                      IconButton(
                        onPressed: _tambahBatangBaru,
                        icon: const Icon(Icons.add_circle, color: Color(0xFFD97706)),
                        tooltip: 'Tambah Profil/Batang',
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),

                  // Presets Cepat
                  Row(
                    children: [
                      _buildPresetChip('Jendela', () {
                        setState(() {
                          _parts = AssemblyEngine.createKusenJendelaPreset(lebarMm: 800, tinggiMm: 1200);
                          _selectedPartId = _parts.firstOrNull?.id;
                        });
                      }),
                      const SizedBox(width: 4),
                      _buildPresetChip('Pintu', () {
                        setState(() {
                          _parts = AssemblyEngine.createKusenPintuPreset(lebarMm: 900, tinggiMm: 2100);
                          _selectedPartId = _parts.firstOrNull?.id;
                        });
                      }),
                      const SizedBox(width: 4),
                      _buildPresetChip('Meja/Rak', () {
                        setState(() {
                          _parts = AssemblyEngine.createRangkaMejaPreset();
                          _selectedPartId = _parts.firstOrNull?.id;
                        });
                      }),
                    ],
                  ),
                  const SizedBox(height: 8),

                  // Daftar Hirarki Objek
                  Expanded(
                    child: ListView.separated(
                      itemCount: _parts.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 6),
                      itemBuilder: (context, index) {
                        final part = _parts[index];
                        final isSelected = part.id == _selectedPartId;

                        return Container(
                          decoration: BoxDecoration(
                            color: isSelected ? const Color(0xFF1E293B) : const Color(0xFF0F172A),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isSelected ? const Color(0xFFF59E0B) : const Color(0xFF243046),
                              width: isSelected ? 1.5 : 0.5,
                            ),
                          ),
                          child: ListTile(
                            dense: true,
                            contentPadding: const EdgeInsets.symmetric(horizontal: 10),
                            title: Text(
                              part.name,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                color: isSelected ? const Color(0xFFF59E0B) : Colors.white,
                              ),
                            ),
                            subtitle: Text(
                              '${part.lengthMm.toInt()} mm • ${part.profileType}',
                              style: const TextStyle(fontSize: 11, color: Colors.grey),
                            ),
                            trailing: IconButton(
                              icon: const Icon(Icons.close, size: 16, color: Colors.redAccent),
                              onPressed: () {
                                setState(() {
                                  _parts.removeAt(index);
                                  if (_selectedPartId == part.id) {
                                    _selectedPartId = _parts.firstOrNull?.id;
                                  }
                                });
                              },
                            ),
                            onTap: () => setState(() => _selectedPartId = part.id),
                          ),
                        );
                      },
                    ),
                  ),

                  // Total & Ekspor
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0F172A),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      'Total: ${_parts.length} Batang (${_parts.fold<double>(0, (s, p) => s + p.lengthMm).toInt()} mm)',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ],
              ),
            ),

            // =================================================================
            // 2. AREA TENGAH/KANAN: KANVAS 3D VIEWPORT & PANEL KONTROL BAWAH
            // =================================================================
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(8.0),
                child: Column(
                  children: [
                    // KANVAS JENDELA 3D VIEWPORT LUAS
                    Expanded(
                      child: Container(
                        decoration: BoxDecoration(
                          color: const Color(0xFF0F172A),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: const Color(0xFF1E293B)),
                        ),
                        child: Stack(
                          children: [
                            // 3D Canvas Multi-Object Assembly Renderer
                            Center(
                              child: Text(
                                '3D Viewport Perakitan Rangka (${_parts.length} Batang)\nOrbit 360° • Zoom • Shading Aluminium',
                                textAlign: TextAlign.center,
                                style: const TextStyle(color: Color(0xFF64748B), fontSize: 13),
                              ),
                            ),
                            Positioned(
                              top: 10,
                              left: 10,
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: Colors.black54,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: const Text(
                                  'Viewport Moblo Studio • Sumbu X, Y, Z',
                                  style: TextStyle(fontSize: 11, color: Colors.white70),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),

                    // PANEL BAWAH: KONTROL PARAMETER (Dimensions, Position, Rotation)
                    Container(
                      height: 165,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFF131B2A),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFF1E293B)),
                      ),
                      child: activePart == null
                          ? const Center(child: Text('Pilih batang di panel kiri untuk mengatur parameter'))
                          : Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                // Tab Switcher
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text(
                                      activePart.name,
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                                    ),
                                    Row(
                                      children: [
                                        _buildInspectorTabButton('Dimensi', 0),
                                        _buildInspectorTabButton('Posisi (XYZ)', 1),
                                        _buildInspectorTabButton('Rotasi 3D', 2),
                                      ],
                                    ),
                                  ],
                                ),
                                const Divider(color: Color(0xFF1E293B), height: 16),

                                // Isi Parameter Aktif
                                Expanded(
                                  child: _activeInspectorTab == 0
                                      ? _buildDimensionsControls(activePart)
                                      : _activeInspectorTab == 1
                                          ? _buildPositionControls(activePart)
                                          : _buildRotationControls(activePart),
                                ),
                              ],
                            ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPresetChip(String label, VoidCallback onTap) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 4),
          decoration: BoxDecoration(
            color: const Color(0xFF1E293B),
            borderRadius: BorderRadius.circular(6),
          ),
          alignment: Alignment.Center,
          child: Text(label, style: const TextStyle(fontSize: 11)),
        ),
      ),
    );
  }

  Widget _buildInspectorTabButton(String label, int index) {
    final isActive = _activeInspectorTab == index;
    return InkWell(
      onTap: () => setState(() => _activeInspectorTab = index),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        margin: const EdgeInsets.only(left: 4),
        decoration: BoxDecoration(
          color: isActive ? const Color(0xFFD97706) : Colors.transparent,
          borderRadius: BorderRadius.circular(6),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
            color: isActive ? Colors.white : Colors.grey,
          ),
        ),
      ),
    );
  }

  Widget _buildDimensionsControls(AssemblyPart part) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Panjang: ${part.lengthMm.toInt()} mm', style: const TextStyle(fontSize: 11)),
              Slider(
                value: part.lengthMm,
                min: 100,
                max: 3000,
                onChanged: (v) => setState(() => part.lengthMm = v),
              ),
            ],
          ),
        ),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Tebal Hollow: ${part.thicknessMm.toStringAsFixed(2)} mm', style: const TextStyle(fontSize: 11)),
              Slider(
                value: part.thicknessMm,
                min: 0.8,
                max: 3.5,
                onChanged: (v) => setState(() => part.thicknessMm = v),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildPositionControls(AssemblyPart part) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: [
        _buildAxisControl('Pos X', part.posX, (v) => setState(() => part.posX = v)),
        _buildAxisControl('Pos Y (Tinggi)', part.posY, (v) => setState(() => part.posY = v)),
        _buildAxisControl('Pos Z (Tebal)', part.posZ, (v) => setState(() => part.posZ = v)),
      ],
    );
  }

  Widget _buildRotationControls(AssemblyPart part) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: [
        _buildAxisControl('Pitch X', part.rotPitch, (v) => setState(() => part.rotPitch = v), step: 45),
        _buildAxisControl('Yaw Y', part.rotYaw, (v) => setState(() => part.rotYaw = v), step: 45),
        _buildAxisControl('Roll Z', part.rotRoll, (v) => setState(() => part.rotRoll = v), step: 45),
      ],
    );
  }

  Widget _buildAxisControl(String name, double val, Function(double) onChange, {double step = 50}) {
    return Column(
      children: [
        Text('$name: ${val.toInt()}', style: const TextStyle(fontSize: 11)),
        Row(
          children: [
            IconButton(
              icon: const Icon(Icons.remove, size: 16),
              onPressed: () => onChange(val - step),
            ),
            IconButton(
              icon: const Icon(Icons.add, size: 16),
              onPressed: () => onChange(val + step),
            ),
          ],
        ),
      ],
    );
  }

  void _tambahBatangBaru() {
    setState(() {
      final newPart = AssemblyPart(
        id: UniqueKey().toString(),
        name: 'Batang #${_parts.length + 1}',
        lengthMm: 1000,
        widthMm: 40,
        heightMm: 40,
        thicknessMm: 1.35,
      );
      _parts.add(newPart);
      _selectedPartId = newPart.id;
    });
  }
}
