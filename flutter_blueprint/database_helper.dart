// ==============================================================================
// TOKO YOGA - ARSITEKTUR DATABASE LOKAL OFFLINE (FLUTTER / DART)
// Menggunakan sqflite & path untuk persistensi 100% offline tanpa internet
// ==============================================================================

import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

/// Model Data Profil Aluminium
class ProdukProfil {
  final int? id;
  final String sectionNo;
  final String kategori; // Shop Front, Sliding Door, Casement Window, Curtain Wall, Commodities
  final double thickness; // mm
  final int hargaPerMeter; // Rupiah
  final String warna; // Silver, Black, Brown, Putih
  final String file3dPath; // Lokasi path lokal file .obj

  ProdukProfil({
    this.id,
    required this.sectionNo,
    required this.kategori,
    required this.thickness,
    required this.hargaPerMeter,
    required this.warna,
    this.file3dPath = '',
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'section_no': sectionNo,
      'kategori': kategori,
      'thickness': thickness,
      'harga_per_meter': hargaPerMeter,
      'warna': warna,
      'file_3d_path': file3dPath,
    };
  }

  factory ProdukProfil.fromMap(Map<String, dynamic> map) {
    return ProdukProfil(
      id: map['id'] as int?,
      sectionNo: map['section_no'] as String,
      kategori: map['kategori'] as String,
      thickness: (map['thickness'] as num).toDouble(),
      hargaPerMeter: map['harga_per_meter'] as int,
      warna: map['warna'] as String,
      file3dPath: (map['file_3d_path'] as String?) ?? '',
    );
  }

  ProdukProfil copyWith({
    int? id,
    String? sectionNo,
    String? kategori,
    double? thickness,
    int? hargaPerMeter,
    String? warna,
    String? file3dPath,
  }) {
    return ProdukProfil(
      id: id ?? this.id,
      sectionNo: sectionNo ?? this.sectionNo,
      kategori: kategori ?? this.kategori,
      thickness: thickness ?? this.thickness,
      hargaPerMeter: hargaPerMeter ?? this.hargaPerMeter,
      warna: warna ?? this.warna,
      file3dPath: file3dPath ?? this.file3dPath,
    );
  }
}

/// Model Data Transaksi Kasir POS
class Transaksi {
  final int? id;
  final String tanggal; // YYYY-MM-DD HH:mm
  final int totalHarga;

  Transaksi({
    this.id,
    required this.tanggal,
    required this.totalHarga,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'tanggal': tanggal,
      'total_harga': totalHarga,
    };
  }

  factory Transaksi.fromMap(Map<String, dynamic> map) {
    return Transaksi(
      id: map['id'] as int?,
      tanggal: map['tanggal'] as String,
      totalHarga: map['total_harga'] as int,
    );
  }
}

/// Model Data Rincian Potongan Transaksi
class TransaksiDetail {
  final int? id;
  final int transaksiId;
  final int produkId;
  final double panjangDiminta; // mm
  final int jumlahBatang;
  final int subtotal;

  TransaksiDetail({
    this.id,
    required this.transaksiId,
    required this.produkId,
    required this.panjangDiminta,
    required this.jumlahBatang,
    required this.subtotal,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'transaksi_id': transaksiId,
      'produk_id': produkId,
      'panjang_diminta': panjangDiminta,
      'jumlah_batang': jumlahBatang,
      'subtotal': subtotal,
    };
  }

  factory TransaksiDetail.fromMap(Map<String, dynamic> map) {
    return TransaksiDetail(
      id: map['id'] as int?,
      transaksiId: map['transaksi_id'] as int,
      produkId: map['produk_id'] as int,
      panjangDiminta: (map['panjang_diminta'] as num).toDouble(),
      jumlahBatang: map['jumlah_batang'] as int,
      subtotal: map['subtotal'] as int,
    );
  }
}

