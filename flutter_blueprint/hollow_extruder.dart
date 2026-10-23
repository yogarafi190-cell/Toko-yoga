// ==============================================================================
// TOKO YOGA - 3D HOLLOW EXTRUSION MESH GENERATOR (FLUTTER / DART)
// Menghasilkan objek 3D berongga (hollow) dari List<Point2D> dan mengekspor .obj
// ==============================================================================

import 'dart:io';
import 'dart:math';
import 'package:path_provider/path_provider.dart';

/// Representasi Koordinat 2D pada kanvas sketsa penampang profil
class Point2D {
  final double x;
  final double y;

  const Point2D(this.x, this.y);

  Point2D operator +(Point2D other) => Point2D(x + other.x, y + other.y);
  Point2D operator -(Point2D other) => Point2D(x - other.x, y - other.y);
  Point2D operator *(double scalar) => Point2D(x * scalar, y * scalar);

  double length() => sqrt(x * x + y * y);

  Point2D normalized() {
    final len = length();
    return len > 0.0001 ? Point2D(x / len, y / len) : const Point2D(0, 0);
  }
}

/// Struktur data Titik Sudut 3D
class Vertex3D {
  final double x;
  final double y;
  final double z;

  const Vertex3D(this.x, this.y, this.z);

  Vertex3D operator +(Vertex3D o) => Vertex3D(x + o.x, y + o.y, z + o.z);
  Vertex3D operator -(Vertex3D o) => Vertex3D(x - o.x, y - o.y, z - o.z);
  Vertex3D operator *(double s) => Vertex3D(x * s, y * s, z * s);

  Vertex3D cross(Vertex3D o) => Vertex3D(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
      );

  Vertex3D normalized() {
    final len = sqrt(x * x + y * y + z * z);
    return len > 0.00001 ? Vertex3D(x / len, y / len, z / len) : const Vertex3D(0, 0, 1);
  }
}

/// Struktur data Mesh Lengkap
class Mesh3DResult {
  final List<Vertex3D> vertices;
  final List<Vertex3D> normals;
  final List<int> indices; // Daftar indeks segitiga (triplets)

  Mesh3DResult({
    required this.vertices,
    required this.normals,
    required this.indices,
  });
}

class HollowExtruder {
  /// Melakukan linear extrusion berongga (hollow) dari koordinat 2D
  ///
  /// [points] : Titik-titik penampang dinding luar profil (mm)
  /// [thickness] : Ketebalan dinding aluminium (mm)
  /// [panjangBatang] : Panjang ekstrusi sumbu Z (mm)
  /// [isClosed] : true untuk profil kotak/pipa/lingkar tertutup
  static Mesh3DResult generateHollowMesh({
    required List<Point2D> points,
    required double thickness,
    required double panjangBatang,
    bool isClosed = true,
  }) {
    if (points.length < 3) {
      return Mesh3DResult(vertices: [], normals: [], indices: []);
    }

    final double wallT = thickness.clamp(0.4, 50.0);
    final double zLength = panjangBatang.clamp(1.0, 12000.0);

    // 1. Hitung Poligon Dalam (Inner Loop) dengan Inward Normal Offset
    final innerPoints = _computeInwardOffset(points, wallT, isClosed);

    final List<Vertex3D> vertices = [];
    final List<Vertex3D> normals = [];
    final List<int> indices = [];

    final int n = points.length;

    // Index Map:
    // 0 .. n-1         : Outer Front (Z = 0)
    // n .. 2n-1        : Inner Front (Z = 0)
    // 2n .. 3n-1       : Outer Back  (Z = zLength)
    // 3n .. 4n-1       : Inner Back  (Z = zLength)

    for (var p in points) vertices.add(Vertex3D(p.x, p.y, 0));
    for (var p in innerPoints) vertices.add(Vertex3D(p.x, p.y, 0));
    for (var p in points) vertices.add(Vertex3D(p.x, p.y, zLength));
    for (var p in innerPoints) vertices.add(Vertex3D(p.x, p.y, zLength));

    void addQuad(int i1, int i2, int i3, int i4, {bool flipNormal = false}) {
      final v1 = vertices[i1];
      final v2 = vertices[i2];
      final v3 = vertices[i3];

      final edge1 = v2 - v1;
      final edge2 = v3 - v1;
      var norm = edge1.cross(edge2).normalized();
      if (flipNormal) norm = norm * -1.0;

      // Segitiga 1: i1, i2, i3
      indices.addAll([i1, i2, i3]);
      normals.add(norm);

      // Segitiga 2: i1, i3, i4
      indices.addAll([i1, i3, i4]);
      normals.add(norm);
    }

    final int segmentCount = isClosed ? n : n - 1;

    for (int i = 0; i < segmentCount; i++) {
      final int next = (i + 1) % n;

      final int outF1 = i;
      final int outF2 = next;
      final int inF1 = i + n;
      final int inF2 = next + n;

      final int outB1 = i + 2 * n;
      final int outB2 = next + 2 * n;
      final int inB1 = i + 3 * n;
      final int inB2 = next + 3 * n;

      // 1. Dinding Luar (Outer Side Walls)
      addQuad(outF1, outF2, outB2, outB1);

      // 2. Dinding Rongga Dalam (Inner Hollow Cavity Walls)
      addQuad(inF1, inB1, inB2, inF2, flipNormal: true);

      // 3. Ring Tutup Muka Depan (Front Ring Cap di Z = 0)
      addQuad(outF1, inF1, inF2, outF2);

      // 4. Ring Tutup Muka Belakang (Back Ring Cap di Z = zLength)
      addQuad(outB1, outB2, inB2, inB1);
    }

    return Mesh3DResult(
      vertices: vertices,
      normals: normals,
      indices: indices,
    );
  }

