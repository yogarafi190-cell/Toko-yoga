package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FlutterBlueprintScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val modules = listOf(
        "1. main.dart (Landscape Moblo UI)",
        "2. Multi-Object Assembly Engine",
        "3. DatabaseHelper (sqflite)",
        "4. 3D Hollow Mesh (.obj)",
        "5. Form Dynamic (Add/Edit)",
        "6. Cutting Stock Logic",
        "7. Excel Export Service",
        "8. History Stack (Undo/Redo)",
        "9. Search & Filter Bar"
    )

    val flutterCodeSnippets = listOf(
        // Modul 1: main.dart Landscape
        """// ==============================================================================
// 1. FLUTTER ENTRYPOINT - LANDSCAPE ORIENTATION & MOBLO STUDIO 2-COLUMN UI
// Memaksa aplikasi langsung berjalan dalam mode Landscape secara otomatis
// ==============================================================================
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Kunci orientasi ke Horizontal Landscape saat APK dibuka
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  runApp(const TokoYogaApp());
}

class MobloStudioLandscapeScreen extends StatefulWidget {
  const MobloStudioLandscapeScreen({Key? key}) : super(key: key);
  @override
  State<MobloStudioLandscapeScreen> createState() => _MobloStudioLandscapeScreenState();
}

class _MobloStudioLandscapeScreenState extends State<MobloStudioLandscapeScreen> {
  // Struktur 2-Column Landscape:
  // - Kolom Kiri (300px): Panel Komponen & Inventaris Rangka (+ Tambah Profil)
  // - Area Tengah/Kanan: Kanvas Jendela 3D Viewport Luas (X, Y, Z)
  // - Panel Bawah (165px): Kontrol Parameter (Dimensi, Posisi XYZ, Rotasi 3D)
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Row(
          children: [
            // Kolom Kiri
            Container(width: 300, color: const Color(0xFF131B2A), child: _buildHierarchyPanel()),
            // Area Viewport & Panel Bawah
            Expanded(
              child: Column(
                children: [
                  Expanded(child: _build3DViewport()),
                  Container(height: 165, color: const Color(0xFF131B2A), child: _buildParameterControls()),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}""",

        // Modul 2: Multi-Object Assembly Engine
        """// ==============================================================================
// 2. LOGIKA PERAKITAN RANGKA (MULTI-OBJECT ASSEMBLY ENGINE)
// Menggabungkan banyak batang aluminium dengan koordinat & rotasi independen
// ==============================================================================
class AssemblyPart {
  final String id;
  String name;
  String profileType;
  double lengthMm;
  double posX, posY, posZ;
  double rotPitch, rotYaw, rotRoll;
  String color;

  AssemblyPart({
    required this.id,
    required this.name,
    this.profileType = 'Hollow Box',
    this.lengthMm = 1000.0,
    this.posX = 0.0,
    this.posY = 0.0,
    this.posZ = 0.0,
    this.rotPitch = 0.0,
    this.rotYaw = 0.0,
    this.rotRoll = 0.0,
    this.color = 'Silver',
  });
}

class AssemblyEngine {
  // Preset Kusen Jendela (4 Batang: 2 Tiang Vertikal & 2 Kusen Ambang)
  static List<AssemblyPart> createKusenJendelaPreset({double lebarMm = 800, double tinggiMm = 1200}) {
    return [
      AssemblyPart(id: 't_kiri', name: 'Tiang Kiri', lengthMm: tinggiMm, posX: 0, posY: 0, posZ: 0, rotPitch: 90),
      AssemblyPart(id: 't_kanan', name: 'Tiang Kanan', lengthMm: tinggiMm, posX: lebarMm, posY: 0, posZ: 0, rotPitch: 90),
      AssemblyPart(id: 'a_atas', name: 'Ambang Atas', lengthMm: lebarMm, posX: 0, posY: tinggiMm, posZ: 0, rotYaw: 90),
      AssemblyPart(id: 'a_bawah', name: 'Ambang Bawah', lengthMm: lebarMm, posX: 0, posY: 0, posZ: 0, rotYaw: 90),
    ];
  }
}""",

        // Modul 3: DatabaseHelper
        """// ==============================================================================
// 3. DATABASE LOKAL OFFLINE (FLUTTER / sqflite)
// CRUD Lengkap untuk produk_profil, transaksi, dan transaksi_detail
// ==============================================================================
import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

class DatabaseHelper {
  static final DatabaseHelper instance = DatabaseHelper._init();
  static Database? _database;
  DatabaseHelper._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('toko_yoga_offline.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);
    return await openDatabase(path, version: 1, onCreate: _createDB);
  }

  Future<void> _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE produk_profil (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        section_no TEXT NOT NULL,
        kategori TEXT NOT NULL,
        thickness REAL NOT NULL,
        harga_per_meter INTEGER NOT NULL,
        warna TEXT NOT NULL,
        file_3d_path TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE transaksi (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tanggal TEXT NOT NULL,
        total_harga INTEGER NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE transaksi_detail (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        transaksi_id INTEGER NOT NULL,
        produk_id INTEGER NOT NULL,
        panjang_diminta REAL NOT NULL,
        jumlah_batang INTEGER NOT NULL,
        subtotal INTEGER NOT NULL,
        FOREIGN KEY (transaksi_id) REFERENCES transaksi (id) ON DELETE CASCADE,
        FOREIGN KEY (produk_id) REFERENCES produk_profil (id) ON DELETE CASCADE
      )
    ''');
  }

  Future<int> insertProduk(ProdukProfil produk) async {
    final db = await instance.database;
    return await db.insert('produk_profil', produk.toMap());
  }

  Future<int> updateProduk(ProdukProfil produk) async {
    final db = await instance.database;
    return await db.update('produk_profil', produk.toMap(), where: 'id = ?', whereArgs: [produk.id]);
  }
}""",

        // Modul 4: 3D Hollow Mesh
        """// ==============================================================================
// 4. LOGIKA MATEMATIKA 3D HOLLOW EXTRUSION (FLUTTER / DART)
// ==============================================================================
class HollowExtruder {
  static Future<String> generateAndExportObj({
    required List<Point2D> points,
    required double thickness,
    required double panjangBatang,
    required String sectionName,
  }) async {
    final int n = points.length;
    final List<Point2D> inner = [];
    for (int i = 0; i < n; i++) {
      final prev = points[(i - 1 + n) % n];
      final curr = points[i];
      final next = points[(i + 1) % n];
      final d1 = (curr - prev).normalized();
      final d2 = (next - curr).normalized();
      final n1 = Point2D(-d1.y, d1.x);
      final n2 = Point2D(-d2.y, d2.x);
      var avgN = (n1 + n2).normalized();
      inner.add(curr + (avgN * thickness));
    }
    // Linear Extrusion sumbu Z dan simpan ke file .obj lokal
    final dir = await getApplicationDocumentsDirectory();
    final file = File("${'$'}{dir.path}/profil_${'$'}{sectionName}.obj");
    // Return file_3d_path
    return file.path;
  }
}""",

        // Modul 5: Form Dynamic
        """// ==============================================================================
// 5. FORM UI INPUT & EDIT DYNAMIC (FLUTTER / DART)
// ==============================================================================
class FormProfilPage extends StatefulWidget {
  final ProdukProfil? existingProduk;
  const FormProfilPage({Key? key, this.existingProduk}) : super(key: key);
  @override
  State<FormProfilPage> createState() => _FormProfilPageState();
}
class _FormProfilPageState extends State<FormProfilPage> {
  // Mode Create New vs Edit Existing dengan auto pre-fill
}""",

        // Modul 6: Cutting Stock Logic
        """// ==============================================================================
// 6. KALKULATOR OPTIMASI POTONGAN BAHAN (FLUTTER / DART)
// 1D Bin Packing (Best-Fit Decreasing) dengan Kerf Mata Pisau
// ==============================================================================
class CuttingStockOptimizer {
  static CuttingOptimizationResult optimize({
    double standardBarLengthMm = 6000.0,
    required List<CutItem> items,
    double sawKerfMm = 3.0,
  }) {
    // Menghitung minimal batang utuh & sisa limbah scrap dalam mm
  }
}""",

        // Modul 7: Excel Export Service
        """// ==============================================================================
// 7. EKSPOR LAPORAN NOTA EXCEL OFFLINE (FLUTTER / DART)
// ==============================================================================
class ExcelExportService {
  static Future<String?> exportNotaToExcel(int transaksiId) async {
    // Generate .xlsx offline dan bagikan via WhatsApp / Bluetooth
  }
}""",

        // Modul 8: History Stack (Undo/Redo)
        """// ==============================================================================
// 8. SISTEM HISTORY STACK (UNDO / REDO) FLUTTER / DART
// Merekam snapshot perakitan multi-objek untuk pembatalan perubahan koordinat/batang
// ==============================================================================
class AssemblySnapshot {
  final List<AssemblyPart> parts;
  final String? selectedPartId;
  final String actionLabel;
  final DateTime timestamp;

  AssemblySnapshot({
    required List<AssemblyPart> parts,
    this.selectedPartId,
    this.actionLabel = 'Perubahan',
  })  : parts = parts.map((p) => p.clone()).toList(),
        timestamp = DateTime.now();
}

class AssemblyHistoryManager {
  final List<AssemblySnapshot> _undoStack = [];
  final List<AssemblySnapshot> _redoStack = [];
  final int maxHistorySize;

  AssemblyHistoryManager({this.maxHistorySize = 50});

  bool get canUndo => _undoStack.isNotEmpty;
  bool get canRedo => _redoStack.isNotEmpty;
  int get undoCount => _undoStack.length;
  int get redoCount => _redoStack.length;

  void recordChange(List<AssemblyPart> currentParts, String? selectedId, String actionLabel) {
    _undoStack.add(AssemblySnapshot(parts: currentParts, selectedPartId: selectedId, actionLabel: actionLabel));
    if (_undoStack.length > maxHistorySize) {
      _undoStack.removeAt(0);
    }
    _redoStack.clear();
  }

  AssemblySnapshot? undo(List<AssemblyPart> currentParts, String? selectedId) {
    if (_undoStack.isEmpty) return null;
    final prev = _undoStack.removeLast();
    _redoStack.add(AssemblySnapshot(parts: currentParts, selectedPartId: selectedId, actionLabel: prev.actionLabel));
    return prev;
  }

  AssemblySnapshot? redo(List<AssemblyPart> currentParts, String? selectedId) {
    if (_redoStack.isEmpty) return null;
    final next = _redoStack.removeLast();
    _undoStack.add(AssemblySnapshot(parts: currentParts, selectedPartId: selectedId, actionLabel: next.actionLabel));
    return next;
  }
}""",
        // Modul 9: Search & Filter Bar (Inventory & Transactions)
        """// ==============================================================================
// 9. SEARCH & FILTER BAR - FILTER BY SECTION NUMBER OR PRODUCT CATEGORY (FLUTTER)
// ==============================================================================
import 'package:flutter/material.dart';

class SearchFilterBar extends StatelessWidget {
  final TextEditingController controller;
  final String hintText;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;
  final List<String> categories;
  final String selectedCategory;
  final ValueChanged<String> onSelectCategory;
  final VoidCallback onResetAll;

  const SearchFilterBar({
    Key? key,
    required this.controller,
    required this.hintText,
    required this.onChanged,
    required this.onClear,
    required this.categories,
    required this.selectedCategory,
    required this.onSelectCategory,
    required this.onResetAll,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 1. Search TextField
        TextField(
          controller: controller,
          onChanged: onChanged,
          decoration: InputDecoration(
            hintText: hintText,
            prefixIcon: const Icon(Icons.search),
            suffixIcon: controller.text.isNotEmpty
                ? IconButton(icon: const Icon(Icons.clear), onPressed: onClear)
                : null,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          ),
        ),
        const SizedBox(height: 8),

        // 2. Category Filter Chips (Shop Front, Sliding Door, Casement Window, etc.)
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              ...categories.map((cat) {
                final isSelected = selectedCategory == cat;
                return Padding(
                  padding: const EdgeInsets.only(right: 6.0),
                  child: FilterChip(
                    label: Text(cat),
                    selected: isSelected,
                    onSelected: (_) => onSelectCategory(cat),
                  ),
                );
              }),
              if (controller.text.isNotEmpty || selectedCategory != 'Semua')
                TextButton.icon(
                  onPressed: onResetAll,
                  icon: const Icon(Icons.refresh, size: 16),
                  label: const Text('Reset'),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

// Logic Penyaringan di Halaman Inventaris & Transaksi:
// bool matchItem(ProdukProfil item, String query, String category) {
//   final matchQuery = query.isEmpty ||
//       item.sectionNo.toLowerCase().contains(query.toLowerCase()) ||
//       item.kategori.toLowerCase().contains(query.toLowerCase());
//   final matchCat = category == 'Semua' || item.kategori.toLowerCase() == category.toLowerCase();
//   return matchQuery && matchCat;
// }"""
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Blueprint Arsitektur Flutter (Dart)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Format Landscape Moblo 3D Studio, Multi-Object Assembly & SQLite Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Flutter Code", flutterCodeSnippets[selectedTab])
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Kode modul berhasil disalin!", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                modifier = Modifier.testTag("btn_copy_flutter_code")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salin Modul", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = Color(0xFF131B2A),
            contentColor = Color(0xFFF59E0B)
        ) {
            modules.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Code Viewer Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()

            Text(
                text = flutterCodeSnippets[selectedTab],
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = Color(0xFFE2E8F0),
                modifier = Modifier
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
            )
        }
    }
}