/// Helper Pengelola Database SQLite Offline (Singleton)
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

    return await openDatabase(
      path,
      version: 1,
      onCreate: _createDB,
    );
  }

  Future<void> _createDB(Database db, int version) async {
    // 1. Tabel Produk Profil
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

    // 2. Tabel Transaksi
    await db.execute('''
      CREATE TABLE transaksi (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tanggal TEXT NOT NULL,
        total_harga INTEGER NOT NULL
      )
    ''');

    // 3. Tabel Transaksi Detail
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

    // Data Awal Standar Toko Yoga
    await db.insert('produk_profil', {
      'section_no': 'SF-001 Open Back',
      'kategori': 'Shop Front',
      'thickness': 1.25,
      'harga_per_meter': 48000,
      'warna': 'Silver',
      'file_3d_path': ''
    });
    await db.insert('produk_profil', {
      'section_no': 'SD-9055 Kusen Geser',
      'kategori': 'Sliding Door',
      'thickness': 1.35,
      'harga_per_meter': 62000,
      'warna': 'Black',
      'file_3d_path': ''
    });
  }

  // ---------------------------------------------------------------------------
  // CRUD PRODUK PROFIL
  // ---------------------------------------------------------------------------

  /// Create: Tambah produk profil baru
  Future<int> insertProduk(ProdukProfil produk) async {
    final db = await instance.database;
    return await db.insert('produk_profil', produk.toMap());
  }

  /// Read: Ambil seluruh daftar profil
  Future<List<ProdukProfil>> getAllProduk() async {
    final db = await instance.database;
    final result = await db.query('produk_profil', orderBy: 'id DESC');
    return result.map((json) => ProdukProfil.fromMap(json)).toList();
  }

  /// Read: Ambil profil berdasarkan ID
  Future<ProdukProfil?> getProdukById(int id) async {
    final db = await instance.database;
    final maps = await db.query(
      'produk_profil',
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (maps.isNotEmpty) {
      return ProdukProfil.fromMap(maps.first);
    }
    return null;
  }

  /// UPDATE: Mengedit data profil jika terjadi kesalahan input
  Future<int> updateProduk(ProdukProfil produk) async {
    final db = await instance.database;
    return await db.update(
      'produk_profil',
      produk.toMap(),
      where: 'id = ?',
      whereArgs: [produk.id],
    );
  }

  /// Delete: Hapus produk berdasarkan ID
  Future<int> deleteProduk(int id) async {
    final db = await instance.database;
    return await db.delete(
      'produk_profil',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  // ---------------------------------------------------------------------------
  // CRUD TRANSAKSI & DETAIL
  // ---------------------------------------------------------------------------

  /// Simpan Transaksi Kasir POS beserta Detail Potongannya dalam 1 Batch Aman
  Future<int> simpanTransaksiLengkap({
    required Transaksi transaksi,
    required List<TransaksiDetail> details,
  }) async {
    final db = await instance.database;
    return await db.transaction((txn) async {
      final trxId = await txn.insert('transaksi', transaksi.toMap());
      for (var detail in details) {
        final map = detail.toMap();
        map['transaksi_id'] = trxId;
        await txn.insert('transaksi_detail', map);
      }
      return trxId;
    });
  }

  /// Read: Seluruh riwayat transaksi
  Future<List<Transaksi>> getAllTransaksi() async {
    final db = await instance.database;
    final result = await db.query('transaksi', orderBy: 'id DESC');
    return result.map((json) => Transaksi.fromMap(json)).toList();
  }

  /// Read: Detail transaksi berdasarkan transaksi_id
  Future<List<Map<String, dynamic>>> getDetailTransaksiWithProduct(int transaksiId) async {
    final db = await instance.database;
    return await db.rawQuery('''
      SELECT td.*, p.section_no, p.kategori, p.warna, p.thickness, p.harga_per_meter
      FROM transaksi_detail td
      JOIN produk_profil p ON td.produk_id = p.id
      WHERE td.transaksi_id = ?
    ''', [transaksiId]);
  }

  Future<void> close() async {
    final db = await instance.database;
    db.close();
  }
}
