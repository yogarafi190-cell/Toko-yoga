package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.TransaksiDetailWithProduct
import com.example.data.local.TransaksiEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale

object ReportExporter {

    private val rupiahFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    /**
     * Mengekspor transaksi tunggal beserta detail pemotongannya menjadi berkas Spreadsheet Excel/CSV.
     * Menggunakan UTF-8 BOM (\uFEFF) agar Microsoft Excel otomatis membuka kolom secara rapi.
     */
    fun exportTransaksiToSpreadsheet(
        context: Context,
        transaksi: TransaksiEntity,
        details: List<TransaksiDetailWithProduct>
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "laporan_excel")
            if (!exportDir.exists()) exportDir.mkdirs()

            val fileName = "Nota_TokoYoga_TRX_${transaksi.id}_${transaksi.tanggal.replace(":", "-").replace(" ", "_")}.csv"
            val file = File(exportDir, fileName)

            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // UTF-8 BOM untuk Microsoft Excel Windows & Android compatibility
                    writer.write("\uFEFF")

                    // Header Nota
                    writer.write("TOKO YOGA - ALUMINIUM EKSTRUSI & WORKSHOP\n")
                    writer.write("NOTA PENJUALAN & PEMOTONGAN BAHAN (OFFLINE)\n")
                    writer.write("No. Transaksi;TRX-${transaksi.id}\n")
                    writer.write("Tanggal;${transaksi.tanggal}\n")
                    writer.write("Status Pembayaran;LUNAS (OFFLINE)\n\n")

                    // Tabel Rincian Barang
                    writer.write("No;Section No;Kategori;Warna;Ketebalan (mm);Panjang Diminta (mm);Jumlah (Batang);Harga /m;Subtotal\n")

                    var totalItemCount = 0
                    for ((index, item) in details.withIndex()) {
                        val prod = item.produk
                        val sectionNo = prod?.sectionNo ?: "Profil Kustom"
                        val kategori = prod?.kategori ?: "-"
                        val warna = prod?.warna ?: "-"
                        val thickness = prod?.thickness ?: 0.0
                        val panjangMm = item.detail.panjangDiminta
                        val qty = item.detail.jumlahBatang
                        val harga = prod?.hargaPerMeter ?: 0L
                        val subtotal = item.detail.subtotal
                        totalItemCount += qty

                        writer.write(
                            "${index + 1};\"$sectionNo\";\"$kategori\";\"$warna\";$thickness;$panjangMm;$qty;$harga;$subtotal\n"
                        )
                    }

                    writer.write("\n")
                    writer.write(";;;;;TOTAL BATANG;$totalItemCount;TOTAL HARGA;\"${rupiahFormat.format(transaksi.totalHarga)}\"\n")
                    writer.write("\n")
                    writer.write("Catatan: Simpan berkas nota ini sebagai bukti transaksi resmi Toko Yoga.\n")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Membuka Android Sharesheet agar berkas laporan dapat dibagikan secara offline
     * melalui Bluetooth lokal, WhatsApp, ataupun disimpan ke File Manager.
     */
    fun shareReportFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Nota Transaksi Toko Yoga - ${file.name}")
                putExtra(Intent.EXTRA_TEXT, "Berikut terlampir nota dan rekap pemotongan profil aluminium Toko Yoga.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Nota via Bluetooth / WhatsApp")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