  /// Menghitung offset normal ke dalam (inward normal)
  static List<Point2D> _computeInwardOffset(
    List<Point2D> points,
    double thickness,
    bool isClosed,
  ) {
    final int n = points.length;
    final List<Point2D> inner = [];

    // Tentukan arah orientasi searah jarum jam (Clockwise)
    double signedArea = 0;
    for (int i = 0; i < n; i++) {
      final p1 = points[i];
      final p2 = points[(i + 1) % n];
      signedArea += (p2.x - p1.x) * (p2.y + p1.y);
    }
    final bool isCw = signedArea > 0;

    for (int i = 0; i < n; i++) {
      final prev = points[(i - 1 + n) % n];
      final curr = points[i];
      final next = points[(i + 1) % n];

      final d1 = (curr - prev).normalized();
      final d2 = (next - curr).normalized();

      final n1 = isCw ? Point2D(-d1.y, d1.x) : Point2D(d1.y, -d1.x);
      final n2 = isCw ? Point2D(-d2.y, d2.x) : Point2D(d2.y, -d2.x);

      var avgN = (n1 + n2).normalized();
      if (avgN.length() < 0.001) avgN = n1;

      final dot = avgN.x * n1.x + avgN.y * n1.y;
      final miterScale = dot > 0.2 ? (thickness / dot).clamp(thickness, thickness * 2.5) : thickness;

      inner.add(curr + (avgN * miterScale));
    }

    return inner;
  }

  /// Mengekspor hasil mesh menjadi file lokal berformat Wavefront .obj
  /// Menyimpannya ke direktori aplikasi lokal dan mengembalikan string file_3d_path.
  static Future<String> exportToObjFile({
    required Mesh3DResult mesh,
    required String sectionName,
  }) async {
    try {
      final directory = await getApplicationDocumentsDirectory();
      final modelsDir = Directory('${directory.path}/models_3d');
      if (!await modelsDir.exists()) {
        await modelsDir.create(recursive: true);
      }

      final cleanName = sectionName.replaceAll(RegExp(r'[^a-zA-Z0-9_]'), '_').toLowerCase();
      final filePath = '${modelsDir.path}/profil_${cleanName}_${DateTime.now().millisecondsSinceEpoch}.obj';
      final file = File(filePath);

      final buffer = StringBuffer();
      buffer.writeln('# TOKO YOGA 3D HOLLOW EXTRUSION MODEL');
      buffer.writeln('# Section: $sectionName');
      buffer.writeln('o $cleanName\n');

      // 1. Tulis Vertex (v x y z)
      for (var v in mesh.vertices) {
        buffer.writeln('v ${v.x.toStringAsFixed(4)} ${v.y.toStringAsFixed(4)} ${v.z.toStringAsFixed(4)}');
      }
      buffer.writeln();

      // 2. Tulis Normals (vn nx ny nz)
      for (var n in mesh.normals) {
        buffer.writeln('vn ${n.x.toStringAsFixed(4)} ${n.y.toStringAsFixed(4)} ${n.z.toStringAsFixed(4)}');
      }
      buffer.writeln();

      // 3. Tulis Faces (f v1//vn v2//vn v3//vn) - 1-based index
      final faceCount = mesh.indices.length ~/ 3;
      for (int i = 0; i < faceCount; i++) {
        final v1 = mesh.indices[i * 3] + 1;
        final v2 = mesh.indices[i * 3 + 1] + 1;
        final v3 = mesh.indices[i * 3 + 2] + 1;
        final vn = (i < mesh.normals.length) ? i + 1 : 1;
        buffer.writeln('f $v1//$vn $v2//$vn $v3//$vn');
      }

      await file.writeAsString(buffer.toString());
      return file.path; // Mengembalikan string path file .obj lokal
    } catch (e) {
      print('Gagal mengekspor file .obj: $e');
      return '';
    }
  }
}
