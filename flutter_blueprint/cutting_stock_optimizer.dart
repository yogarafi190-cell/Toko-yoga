// ==============================================================================
// TOKO YOGA - KALKULATOR OPTIMASI POTONGAN BAHAN (FLUTTER / DART)
// 1D Cutting Stock Problem Solver (Best-Fit Decreasing) dengan Kerf Mata Pisau
// ==============================================================================

class CutItem {
  final double lengthMm; // Ukuran panjang potongan diminta (mm)
  final int quantity; // Jumlah batang potongan
  final String label;

  CutItem({
    required this.lengthMm,
    required this.quantity,
    this.label = '',
  });
}

class BarAllocation {
  final int barNumber;
  final List<double> cuts; // Daftar potongan dalam batang ini (mm)
  final double usedLengthMm;
  final double remainingWasteMm; // Sisa limbah potongan (mm)
  final double efficiencyPct;

  BarAllocation({
    required this.barNumber,
    required this.cuts,
    required this.usedLengthMm,
    required this.remainingWasteMm,
    required this.efficiencyPct,
  });
}

class CuttingOptimizationResult {
  final double standardBarLengthMm; // Misal 6000 mm
  final int totalBarsNeeded; // Jumlah batang utuh yang wajib dipotong
  final int totalPiecesCount;
  final double totalRequestedLengthMm;
  final double totalMaterialConsumedMm;
  final double totalWasteLengthMm; // Total limbah bahan (mm)
  final double overallEfficiencyPct; // % efisiensi material
  final double overallWastePct; // % limbah potongan
  final List<BarAllocation> barAllocations; // Pola potong tiap batang

  CuttingOptimizationResult({
    required this.standardBarLengthMm,
    required this.totalBarsNeeded,
    required this.totalPiecesCount,
    required this.totalRequestedLengthMm,
    required this.totalMaterialConsumedMm,
    required this.totalWasteLengthMm,
    required this.overallEfficiencyPct,
    required this.overallWastePct,
    required this.barAllocations,
  });
}

class CuttingStockOptimizer {
  /// Menghitung efisiensi pemotongan batangan utuh toko aluminium
  ///
  /// [standardBarLengthMm] : Panjang standar 1 batang utuh (default 6.000 mm)
  /// [items] : List ukuran potongan yang diminta pelanggan beserta kuantitasnya
  /// [sawKerfMm] : Lebar mata pisau gergaji (default 3.0 mm per pemotongan)
  static CuttingOptimizationResult optimize({
    double standardBarLengthMm = 6000.0,
    required List<CutItem> items,
    double sawKerfMm = 3.0,
  }) {
    final double barLen = standardBarLengthMm > 0 ? standardBarLengthMm : 6000.0;

    // 1. Urai seluruh permintaan ke dalam list potongan individual
    final List<double> pieces = [];
    for (var item in items) {
      final double len = item.lengthMm;
      if (len > 0 && len <= barLen) {
        for (int i = 0; i < item.quantity; i++) {
          pieces.add(len);
        }
      }
    }

    if (pieces.isEmpty) {
      return CuttingOptimizationResult(
        standardBarLengthMm: barLen,
        totalBarsNeeded: 0,
        totalPiecesCount: 0,
        totalRequestedLengthMm: 0,
        totalMaterialConsumedMm: 0,
        totalWasteLengthMm: 0,
        overallEfficiencyPct: 0,
        overallWastePct: 0,
        barAllocations: [],
      );
    }

    // 2. Sort Decreasing (Potongan terpanjang diprioritaskan terlebih dahulu)
    pieces.sort((a, b) => b.compareTo(a));

    // Struktur bar terbuka saat kalkulasi
    final List<_OpenBar> openBars = [];

    // 3. Algoritma Best-Fit: Cari batangan yang memiliki sisa ruang paling mepet
    for (var piece in pieces) {
      _OpenBar? bestBar;
      double minRemaining = double.infinity;

      for (var bar in openBars) {
        if (bar.canFit(piece, barLen, sawKerfMm)) {
          final remainingAfter = bar.remainingSpace(barLen, sawKerfMm) - piece;
          if (remainingAfter < minRemaining) {
            minRemaining = remainingAfter;
            bestBar = bar;
          }
        }
      }

      if (bestBar != null) {
        bestBar.addPiece(piece, sawKerfMm);
      } else {
        // Tidak muat di batang mana pun, buka batangan utuh baru
        final newBar = _OpenBar(openBars.length + 1);
        newBar.addPiece(piece, sawKerfMm);
        openBars.add(newBar);
      }
    }

    // 4. Hitung hasil rekapitulasi limbah & efisiensi
    double totalRequestedLen = 0;
    final List<BarAllocation> allocations = [];

    for (var bar in openBars) {
      final sumCuts = bar.cuts.fold<double>(0, (prev, elem) => prev + elem);
      totalRequestedLen += sumCuts;

      final kerfLoss = bar.cuts.length > 1 ? (bar.cuts.length - 1) * sawKerfMm : 0.0;
      final usedTotal = sumCuts + kerfLoss;
      final waste = (barLen - usedTotal).clamp(0.0, barLen);
      final eff = (sumCuts / barLen) * 100.0;

      allocations.add(
        BarAllocation(
          barNumber: bar.index,
          cuts: bar.cuts,
          usedLengthMm: usedTotal,
          remainingWasteMm: waste,
          efficiencyPct: eff,
        ),
      );
    }

    final int totalBars = openBars.length;
    final double totalMaterial = totalBars * barLen;
    final double totalWaste = (totalMaterial - totalRequestedLen).clamp(0.0, totalMaterial);
    final double overallEff = totalMaterial > 0 ? (totalRequestedLen / totalMaterial) * 100.0 : 0.0;
    final double overallWastePct = (100.0 - overallEff).clamp(0.0, 100.0);

    return CuttingOptimizationResult(
      standardBarLengthMm: barLen,
      totalBarsNeeded: totalBars,
      totalPiecesCount: pieces.length,
      totalRequestedLengthMm: totalRequestedLen,
      totalMaterialConsumedMm: totalMaterial,
      totalWasteLengthMm: totalWaste,
      overallEfficiencyPct: overallEff,
      overallWastePct: overallWastePct,
      barAllocations: allocations,
    );
  }
}

class _OpenBar {
  final int index;
  final List<double> cuts = [];
  double currentUsed = 0.0;

  _OpenBar(this.index);

  bool canFit(double piece, double barCapacity, double sawKerf) {
    final needed = cuts.isEmpty ? piece : piece + sawKerf;
    return (currentUsed + needed) <= barCapacity;
  }

  void addPiece(double piece, double sawKerf) {
    final needed = cuts.isEmpty ? piece : piece + sawKerf;
    cuts.add(piece);
    currentUsed += needed;
  }

  double remainingSpace(double barCapacity, double sawKerf) {
    return barCapacity - currentUsed;
  }
}
