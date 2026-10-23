package com.example.cuttingstock

import java.util.UUID

data class CutRequest(
    val id: String = UUID.randomUUID().toString(),
    val lengthMm: Double,
    val quantity: Int,
    val label: String = ""
)

data class StockBarAllocation(
    val barNumber: Int,
    val cuts: List<Double>,
    val usedLengthMm: Double,
    val remainingWasteMm: Double,
    val efficiencyPct: Double,
    val kerfLossTotalMm: Double
)

data class CuttingOptimizationResult(
    val standardBarLengthMm: Double,
    val totalBarsNeeded: Int,
    val totalPiecesCount: Int,
    val totalRequestedLengthMm: Double,
    val totalMaterialConsumedMm: Double,
    val totalWasteLengthMm: Double,
    val overallEfficiencyPct: Double,
    val overallWastePct: Double,
    val barAllocations: List<StockBarAllocation>
)

object CuttingStockOptimizer {

    /**
     * Menghitung optimasi pemotongan profil aluminium batangan utuh menggunakan
     * algoritma Best-Fit Decreasing (1D Bin Packing) dengan kompensasi mata pisau (kerf).
     *
     * @param standardBarLengthMm Panjang standar 1 batang utuh dari pabrik (misal 6.000 mm).
     * @param requests Daftar potongan yang diminta pelanggan beserta kuantitasnya.
     * @param sawKerfMm Ketebalan mata gergaji/pisau potong (standar 3.0 mm).
     */
    fun optimize(
        standardBarLengthMm: Double = 6000.0,
        requests: List<CutRequest>,
        sawKerfMm: Double = 3.0
    ): CuttingOptimizationResult {
        val barLength = standardBarLengthMm.coerceAtLeast(100.0)

        // 1. Ekstrak seluruh item permintaan menjadi list potongan individu
        val individualPieces = mutableListOf<Double>()
        for (req in requests) {
            val validLen = req.lengthMm.coerceAtLeast(1.0)
            val validQty = req.quantity.coerceAtLeast(1)
            for (i in 0 until validQty) {
                if (validLen <= barLength) {
                    individualPieces.add(validLen)
                }
            }
        }

        if (individualPieces.isEmpty()) {
            return CuttingOptimizationResult(
                standardBarLengthMm = barLength,
                totalBarsNeeded = 0,
                totalPiecesCount = 0,
                totalRequestedLengthMm = 0.0,
                totalMaterialConsumedMm = 0.0,
                totalWasteLengthMm = 0.0,
                overallEfficiencyPct = 0.0,
                overallWastePct = 0.0,
                barAllocations = emptyList()
            )
        }

        // 2. Urutkan potongan dari yang terpanjang ke yang terpendek (Decreasing)
        individualPieces.sortDescending()

        class OpenBar(val barIndex: Int) {
            val assignedCuts = mutableListOf<Double>()
            var currentUsed = 0.0

            fun canFit(piece: Double): Boolean {
                val needed = if (assignedCuts.isEmpty()) piece else piece + sawKerfMm
                return (currentUsed + needed) <= barLength
            }

            fun addPiece(piece: Double) {
                val added = if (assignedCuts.isEmpty()) piece else piece + sawKerfMm
                assignedCuts.add(piece)
                currentUsed += added
            }

            fun remainingSpace(): Double = barLength - currentUsed
        }

        val bars = mutableListOf<OpenBar>()

        // 3. Alokasikan dengan Best-Fit: pilih bar dengan sisa ruang paling ketat yang masih muat
        for (piece in individualPieces) {
            var bestBar: OpenBar? = null
            var minRemainingSpace = Double.MAX_VALUE

            for (bar in bars) {
                if (bar.canFit(piece)) {
                    val rem = bar.remainingSpace() - (if (bar.assignedCuts.isEmpty()) piece else piece + sawKerfMm)
                    if (rem < minRemainingSpace) {
                        minRemainingSpace = rem
                        bestBar = bar
                    }
                }
            }

            if (bestBar != null) {
                bestBar.addPiece(piece)
            } else {
                // Buka batangan utuh baru
                val newBar = OpenBar(bars.size + 1)
                newBar.addPiece(piece)
                bars.add(newBar)
            }
        }

        // 4. Hitung statistik dan susun hasil alokasi per batang
        var totalRequestedLen = 0.0
        val allocations = mutableListOf<StockBarAllocation>()

        for (bar in bars) {
            val sumCuts = bar.assignedCuts.sum()
            totalRequestedLen += sumCuts
            val kerfLoss = if (bar.assignedCuts.size > 1) (bar.assignedCuts.size - 1) * sawKerfMm else 0.0
            val usedTotal = sumCuts + kerfLoss
            val waste = (barLength - usedTotal).coerceAtLeast(0.0)
            val effPct = (sumCuts / barLength) * 100.0

            allocations.add(
                StockBarAllocation(
                    barNumber = bar.barIndex,
                    cuts = bar.assignedCuts,
                    usedLengthMm = usedTotal,
                    remainingWasteMm = waste,
                    efficiencyPct = effPct,
                    kerfLossTotalMm = kerfLoss
                )
            )
        }

        val totalBars = bars.size
        val totalMaterial = totalBars * barLength
        val totalWaste = (totalMaterial - totalRequestedLen).coerceAtLeast(0.0)
        val overallEff = if (totalMaterial > 0) (totalRequestedLen / totalMaterial) * 100.0 else 0.0
        val overallWastePct = (100.0 - overallEff).coerceAtLeast(0.0)

        return CuttingOptimizationResult(
            standardBarLengthMm = barLength,
            totalBarsNeeded = totalBars,
            totalPiecesCount = individualPieces.size,
            totalRequestedLengthMm = totalRequestedLen,
            totalMaterialConsumedMm = totalMaterial,
            totalWasteLengthMm = totalWaste,
            overallEfficiencyPct = overallEff,
            overallWastePct = overallWastePct,
            barAllocations = allocations
        )
    }
}
