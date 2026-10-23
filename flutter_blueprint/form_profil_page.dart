// ==============================================================================
// TOKO YOGA - FORM UI INPUT & EDIT DYNAMIC (FLUTTER / DART)
// Mendukung mode Tambah Baru atau Edit Data Lama dengan validasi input ketat
// ==============================================================================

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'database_helper.dart';

class FormProfilPage extends StatefulWidget {
  final ProdukProfil? existingProduk; // Null jika Tambah Baru, terisi jika Edit

  const FormProfilPage({Key? key, this.existingProduk}) : super(key: key);

  @override
  State<FormProfilPage> createState() => _FormProfilPageState();
}

class _FormProfilPageState extends State<FormProfilPage> {
  final _formKey = GlobalKey<FormState>();

  late TextEditingController _sectionNoController;
  late TextEditingController _thicknessController;
  late TextEditingController _hargaController;

  final List<String> _kategoriList = [
    'Shop Front',
    'Sliding Door',
    'Casement Window',
    'Curtain Wall',
    'Commodities',
  ];

  final List<String> _warnaList = [
    'Silver',
    'Black',
    'Brown',
    'Putih',
  ];

  late String _selectedKategori;
  late String _selectedWarna;
  bool _isLoading = false;

  bool get isEditMode => widget.existingProduk != null;

  @override
  void initState() {
    super.initState();
    final p = widget.existingProduk;

    // Inisialisasi controller dengan data lama jika dalam mode Edit
    _sectionNoController = TextEditingController(text: p?.sectionNo ?? '');
    _thicknessController = TextEditingController(text: p != null ? p.thickness.toString() : '');
    _hargaController = TextEditingController(text: p != null ? p.hargaPerMeter.toString() : '');

    _selectedKategori = p != null && _kategoriList.contains(p.kategori) ? p.kategori : _kategoriList.first;
    _selectedWarna = p != null && _warnaList.contains(p.warna) ? p.warna : _warnaList.first;
  }

  @override
  void dispose() {
    _sectionNoController.dispose();
    _thicknessController.dispose();
    _hargaController.dispose();
    super.dispose();
  }

