package com.example.cuttingstock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PaymentMethod
import com.example.data.local.ProdukProfilEntity
import com.example.data.local.Product
import com.example.data.local.TransaksiDetailEntity
import com.example.data.local.TransaksiEntity
import com.example.data.local.Transaction
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.repository.TokoYogaRepository
import com.example.mesh3d.AssemblyPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Representasi satu baris item pada struk kalkulasi kasir POS.
 */
data class ReceiptLineItem(
    val id: String = UUID.randomUUID().toString(),
    val sectionNumber: String,
    val name: String,
    val cutLengthMm: Double,
    val quantity: Int,
    val totalMeters: Double,
    val unitPricePerMeter: Long,
    val subtotal: Long
)

/**
 * Hasil kalkulasi lengkap nota kasir / receipt calculation.
 */
data class ReceiptCalculation(
    val invoiceNumber: String,
    val timestamp: Date,
    val customerName: String,
    val items: List<ReceiptLineItem>,
    val totalPieces: Int,
    val totalBarsNeeded: Int,
    val totalLengthMeters: Double,
    val cuttingEfficiencyPct: Double,
    val cuttingWasteMm: Double,
    val materialSubtotal: Long,
    val cuttingFeeTotal: Long,
    val discountAmount: Long,
    val taxRatePct: Double,
    val taxAmount: Long,
    val grandTotal: Long,
    val cashReceived: Long,
    val changeAmount: Long,
    val paymentMethod: PaymentMethod
)

/**
 * State UI untuk Cutting Stock Optimizer dan POS Kasir.
 */
