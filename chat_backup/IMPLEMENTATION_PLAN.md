# Toko Yoga - Sistem Manajemen Profil Ekstrusi Aluminium 3D, POS & Optimasi Potongan

Aplikasi toko aluminium ekstrusi 100% offline yang menggabungkan generator geometri 3D hollow extrusion, manajemen inventaris profil, kalkulator efisiensi pemotongan bahan (cutting stock 1D bin packing), sistem kasir POS, ekspor laporan offline, serta penyediaan blueprint modular dalam arsitektur Flutter (Dart).

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Keputusan Arsitektur Terkonfirmasi:**
> 1. **Dua Output Utama**: Membangun aplikasi Android interaktif yang dapat langsung dioperasikan pada emulator Android (Kotlin + Jetpack Compose + Room SQLite + 3D Canvas Renderer + Spreadsheet Exporter) **DAN** menyediakan file blueprint arsitektur Flutter (Dart) lengkap dan terstruktur rapi untuk 5 modul utama yang diminta.
> 2. **Algoritma 3D Hollow Extrusion**: Geometri penampang berongga dihasilkan dari poligon jalur 2D dengan *inward normal offset* otomatis berjarak `thickness`, lalu diekstrusi sepanjang sumbu Z (`panjang_diminta`). Menghasilkan vertex, normal, dan triangulasi index untuk outer wall, inner wall, serta ring cap (ujung depan & belakang) yang diekspor menjadi file `.obj`.
> 3. **Prioritas Tampilan Utama**: Dashboard Kasir POS dengan kalkulator optimasi potongan bahan (Cutting Stock Logic) sebagai beranda default, dilengkapi navigasi praktis ke Katalog/Inventaris Profil, Form Input/Edit Dinamis, Kanvas Sketsa 2D & Viewer 3D, serta Riwayat Transaksi & Ekspor Laporan.

---

### 1. Overview & Core Concept

- **Apa yang Dibangun**: Sistem operasional toko aluminium ekstrusi bernama **Toko Yoga** yang berjalan 100% offline tanpa ketergantungan server/internet.
- **Target Pengguna**: Pemilik dan staf toko aluminium, aplikator kusen/pintu/jendela aluminium, dan teknisi workshop etalase/arsitektur.
- **Nilai Utama**:
  - Mengeliminasi limbah potongan aluminium (*scrap waste*) melalui perhitungan matematis pemotongan batangan (standar 6.000 mm).
  - Visualisasi 3D penampang berongga (*hollow extrusion*) langsung dari sketsa koordinat atau preset profil toko (Shop Front, Sliding Door, Casement Window, Curtain Wall, Commodities).
  - Kasir POS cepat dengan pencatatan nota dan ekspor laporan berkas offline yang siap dibagikan via Bluetooth atau WhatsApp.

---

### 2. User Experience & Visual Design

- **Arah Visual & Tema**: Industrial Precision & Architectural Modernism. Menggunakan palet aluminium anodized (Deep Slate Grey `#1E252B`, Anodized Silver `#E2E8F0`, Industrial Amber `#D97706` untuk highlight aksi utama, dan Precision Emerald `#059669` untuk status efisiensi bahan).
- **Struktur Navigasi & Layar Utama**:
  1. **POS & Cutting Stock Optimizer (Beranda)**:
     - Input panjang batang standar (default 6.000 mm).
     - Tambah daftar potongan pesanan pelanggan (panjang mm & kuantitas).
     - Tampilan visual diagram batangan terpotong (bar visualizer interaktif), sisa limbah (*kerf / offcut scrap*), dan rekomendasi total batang utuh yang dibutuhkan.
     - Simpan pesanan langsung ke Keranjang POS dan catat transaksi offline.
  2. **Katalog Profil & Form Input/Edit Dinamis (`FormProfilPage`)**:
     - Daftar katalog profil dengan filter kategori (*Shop Front, Sliding Door, Casement Window, Curtain Wall, Commodities*).
     - Badge warna (*Silver, Black, Brown, Putih*), nomor seksi (*Section No*), dan ketebalan (*thickness*).
     - Form dinamis untuk mode Tambah Baru atau Edit Data Lama dengan validasi form ketat.
  3. **Studio 2D Sketsa & Viewer 3D Hollow Extrusion**:
     - Kanvas grid interaktif untuk menggambar titik koordinat profil penampang (Point2D).
     - Preview 3D interaktif real-time dengan kontrol rotasi (orbit), zoom, toggle wireframe/solid, dan warna anodized aluminium.
     - Generator file 3D `.obj` yang disimpan ke penyimpanan lokal dan mengembalikan path file.
  4. **Riwayat Transaksi & Ekspor Laporan**:
     - Riwayat nota kasir offline beserta detail potongan.
     - Ekspor laporan spreadsheet offline (format tabel Excel `.csv` / `.xlsx` terstruktur) yang bisa langsung dibuka atau dibagikan melalui Android Sharesheet (WhatsApp/Bluetooth/File Manager).
  5. **Dokumentasi Blueprint Flutter (Dart)**:
     - Menu khusus dalam aplikasi untuk membaca dan menyalin kode referensi arsitektur Flutter 5 komponen yang siap diimplementasikan di framework Flutter.