  Future<void> _simpanData() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _isLoading = true);

    try {
      final sectionNo = _sectionNoController.text.trim();
      final thickness = double.parse(_thicknessController.text.replaceAll(',', '.'));
      final hargaPerMeter = int.parse(_hargaController.text.replaceAll(RegExp(r'[^0-9]'), ''));

      if (isEditMode) {
        // Mode EDIT: Panggil fungsi update ke SQLite
        final updatedProduk = widget.existingProduk!.copyWith(
          sectionNo: sectionNo,
          kategori: _selectedKategori,
          thickness: thickness,
          hargaPerMeter: hargaPerMeter,
          warna: _selectedWarna,
        );

        await DatabaseHelper.instance.updateProduk(updatedProduk);

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Data profil berhasil diperbarui!'),
              backgroundColor: Colors.green,
            ),
          );
          Navigator.pop(context, true);
        }
      } else {
        // Mode TAMBAH BARU: Panggil fungsi insert ke SQLite
        final newProduk = ProdukProfil(
          sectionNo: sectionNo,
          kategori: _selectedKategori,
          thickness: thickness,
          hargaPerMeter: hargaPerMeter,
          warna: _selectedWarna,
          file3dPath: '',
        );

        await DatabaseHelper.instance.insertProduk(newProduk);

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Profil aluminium baru berhasil disimpan!'),
              backgroundColor: Colors.green,
            ),
          );
          Navigator.pop(context, true);
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Terjadi kesalahan: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(isEditMode ? 'Edit Profil Aluminium' : 'Tambah Profil Baru'),
        backgroundColor: const Color(0xFF1E293B),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(20.0),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Header Banner
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF1F5F9),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFCBD5E1)),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            isEditMode ? Icons.edit_note : Icons.add_box,
                            color: const Color(0xFFD97706),
                            size: 32,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  isEditMode ? 'Memperbarui ID: ${widget.existingProduk?.id}' : 'Input Spesifikasi Ekstrusi',
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                                ),
                                const Text(
                                  'Pastikan Section No dan ketebalan sesuai standar katalog toko.',
                                  style: TextStyle(fontSize: 12, color: Colors.black54),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),

                    // 1. TextField 'Section No'
                    TextFormField(
                      controller: _sectionNoController,
                      decoration: const InputDecoration(
                        labelText: 'Section No *',
                        hintText: 'Contoh: SF-001, SD-9055, Pipa 40x40',
                        prefixIcon: Icon(Icons.tag),
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Section No wajib diisi!';
                        }
                        if (value.trim().length < 2) {
                          return 'Section No minimal 2 karakter';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    // 2. Dropdown 'Kategori'
                    DropdownButtonFormField<String>(
                      value: _selectedKategori,
                      decoration: const InputDecoration(
                        labelText: 'Kategori Profil *',
                        prefixIcon: Icon(Icons.category),
                        border: OutlineInputBorder(),
                      ),
                      items: _kategoriList.map((String cat) {
                        return DropdownMenuItem<String>(
                          value: cat,
                          child: Text(cat),
                        );
                      }).toList(),
                      onChanged: (String? val) {
                        if (val != null) setState(() => _selectedKategori = val);
                      },
                    ),
                    const SizedBox(height: 16),

                    // 3. TextField 'Thickness (mm)' (Hanya angka desimal)
                    TextFormField(
                      controller: _thicknessController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: [
                        FilteringTextInputFormatter.allow(RegExp(r'^\d*[.,]?\d{0,2}')),
                      ],
                      decoration: const InputDecoration(
                        labelText: 'Thickness / Ketebalan (mm) *',
                        hintText: 'Misal: 1.25 atau 1.50',
                        prefixIcon: Icon(Icons.line_weight),
                        suffixText: 'mm',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Thickness wajib diisi!';
                        }
                        final parsed = double.tryParse(value.replaceAll(',', '.'));
                        if (parsed == null || parsed <= 0) {
                          return 'Masukkan angka ketebalan yang valid (> 0)';
                        }
                        if (parsed > 15.0) {
                          return 'Ketebalan profil aluminium maksimal 15 mm';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    // 4. TextField 'Harga per Meter' (Hanya angka)
                    TextFormField(
                      controller: _hargaController,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: const InputDecoration(
                        labelText: 'Harga per Meter (Rp) *',
                        hintText: 'Misal: 48000',
                        prefixIcon: Icon(Icons.attach_money),
                        prefixText: 'Rp ',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Harga per meter wajib diisi!';
                        }
                        final parsed = int.tryParse(value);
                        if (parsed == null || parsed <= 0) {
                          return 'Harga harus lebih dari 0';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    // 5. Dropdown 'Pilihan Warna'
                    DropdownButtonFormField<String>(
                      value: _selectedWarna,
                      decoration: const InputDecoration(
                        labelText: 'Pilihan Warna Ekstrusi *',
                        prefixIcon: Icon(Icons.palette),
                        border: OutlineInputBorder(),
                      ),
                      items: _warnaList.map((String color) {
                        return DropdownMenuItem<String>(
                          value: color,
                          child: Row(
                            children: [
                              Container(
                                width: 14,
                                height: 14,
                                margin: const EdgeInsets.only(right: 8),
                                decoration: BoxDecoration(
                                  color: _getColorPreview(color),
                                  shape: BoxShape.circle,
                                  border: Border.all(color: Colors.grey),
                                ),
                              ),
                              Text(color),
                            ],
                          ),
                        );
                      }).toList(),
                      onChanged: (String? val) {
                        if (val != null) setState(() => _selectedWarna = val);
                      },
                    ),
                    const SizedBox(height: 32),

                    // Tombol Simpan
                    ElevatedButton.icon(
                      onPressed: _simpanData,
                      icon: Icon(isEditMode ? Icons.save : Icons.add_circle),
                      label: Text(
                        isEditMode ? 'Simpan Perubahan (Update)' : 'Tambah ke Database',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFD97706),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedCornerShape(12),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  Color _getColorPreview(String warna) {
    switch (warna.toLowerCase()) {
      case 'black':
        return Colors.black87;
      case 'brown':
        return const Color(0xFF5D4037);
      case 'putih':
        return Colors.white;
      default:
        return const Color(0xFFB0BEC5); // Silver
    }
  }
}
