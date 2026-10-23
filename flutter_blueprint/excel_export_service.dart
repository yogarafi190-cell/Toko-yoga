// ==============================================================================
// TOKO YOGA - EKSPOR LAPORAN NOTA EXCEL OFFLINE (FLUTTER / DART)
// Mengekspor data transaksi lokal ke Spreadsheet Excel (.xlsx) / CSV
// Siap dibagikan 100% offline via WhatsApp lokal atau Bluetooth ke pelanggan.
// ==============================================================================

import 'dart:io';
import 'package:excel/excel.dart'; // package: excel/excel.dart
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart'; // package: share_plus/share_plus.dart
import 'database_helper.dart';

class ExcelExportService {
  /// Mengekspor Transaksi dan Detail Potongan ke file berkas Spreadsheet Excel (.xlsx)
  /// yang disimpan secara lokal di memori HP, lalu mengembalikan path file.
  static Future<String?> exportNotaTransaksiToExcel(int transaksiId) async {
    try {
      final db = DatabaseHelper.instance;

      // 1. Ambil data transaksi dari SQLite lokal
      final rawDetails = await db.getDetailTransaksiWithProduct(transaksiId);
      if (rawDetails.isEmpty) {
        throw Exception('Tidak ada detail transaksi untuk ID: $transaksiId');
      }

      final transaksiList = await db.getAllTransaksi();
      final transaksi = transaksiList.firstWhere(
        (t) => t.id == transaksiId,
        orElse: () => Transaksi(id: transaksiId, tanggal: 'Unknown', totalHarga: 0),
      );

      // 2. Buat Workbook Excel baru
      final excel = Excel.createExcel();
      final String sheetName = 'Nota_TRX_$transaksiId';
      final Sheet sheet = excel[sheetName];
      excel.setDefaultSheet(sheetName);

      // Style Header
      final headerStyle = CellStyle(
        bold: true,
        fontFamily: getFontFamily(FontFamily.Arial),
        backgroundColorHex: ExcelColor.fromHexString('#1E293B'),
        fontColorHex: ExcelColor.fromHexString('#FFFFFF'),
        horizontalAlign: HorizontalAlign.Center,
      );

      final titleStyle = CellStyle(
        bold: true,
        fontSize: 14,
        fontFamily: getFontFamily(FontFamily.Arial),
      );

      // Judul Nota
      sheet.cell(CellIndex.indexByString("A1"))
        ..value = TextCellValue("TOKO YOGA - ALUMINIUM EKSTRUSI & WORKSHOP")
        ..cellStyle = titleStyle;

      sheet.cell(CellIndex.indexByString("A2"))
        ..value = TextCellValue("Nota Pemotongan & Penjualan (100% Offline)");

      sheet.cell(CellIndex.indexByString("A3"))
        ..value = TextCellValue("No. Nota: TRX-$transaksiId | Tanggal: ${transaksi.tanggal}");

      // Baris Header Tabel (Row 5)
      final List<String> columns = [
        "No",
        "Section No",
        "Kategori",
        "Warna",
        "Tebal (mm)",
        "Panjang (mm)",
        "Jumlah (Btg)",
        "Harga/m (Rp)",
        "Subtotal (Rp)"
      ];

      for (int c = 0; c < columns.length; c++) {
        final cell = sheet.cell(CellIndex.indexByColumnRow(columnIndex: c, rowIndex: 4));
        cell.value = TextCellValue(columns[c]);
        cell.cellStyle = headerStyle;
      }

      // Baris Data Transaksi
      int rowIndex = 5;
      int totalBatang = 0;

      for (int i = 0; i < rawDetails.length; i++) {
        final row = rawDetails[i];
        final sectionNo = (row['section_no'] ?? 'Profil Kustom').toString();
        final kategori = (row['kategori'] ?? '-').toString();
        final warna = (row['warna'] ?? '-').toString();
        final thickness = (row['thickness'] ?? 0.0).toString();
        final double panjangMm = (row['panjang_diminta'] as num).toDouble();
        final int qty = row['jumlah_batang'] as int;
        final int harga = row['harga_per_meter'] as int;
        final int subtotal = row['subtotal'] as int;

        totalBatang += qty;

        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 0, rowIndex: rowIndex))
          ..value = IntCellValue(i + 1);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 1, rowIndex: rowIndex))
          ..value = TextCellValue(sectionNo);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 2, rowIndex: rowIndex))
          ..value = TextCellValue(kategori);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 3, rowIndex: rowIndex))
          ..value = TextCellValue(warna);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 4, rowIndex: rowIndex))
          ..value = TextCellValue(thickness);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 5, rowIndex: rowIndex))
          ..value = DoubleCellValue(panjangMm);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 6, rowIndex: rowIndex))
          ..value = IntCellValue(qty);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 7, rowIndex: rowIndex))
          ..value = IntCellValue(harga);
        sheet.cell(CellIndex.indexByColumnRow(columnIndex: 8, rowIndex: rowIndex))
          ..value = IntCellValue(subtotal);

        rowIndex++;
      }

      // Baris Total Grand Total
      rowIndex++;
      final totalLabelCell = sheet.cell(CellIndex.indexByColumnRow(columnIndex: 5, rowIndex: rowIndex))
        ..value = TextCellValue("TOTAL BATANG:")
        ..cellStyle = CellStyle(bold: true);

      sheet.cell(CellIndex.indexByColumnRow(columnIndex: 6, rowIndex: rowIndex))
        ..value = IntCellValue(totalBatang)
        ..cellStyle = CellStyle(bold: true);

      sheet.cell(CellIndex.indexByColumnRow(columnIndex: 7, rowIndex: rowIndex))
        ..value = TextCellValue("GRAND TOTAL:")
        ..cellStyle = CellStyle(bold: true);

      sheet.cell(CellIndex.indexByColumnRow(columnIndex: 8, rowIndex: rowIndex))
        ..value = IntCellValue(transaksi.totalHarga)
        ..cellStyle = CellStyle(bold: true, backgroundColorHex: ExcelColor.fromHexString('#FEF3C7'));

      // 3. Simpan File Excel .xlsx ke Memori HP Lokal
      final directory = await getApplicationDocumentsDirectory();
      final reportDir = Directory('${directory.path}/laporan_excel');
      if (!await reportDir.exists()) {
        await reportDir.create(recursive: true);
      }

      final String filePath = '${reportDir.path}/Nota_TokoYoga_TRX_$transaksiId.xlsx';
      final file = File(filePath);

      final fileBytes = excel.encode();
      if (fileBytes != null) {
        await file.writeAsBytes(fileBytes);
        return filePath;
      }
      return null;
    } catch (e) {
      print('Gagal export nota Excel: $e');
      return null;
    }
  }

  /// Membagikan berkas Excel ke WhatsApp atau Bluetooth secara offline
  static Future<void> shareNotaExcel({
    required String filePath,
    required String sectionInfo,
  }) async {
    final file = File(filePath);
    if (!await file.exists()) {
      print('File tidak ditemukan di path: $filePath');
      return;
    }

    // Menggunakan share_plus untuk membuka native Android Sharesheet
    await Share.shareXFiles(
      [XFile(filePath, mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')],
      subject: 'Nota Transaksi Toko Yoga ($sectionInfo)',
      text: 'Terlampir berkas nota pemotongan dan faktur aluminium Toko Yoga.',
    );
  }
}