data class CuttingStockUiState(
    val standardBarLengthMm: Double = 6000.0,
    val availableStockLengths: List<Double> = listOf(6000.0),
    val requestedPieces: List<CutRequest> = listOf(
        CutRequest(lengthMm = 2100.0, quantity = 2, label = "Tiang Kusen Pintu"),
        CutRequest(lengthMm = 900.0, quantity = 2, label = "Ambang Pintu")
    ),
    val sawKerfMm: Double = 3.0,
    val selectedProfile: ProdukProfilEntity? = null,
    val selectedProduct: Product? = null,
    val optimizationResult: CuttingOptimizationResult? = null,
    val customerName: String = "",
    val cuttingFeePerCut: Long = 0L,
    val discountAmount: Long = 0L,
    val taxRatePct: Double = 0.0,
    val cashReceived: Long = 0L,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val receiptCalculation: ReceiptCalculation? = null,
    val isOptimizing: Boolean = false,
    val isSavingTransaction: Boolean = false,
    val lastSavedTransactionId: Int? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel yang mengimplementasikan algoritma Cutting Stock Optimizer (Greedy Bin-Packing 1D)
 * serta mengelola kalkulasi struk belanja (receipt calculation) pada kasir POS.
 */
class CuttingStockViewModel(
    private val repository: TokoYogaRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuttingStockUiState())
    val uiState: StateFlow<CuttingStockUiState> = _uiState.asStateFlow()

    init {
        // Jalankan kalkulasi awal optimasi
        calculateOptimization()
    }

    /**
     * Algoritma Cutting Stock Optimizer menggunakan pendekatan Greedy Bin-Packing (Best-Fit Decreasing).
     * Memperhitungkan ketebalan mata gergaji (saw kerf compensation) dan meminimalkan sisa limbah aluminium.
     */
    fun calculateOptimization() {
        val currentState = _uiState.value
        val barLength = currentState.standardBarLengthMm.coerceAtLeast(100.0)
        val requests = currentState.requestedPieces
        val kerf = currentState.sawKerfMm.coerceAtLeast(0.0)

        // 1. Uraikan permintaan ke dalam daftar potongan tunggal
        val individualCuts = mutableListOf<Double>()
        for (req in requests) {
            val length = req.lengthMm.coerceAtLeast(1.0)
            val qty = req.quantity.coerceAtLeast(1)
            for (i in 0 until qty) {
                if (length <= barLength) {
                    individualCuts.add(length)
                }
            }
        }

        if (individualCuts.isEmpty()) {
            val emptyResult = CuttingOptimizationResult(
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
            _uiState.value = _uiState.value.copy(
                optimizationResult = emptyResult,
                receiptCalculation = recalculateReceipt(emptyResult)
            )
            return
        }

        // 2. Greedy Sorting: Urutkan potongan dari yang terpanjang ke terpendek (Decreasing)
        individualCuts.sortDescending()

        class WorkingBar(val barIndex: Int) {
            val cuts = mutableListOf<Double>()
            var currentLengthWithKerf = 0.0

            fun canFit(piece: Double): Boolean {
                val added = if (cuts.isEmpty()) piece else piece + kerf
                return (currentLengthWithKerf + added) <= barLength
            }

            fun addPiece(piece: Double) {
                val added = if (cuts.isEmpty()) piece else piece + kerf
                cuts.add(piece)
                currentLengthWithKerf += added
            }

            fun remainingSpace(): Double = barLength - currentLengthWithKerf
        }

        val openBars = mutableListOf<WorkingBar>()

        // 3. Greedy Best-Fit Allocation: pilih batangan yang sisa ruangnya paling mepet
        for (piece in individualCuts) {
            var bestBar: WorkingBar? = null
            var smallestRemainingAfterPiece = Double.MAX_VALUE

            for (bar in openBars) {
                if (bar.canFit(piece)) {
                    val additional = if (bar.cuts.isEmpty()) piece else piece + kerf
                    val spaceLeft = bar.remainingSpace() - additional
                    if (spaceLeft < smallestRemainingAfterPiece) {
                        smallestRemainingAfterPiece = spaceLeft
                        bestBar = bar
                    }
                }
            }

            if (bestBar != null) {
                bestBar.addPiece(piece)
            } else {
                // Buka batangan utuh baru
                val newBar = WorkingBar(openBars.size + 1)
                newBar.addPiece(piece)
                openBars.add(newBar)
            }
        }

        // 4. Susun alokasi batang & statistik efisiensi
        var totalRequestedLen = 0.0
        val allocations = mutableListOf<StockBarAllocation>()

        for (bar in openBars) {
            val sumCuts = bar.cuts.sum()
            totalRequestedLen += sumCuts
            val kerfLoss = if (bar.cuts.size > 1) (bar.cuts.size - 1) * kerf else 0.0
            val usedTotal = sumCuts + kerfLoss
            val waste = (barLength - usedTotal).coerceAtLeast(0.0)
            val efficiency = if (barLength > 0) (sumCuts / barLength) * 100.0 else 0.0

            allocations.add(
                StockBarAllocation(
                    barNumber = bar.barIndex,
                    cuts = bar.cuts,
                    usedLengthMm = usedTotal,
                    remainingWasteMm = waste,
                    efficiencyPct = efficiency,
                    kerfLossTotalMm = kerfLoss
                )
            )
        }

        val totalBars = openBars.size
        val totalMaterial = totalBars * barLength
        val totalWaste = (totalMaterial - totalRequestedLen).coerceAtLeast(0.0)
        val overallEfficiency = if (totalMaterial > 0) (totalRequestedLen / totalMaterial) * 100.0 else 0.0
        val overallWastePct = (100.0 - overallEfficiency).coerceAtLeast(0.0)

        val result = CuttingOptimizationResult(
            standardBarLengthMm = barLength,
            totalBarsNeeded = totalBars,
            totalPiecesCount = individualCuts.size,
            totalRequestedLengthMm = totalRequestedLen,
            totalMaterialConsumedMm = totalMaterial,
            totalWasteLengthMm = totalWaste,
            overallEfficiencyPct = overallEfficiency,
            overallWastePct = overallWastePct,
            barAllocations = allocations
        )

        val receipt = recalculateReceipt(result)

        _uiState.value = _uiState.value.copy(
            optimizationResult = result,
            receiptCalculation = receipt
        )
    }

    /**
     * Menghitung rincian struk kasir POS (receipt calculation) secara komprehensif.
     */
    private fun recalculateReceipt(optResult: CuttingOptimizationResult?): ReceiptCalculation {
        val state = _uiState.value
        val profile = state.selectedProfile
        val pricePerMeter = profile?.hargaPerMeter ?: 48000L
        val sectionName = profile?.sectionNo ?: "Profil Aluminium Standar"
        val profileName = profile?.kategori ?: "Kusen / Profil"

        val lineItems = state.requestedPieces.map { req ->
            val totalMeters = (req.lengthMm / 1000.0) * req.quantity
            val itemSubtotal = (totalMeters * pricePerMeter).toLong()
            ReceiptLineItem(
                sectionNumber = sectionName,
                name = if (req.label.isNotBlank()) req.label else "$profileName (${req.lengthMm.toInt()}mm)",
                cutLengthMm = req.lengthMm,
                quantity = req.quantity,
                totalMeters = totalMeters,
                unitPricePerMeter = pricePerMeter,
                subtotal = itemSubtotal
            )
        }

        val totalBars = optResult?.totalBarsNeeded ?: 0
        // Bahan batangan utuh yang dipakai pelanggan
        val materialSubtotal = (totalBars * (state.standardBarLengthMm / 1000.0) * pricePerMeter).toLong()

        val totalCutsCount = state.requestedPieces.sumOf { it.quantity }
        val cuttingFeeTotal = totalCutsCount * state.cuttingFeePerCut
        val taxableSubtotal = (materialSubtotal + cuttingFeeTotal - state.discountAmount).coerceAtLeast(0L)
        val taxAmount = (taxableSubtotal * (state.taxRatePct / 100.0)).toLong()
        val grandTotal = taxableSubtotal + taxAmount

        val cash = if (state.cashReceived > 0L) state.cashReceived else grandTotal
        val change = (cash - grandTotal).coerceAtLeast(0L)

        val invoiceId = "TRX-YOGA-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())}"

        return ReceiptCalculation(
            invoiceNumber = invoiceId,
            timestamp = Date(),
            customerName = state.customerName.ifBlank { "Pelanggan Toko" },
            items = lineItems,
            totalPieces = totalCutsCount,
            totalBarsNeeded = totalBars,
            totalLengthMeters = (optResult?.totalRequestedLengthMm ?: 0.0) / 1000.0,
            cuttingEfficiencyPct = optResult?.overallEfficiencyPct ?: 0.0,
            cuttingWasteMm = optResult?.totalWasteLengthMm ?: 0.0,
            materialSubtotal = materialSubtotal,
            cuttingFeeTotal = cuttingFeeTotal,
            discountAmount = state.discountAmount,
            taxRatePct = state.taxRatePct,
            taxAmount = taxAmount,
            grandTotal = grandTotal,
            cashReceived = cash,
            changeAmount = change,
            paymentMethod = state.paymentMethod
        )
    }

    // =========================================================================
    // AKSI PENGATURAN PROFIL & PERMINTAAN POTONGAN
    // =========================================================================

    fun selectProfile(profile: ProdukProfilEntity) {
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
        calculateOptimization()
    }

    fun selectProduct(product: Product) {
        _uiState.value = _uiState.value.copy(selectedProduct = product)
        calculateOptimization()
    }

    fun setStandardBarLength(lengthMm: Double) {
        _uiState.value = _uiState.value.copy(
            standardBarLengthMm = lengthMm,
            availableStockLengths = listOf(lengthMm)
        )
        calculateOptimization()
    }

    fun setSawKerf(kerfMm: Double) {
        _uiState.value = _uiState.value.copy(sawKerfMm = kerfMm)
        calculateOptimization()
    }

    fun addCutRequest(lengthMm: Double, quantity: Int = 1, label: String = "") {
        val newReq = CutRequest(
            lengthMm = lengthMm,
            quantity = quantity.coerceAtLeast(1),
            label = label
        )
        _uiState.value = _uiState.value.copy(
            requestedPieces = _uiState.value.requestedPieces + newReq
        )
        calculateOptimization()
    }

    fun removeCutRequest(id: String) {
        _uiState.value = _uiState.value.copy(
            requestedPieces = _uiState.value.requestedPieces.filterNot { it.id == id }
        )
        calculateOptimization()
    }

    fun updateCutQuantity(id: String, newQty: Int) {
        if (newQty <= 0) {
            removeCutRequest(id)
            return
        }
        _uiState.value = _uiState.value.copy(
            requestedPieces = _uiState.value.requestedPieces.map {
                if (it.id == id) it.copy(quantity = newQty) else it
            }
        )
        calculateOptimization()
    }

    fun clearAllCutRequests() {
        _uiState.value = _uiState.value.copy(requestedPieces = emptyList())
        calculateOptimization()
    }

    fun loadFromAssemblyParts(parts: List<AssemblyPart>) {
        if (parts.isEmpty()) return
        val cuts = parts.map { part ->
            CutRequest(
                lengthMm = part.lengthMm.toDouble(),
                quantity = 1,
                label = part.name
            )
        }
        _uiState.value = _uiState.value.copy(requestedPieces = cuts)
        calculateOptimization()
    }

    // =========================================================================
    // AKSI TRANSAKSI & PEMBAYARAN KASIR
    // =========================================================================

    fun setCustomerName(name: String) {
        _uiState.value = _uiState.value.copy(customerName = name)
        updateReceiptState()
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(paymentMethod = method)
        updateReceiptState()
    }

    fun setCashReceived(amount: Long) {
        _uiState.value = _uiState.value.copy(cashReceived = amount)
        updateReceiptState()
    }

    fun setCuttingFeePerCut(fee: Long) {
        _uiState.value = _uiState.value.copy(cuttingFeePerCut = fee)
        updateReceiptState()
    }

    fun setDiscountAmount(amount: Long) {
        _uiState.value = _uiState.value.copy(discountAmount = amount)
        updateReceiptState()
    }

    fun setTaxRate(pct: Double) {
        _uiState.value = _uiState.value.copy(taxRatePct = pct)
        updateReceiptState()
    }

    private fun updateReceiptState() {
        val currentOpt = _uiState.value.optimizationResult
        _uiState.value = _uiState.value.copy(receiptCalculation = recalculateReceipt(currentOpt))
    }

    /**
     * Menyimpan transaksi kasir ke dalam Room Database (tabel transaksi, transaksi_detail, dan transactions).
     */
    fun saveTransaction(onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        val repo = repository ?: run {
            onError("Repository database tidak tersedia")
            return
        }

        val state = _uiState.value
        val receipt = state.receiptCalculation ?: run {
            onError("Kalkulasi struk belum tersedia")
            return
        }

        if (state.requestedPieces.isEmpty()) {
            onError("Antrean potong masih kosong")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSavingTransaction = true)
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                // 1. Simpan ke tabel TransaksiEntity legacy
                val trxEntity = TransaksiEntity(
                    tanggal = nowStr,
                    totalHarga = receipt.grandTotal
                )

                val details = state.requestedPieces.map { req ->
                    val meters = (req.lengthMm / 1000.0) * req.quantity
                    val price = state.selectedProfile?.hargaPerMeter ?: 48000L
                    TransaksiDetailEntity(
                        transaksiId = 0,
                        produkId = state.selectedProfile?.id ?: 1,
                        panjangDiminta = req.lengthMm,
                        jumlahBatang = req.quantity,
                        subtotal = (meters * price).toLong()
                    )
                }

                val savedId = repo.simpanTransaksi(trxEntity, details)

                // 2. Simpan juga ke entitas modern Transaction (New Room schema)
                val newTransaction = Transaction(
                    transactionCode = receipt.invoiceNumber,
                    type = TransactionType.SALE,
                    totalAmount = receipt.grandTotal,
                    paymentMethod = receipt.paymentMethod,
                    customerName = receipt.customerName,
                    notes = "Total ${receipt.totalBarsNeeded} batang profil, efisiensi ${String.format(Locale.US, "%.1f", receipt.cuttingEfficiencyPct)}%",
                    status = TransactionStatus.COMPLETED,
                    timestamp = Date()
                )
                repo.insertTransaction(newTransaction)

                _uiState.value = _uiState.value.copy(
                    isSavingTransaction = false,
                    lastSavedTransactionId = savedId.toInt()
                )

                onSuccess(savedId.toInt())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingTransaction = false,
                    errorMessage = e.message
                )
                onError(e.localizedMessage ?: "Gagal menyimpan transaksi")
            }
        }
    }
}
