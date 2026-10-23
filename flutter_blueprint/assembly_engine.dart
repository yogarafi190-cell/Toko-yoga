// ==============================================================================
// TOKO YOGA - FLUTTER MULTI-OBJECT ASSEMBLY ENGINE (FLUTTER / DART)
// Menggabungkan banyak batang aluminium dengan koordinat & rotasi independen
// ==============================================================================

import 'dart:math';
import 'hollow_extruder.dart';

class AssemblyPart {
  final String id;
  String name;
  String profileType;
  double lengthMm;
  double widthMm;
  double heightMm;
  double thicknessMm;
  double posX;
  double posY;
  double posZ;
  double rotPitch; // Derajat rotasi sumbu X
  double rotYaw;   // Derajat rotasi sumbu Y
  double rotRoll;  // Derajat rotasi sumbu Z
  String color;

  AssemblyPart({
    required this.id,
    required this.name,
    this.profileType = 'Hollow Box',
    this.lengthMm = 1000.0,
    this.widthMm = 40.0,
    this.heightMm = 40.0,
    this.thicknessMm = 1.35,
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
  /// Preset Kusen Jendela (4 Batang: 2 Tiang Vertikal & 2 Kusen Ambang)
  static List<AssemblyPart> createKusenJendelaPreset({
    double lebarMm = 800.0,
    double tinggiMm = 1200.0,
  }) {
    return [
      AssemblyPart(
        id: 'tiang_kiri',
        name: 'Tiang Vertikal Kiri',
        lengthMm: tinggiMm,
        posX: 0,
        posY: 0,
        posZ: 0,
        rotPitch: 90, // Berdiri tegak
      ),
      AssemblyPart(
        id: 'tiang_kanan',
        name: 'Tiang Vertikal Kanan',
        lengthMm: tinggiMm,
        posX: lebarMm,
        posY: 0,
        posZ: 0,
        rotPitch: 90,
      ),
      AssemblyPart(
        id: 'ambang_atas',
        name: 'Kusen Ambang Atas',
        lengthMm: lebarMm,
        posX: 0,
        posY: tinggiMm,
        posZ: 0,
        rotYaw: 90, // Melintang horizontal
      ),
      AssemblyPart(
        id: 'ambang_bawah',
        name: 'Kusen Ambang Bawah',
        lengthMm: lebarMm,
        posX: 0,
        posY: 0,
        posZ: 0,
        rotYaw: 90,
      ),
    ];
  }

  /// Preset Kusen Pintu (3 Batang: 2 Tiang & 1 Ambang Atas)
  static List<AssemblyPart> createKusenPintuPreset({
    double lebarMm = 900.0,
    double tinggiMm = 2100.0,
  }) {
    return [
      AssemblyPart(id: 'p_kiri', name: 'Tiang Pintu Kiri', lengthMm: tinggiMm, posX: 0, posY: 0, posZ: 0, rotPitch: 90),
      AssemblyPart(id: 'p_kanan', name: 'Tiang Pintu Kanan', lengthMm: tinggiMm, posX: lebarMm, posY: 0, posZ: 0, rotPitch: 90),
      AssemblyPart(id: 'p_atas', name: 'Ambang Atas', lengthMm: lebarMm, posX: 0, posY: tinggiMm, posZ: 0, rotYaw: 90),
    ];
  }

  /// Preset Rangka Meja / Rak Aluminium
  static List<AssemblyPart> createRangkaMejaPreset() {
    return [
      AssemblyPart(id: 'k1', name: 'Kaki Depan Kiri', lengthMm: 750, posX: 0, posY: 0, posZ: 0, rotPitch: 90),
      AssemblyPart(id: 'k2', name: 'Kaki Depan Kanan', lengthMm: 750, posX: 1000, posY: 0, posZ: 0, rotPitch: 90),
      AssemblyPart(id: 'k3', name: 'Kaki Belakang Kiri', lengthMm: 750, posX: 0, posY: 0, posZ: 600, rotPitch: 90),
      AssemblyPart(id: 'k4', name: 'Kaki Belakang Kanan', lengthMm: 750, posX: 1000, posY: 0, posZ: 600, rotPitch: 90),
      AssemblyPart(id: 'f1', name: 'Frame Atas Depan', lengthMm: 1000, posX: 0, posY: 750, posZ: 0, rotYaw: 90),
      AssemblyPart(id: 'f2', name: 'Frame Atas Belakang', lengthMm: 1000, posX: 0, posY: 750, posZ: 600, rotYaw: 90),
    ];
  }
}