---

### 3. Key Product Decisions & Trade-Offs

- **Database Engine (Room SQLite)**:
  - *Pendekatan*: Menggunakan Android Room SQLite (sesuai spesifikasi 100% offline) dengan schema relational tabel `produk_profil`, `transaksi`, dan `transaksi_detail`.
  - *Alasan*: Performa tinggi, relasi foreign key aman, reaktif dengan Kotlin Coroutines Flow, dan tidak membutuhkan koneksi cloud.
- **Geometri 3D Hollow (Linear Inset Extrusion)**:
  - *Pendekatan*: Menghitung vektor normal setiap segmen garis `P[i] -> P[i+1]`, membuat inward offset vertex sebesar `thickness` untuk membuat dinding dalam (*inner loop*), lalu mengekstrusi titik luar dan titik dalam sepanjang sumbu Z.
  - *Alasan*: Memastikan profil hollow memiliki rongga tengah yang nyata (berdinding), bukan balok pejal (*solid box*), menghasilkan mesh format standard Wavefront OBJ.
- **Algoritma Pemotongan Bahan (1D Cutting Stock / Best-Fit Decreasing)**:
  - *Pendekatan*: Mengurutkan daftar ukuran potongan dari yang terbesar (*decreasing*), lalu mengalokasikan ke batangan utuh yang memiliki sisa ruang paling ketat (*best-fit*) atau membuat batangan baru jika tidak muat. Memperhitungkan margin mata pisau potong (*saw blade kerf* misal 3 mm).
  - *Alasan*: Menghasilkan minimalisasi jumlah batang utuh tercepat dan memberikan transparansi sisa bahan kepada pemotong workshop.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Toko Yoga UI                                  │
├──────────────┬──────────────────┬─────────────────┬─────────────────────────┤
│ POS & Potong │  Form Dinamis    │  2D/3D Studio   │ Riwayat & Ekspor Laporan│
│ (Cutting     │  Profil          │  (Hollow Mesh   │ (Spreadsheet Exporter / │
│  Stock)      │  (Tambah/Edit)   │   OBJ Gen)      │  Bluetooth / WA Share)  │
└──────┬───────┴────────┬─────────┴────────┬────────┴────────────┬────────────┘
       │                │                  │                     │
       ▼                ▼                  ▼                     │
┌──────────────┐ ┌──────────────┐ ┌─────────────────┐            │
│ CuttingStock │ │ Profil       │ │ MeshExtruder3D  │            │
│ Calculator   │ │ Repository   │ │ (.obj exporter) │            │
└──────┬───────┘ └──────┬───────┘ └────────┬────────┘            │
       │                │                  │                     │
       ▼                ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                 Room Database (SQLite Local Offline)                        │
│   - produk_profil (id, section_no, kategori, thickness, harga, warna, path) │
│   - transaksi (id, tanggal, total_harga)                                   │
│   - transaksi_detail (id, transaksi_id, produk_id, panjang, qty, subtotal)  │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Blueprint Flutter (Dart) yang Disediakan:
1. `DatabaseHelper.dart` (sqflite CRUD lengkap untuk 3 tabel dengan update logic).
2. `hollow_extruder.dart` (Point2D normal offset calculation, vertex/index mapping, OBJ file generator).
3. `form_profil_page.dart` (StatefulWidget mode Create & Edit dengan validator form lengkap).
4. `cutting_stock_optimizer.dart` (1D bin packing algorithm, penghitung batang utuh & limbah).
5. `excel_export_service.dart` (pembangkit spreadsheet excel offline untuk sharing nota).
