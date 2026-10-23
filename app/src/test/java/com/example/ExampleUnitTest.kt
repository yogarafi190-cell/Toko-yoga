package com.example

import com.example.cuttingstock.CutRequest
import com.example.cuttingstock.CuttingStockOptimizer
import com.example.mesh3d.HollowExtruder
import com.example.mesh3d.Point2D
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCuttingStockOptimizer_CalculatesBarsAndWasteAccurately() {
        val requests = listOf(
            CutRequest(lengthMm = 2100.0, quantity = 2, label = "Tiang Kusen Pintu"),
            CutRequest(lengthMm = 900.0, quantity = 2, label = "Ambang Pintu")
        )

        // Total potongan: 2x2100 + 2x900 = 6000 mm
        // Dengan saw kerf 3mm per potongan, 2100+2100+900 = 5100 (+6mm kerf) muat di 1 batang 6000mm
        // Sisa 900mm akan dialokasikan ke batang ke-2
        val result = CuttingStockOptimizer.optimize(
            standardBarLengthMm = 6000.0,
            requests = requests,
            sawKerfMm = 3.0
        )

        assertEquals(2, result.totalBarsNeeded)
        assertEquals(4, result.totalPiecesCount)
        assertEquals(6000.0, result.totalRequestedLengthMm, 0.01)
        assertTrue(result.overallEfficiencyPct > 45.0)
        assertTrue(result.totalWasteLengthMm > 0.0)
        assertEquals(2, result.barAllocations.size)
    }

    @Test
    fun testWasteCalculation_IdentifiesReusableOffcutsAndScrap() {
        val requests = listOf(
            CutRequest(lengthMm = 5200.0, quantity = 1, label = "Batang Panjang"),
            CutRequest(lengthMm = 1500.0, quantity = 1, label = "Kusen Sedang")
        )

        val result = CuttingStockOptimizer.optimize(
            standardBarLengthMm = 6000.0,
            requests = requests,
            sawKerfMm = 3.0
        )

        assertEquals(2, result.totalBarsNeeded)
        val reusableCount = result.barAllocations.count { it.remainingWasteMm >= 500.0 }
        assertTrue(reusableCount >= 1)
        assertTrue(result.totalWasteLengthMm > 0)
    }

    @Test
    fun testHollowExtruder_GeneratesValidMeshVerticesAndIndices() {
        val boxPoints = listOf(
            Point2D(0f, 0f),
            Point2D(50f, 0f),
            Point2D(50f, 50f),
            Point2D(0f, 50f)
        )

        val mesh = HollowExtruder.generateHollowMesh(
            contour = boxPoints,
            thickness = 1.5,
            panjangBatang = 1000.0,
            isClosed = true
        )

        // 4 titik luar depan, 4 titik dalam depan, 4 titik luar belakang, 4 titik dalam belakang = 16 vertex
        assertEquals(16, mesh.vertices.size)
        assertTrue(mesh.triangles.isNotEmpty())
        assertTrue(mesh.indices.isNotEmpty())
        assertEquals(mesh.triangles.size * 3, mesh.indices.size)
    }

    @Test
    fun testAssemblyEngine_BuildsMultiPartKusenMesh() {
        val kusenParts = com.example.mesh3d.AssemblyEngine.createKusenJendelaPreset(
            lebarMm = 800f,
            tinggiMm = 1200f
        )

        assertEquals(4, kusenParts.size)
        val scene = com.example.mesh3d.AssemblyEngine.buildAssemblyMesh(kusenParts)

        assertEquals(4, scene.totalPieces)
        assertEquals(4000.0, scene.totalMaterialLengthMm, 0.01)
        assertTrue(scene.totalVerticesCount > 50)
        assertTrue(scene.triangles.isNotEmpty())
    }

    @Test
    fun testAssemblyHistoryManager_UndoRedoCoordinatesAndAssemblyChanges() {
        val manager = com.example.mesh3d.AssemblyHistoryManager(maxHistorySize = 10)
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)

        val part1 = com.example.mesh3d.AssemblyPart(
            id = "part_1",
            name = "Tiang Utama",
            lengthMm = 1200f,
            posX = 0f,
            posY = 0f,
            posZ = 0f
        )
        val initialParts = listOf(part1)

        // Step 1: User moves part1 X coordinate from 0 to 100
        manager.push(initialParts, part1.id, "Geser Pos X (+100 mm)")
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
        assertEquals(1, manager.undoCount)
        assertEquals("Geser Pos X (+100 mm)", manager.peekUndoLabel())

        val movedParts = listOf(part1.copy(posX = 100f))

        // Step 2: User adds part2
        manager.push(movedParts, part1.id, "Tambah Ambang Atas")
        val part2 = com.example.mesh3d.AssemblyPart(
            id = "part_2",
            name = "Ambang Atas",
            lengthMm = 800f,
            posX = 0f,
            posY = 1200f,
            posZ = 0f
        )
        val twoParts = movedParts + part2
        assertEquals(2, manager.undoCount)

        // Undo Step 2 (revert addition of part2)
        val undoResult1 = manager.undo(twoParts, part2.id)
        assertNotNull(undoResult1)
        assertEquals(1, undoResult1!!.parts.size)
        assertEquals(100f, undoResult1.parts[0].posX, 0.01f)
        assertTrue(manager.canRedo)
        assertEquals(1, manager.redoCount)

        // Undo Step 1 (revert posX movement back to 0)
        val undoResult2 = manager.undo(undoResult1.parts, part1.id)
        assertNotNull(undoResult2)
        assertEquals(0f, undoResult2!!.parts[0].posX, 0.01f)
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)
        assertEquals(2, manager.redoCount)

        // Redo Step 1 (re-apply posX movement to 100)
        val redoResult1 = manager.redo(undoResult2.parts, part1.id)
        assertNotNull(redoResult1)
        assertEquals(100f, redoResult1!!.parts[0].posX, 0.01f)
        assertTrue(manager.canUndo)
        assertTrue(manager.canRedo)

        // Redo Step 2 (re-apply addition of part2)
        val redoResult2 = manager.redo(redoResult1.parts, part1.id)
        assertNotNull(redoResult2)
        assertEquals(2, redoResult2!!.parts.size)
        assertFalse(manager.canRedo)
    }

    @Test
    fun testAssemblyHistoryManager_PrunesWhenExceedingMaxHistory() {
        val manager = com.example.mesh3d.AssemblyHistoryManager(maxHistorySize = 3)
        val part = com.example.mesh3d.AssemblyPart(id = "p1", name = "Test", lengthMm = 100f)

        for (i in 1..5) {
            manager.push(listOf(part.copy(posX = i * 10f)), "p1", "Aksi $i")
        }

        assertEquals(3, manager.undoCount)
        assertEquals("Aksi 5", manager.peekUndoLabel())
    }

    @Test
    fun testInventoryFilter_BySectionNumberAndCategory() {
        val inventory = listOf(
            com.example.data.local.ProdukProfilEntity(
                id = 1,
                sectionNo = "SF-301",
                kategori = "Shop Front",
                warna = "Silver",
                thickness = 1.2,
                hargaPerMeter = 45000
            ),
            com.example.data.local.ProdukProfilEntity(
                id = 2,
                sectionNo = "SD-901",
                kategori = "Sliding Door",
                warna = "Hitam",
                thickness = 1.4,
                hargaPerMeter = 55000
            ),
            com.example.data.local.ProdukProfilEntity(
                id = 3,
                sectionNo = "CW-100",
                kategori = "Curtain Wall",
                warna = "Coklat",
                thickness = 2.0,
                hargaPerMeter = 95000
            )
        )

        // 1. Filter by Section Number
        val searchBySection = inventory.filter { p ->
            val query = "SF"
            p.sectionNo.contains(query, ignoreCase = true) || p.kategori.contains(query, ignoreCase = true)
        }
        assertEquals(1, searchBySection.size)
        assertEquals("SF-301", searchBySection.first().sectionNo)

        // 2. Filter by Product Category
        val searchByCategory = inventory.filter { p ->
            val query = "Sliding"
            p.sectionNo.contains(query, ignoreCase = true) || p.kategori.contains(query, ignoreCase = true)
        }
        assertEquals(1, searchByCategory.size)
        assertEquals("SD-901", searchByCategory.first().sectionNo)

        // 3. Filter with Category Chip & Search Query
        val selectedKategori = "Curtain Wall"
        val queryText = "100"
        val combined = inventory.filter { p ->
            val matchQuery = queryText.isBlank() ||
                    p.sectionNo.contains(queryText, ignoreCase = true) ||
                    p.kategori.contains(queryText, ignoreCase = true)
            val matchCat = selectedKategori == "Semua" || p.kategori.equals(selectedKategori, ignoreCase = true)
            matchQuery && matchCat
        }
        assertEquals(1, combined.size)
        assertEquals("CW-100", combined.first().sectionNo)
    }

    @Test
    fun testTransactionFilter_BySectionNumberAndCategory() {
        val prod1 = com.example.data.local.ProdukProfilEntity(id = 1, sectionNo = "SF-301", kategori = "Shop Front", warna = "Silver", thickness = 1.2, hargaPerMeter = 45000)
        val prod2 = com.example.data.local.ProdukProfilEntity(id = 2, sectionNo = "CW-100", kategori = "Curtain Wall", warna = "Hitam", thickness = 2.0, hargaPerMeter = 90000)

        val trx1 = com.example.data.local.TransaksiEntity(id = 101, tanggal = "2026-10-19 10:00", totalHarga = 450000)
        val trx2 = com.example.data.local.TransaksiEntity(id = 102, tanggal = "2026-10-19 11:00", totalHarga = 900000)

        val details1 = listOf(
            com.example.data.local.TransaksiDetailWithProduct(
                detail = com.example.data.local.TransaksiDetailEntity(id = 1, transaksiId = 101, produkId = 1, panjangDiminta = 2000.0, jumlahBatang = 2, subtotal = 450000),
                produk = prod1
            )
        )
        val details2 = listOf(
            com.example.data.local.TransaksiDetailWithProduct(
                detail = com.example.data.local.TransaksiDetailEntity(id = 2, transaksiId = 102, produkId = 2, panjangDiminta = 3000.0, jumlahBatang = 1, subtotal = 900000),
                produk = prod2
            )
        )

        val detailsGrouped = mapOf(101 to details1, 102 to details2)
        val allTrx = listOf(trx1, trx2)

        // Filter transactions by Section Number "SF"
        val querySection = "SF"
        val filteredBySection = allTrx.filter { trx ->
            val details = detailsGrouped[trx.id] ?: emptyList()
            details.mapNotNull { it.produk?.sectionNo }.any { it.contains(querySection, ignoreCase = true) }
        }
        assertEquals(1, filteredBySection.size)
        assertEquals(101, filteredBySection.first().id)

        // Filter transactions by Category "Curtain Wall"
        val queryCategory = "Curtain Wall"
        val filteredByCategory = allTrx.filter { trx ->
            val details = detailsGrouped[trx.id] ?: emptyList()
            details.mapNotNull { it.produk?.kategori }.any { it.contains(queryCategory, ignoreCase = true) }
        }
        assertEquals(1, filteredByCategory.size)
        assertEquals(102, filteredByCategory.first().id)
    }

    @Test
    fun testRoomTypeConverters_DateConversions() {
        val converter = com.example.data.local.AppTypeConverters()
        val now = java.util.Date()
        val timestamp = converter.dateToTimestamp(now)
        assertNotNull(timestamp)
        val convertedDate = converter.fromTimestamp(timestamp)
        assertEquals(now.time, convertedDate?.time)

        assertNull(converter.dateToTimestamp(null))
        assertNull(converter.fromTimestamp(null))
    }

    @Test
    fun testRoomTypeConverters_EnumConversions() {
        val converter = com.example.data.local.AppTypeConverters()

        // ProductCategory
        val cat = com.example.data.local.ProductCategory.CURTAIN_WALL
        val catString = converter.fromProductCategory(cat)
        assertEquals("CURTAIN_WALL", catString)
        assertEquals(cat, converter.toProductCategory(catString))

        // InventoryStatus
        val status = com.example.data.local.InventoryStatus.LOW_STOCK
        val statusString = converter.fromInventoryStatus(status)
        assertEquals("LOW_STOCK", statusString)
        assertEquals(status, converter.toInventoryStatus(statusString))

        // TransactionType
        val trxType = com.example.data.local.TransactionType.RESTOCK
        val typeString = converter.fromTransactionType(trxType)
        assertEquals("RESTOCK", typeString)
        assertEquals(trxType, converter.toTransactionType(typeString))

        // PaymentMethod
        val method = com.example.data.local.PaymentMethod.QRIS
        val methodString = converter.fromPaymentMethod(method)
        assertEquals("QRIS", methodString)
        assertEquals(method, converter.toPaymentMethod(methodString))

        // TransactionStatus
        val trxStatus = com.example.data.local.TransactionStatus.COMPLETED
        val trxStatusString = converter.fromTransactionStatus(trxStatus)
        assertEquals("COMPLETED", trxStatusString)
        assertEquals(trxStatus, converter.toTransactionStatus(trxStatusString))
    }

    @Test
    fun testRoomTypeConverters_StringListConversions() {
        val converter = com.example.data.local.AppTypeConverters()
        val list = listOf("Alloy 6063-T5", "Anodized Silver", "Tebal 1.35mm")
        val joined = converter.fromStringList(list)
        assertNotNull(joined)
        val recovered = converter.toStringList(joined)
        assertEquals(list, recovered)
        assertTrue(converter.toStringList(null)!!.isEmpty())
    }

    @Test
    fun testRoomEntities_InstantiationsAndDefaults() {
        val product = com.example.data.local.Product(
            id = 1,
            sectionNumber = "SF-301",
            name = "Kusen Shop Front",
            category = com.example.data.local.ProductCategory.SHOP_FRONT,
            thickness = 1.25,
            unitPrice = 48000L,
            color = "Silver"
        )
        assertEquals("SF-301", product.sectionNumber)
        assertEquals(com.example.data.local.ProductCategory.SHOP_FRONT, product.category)

        val inventoryItem = com.example.data.local.InventoryItem(
            id = 10,
            productId = product.id,
            sectionNumber = product.sectionNumber,
            batchNumber = "BATCH-001",
            quantityStock = 50,
            minStockAlert = 5,
            warehouseLocation = "Gudang Barat",
            status = com.example.data.local.InventoryStatus.IN_STOCK
        )
        assertEquals(1, inventoryItem.productId)
        assertEquals(50, inventoryItem.quantityStock)

        val transaction = com.example.data.local.Transaction(
            id = 1001,
            transactionCode = "TRX-2026-001",
            type = com.example.data.local.TransactionType.SALE,
            totalAmount = 240000L,
            paymentMethod = com.example.data.local.PaymentMethod.CASH,
            customerName = "Pak Budi",
            notes = "Potong 4 batang @ 1.5m",
            status = com.example.data.local.TransactionStatus.COMPLETED
        )
        assertEquals("TRX-2026-001", transaction.transactionCode)
        assertEquals(240000L, transaction.totalAmount)
        assertEquals(com.example.data.local.TransactionStatus.COMPLETED, transaction.status)

        val relation = com.example.data.local.InventoryWithProduct(
            inventoryItem = inventoryItem,
            product = product
        )
        assertEquals(product.sectionNumber, relation.product?.sectionNumber)
        assertEquals(inventoryItem.quantityStock, relation.inventoryItem.quantityStock)
    }

    @Test
    fun testProfileCanvas_BoundingBoxCalculations() {
        val points = listOf(
            com.example.mesh3d.Point2D(0f, 0f),
            com.example.mesh3d.Point2D(40f, 0f),
            com.example.mesh3d.Point2D(40f, 60f),
            com.example.mesh3d.Point2D(0f, 60f)
        )
        val bbox = com.example.ui.canvas.ProfileCalculations.computeBoundingBox(points)
        assertEquals(0f, bbox.minX, 0.001f)
        assertEquals(40f, bbox.maxX, 0.001f)
        assertEquals(0f, bbox.minY, 0.001f)
        assertEquals(60f, bbox.maxY, 0.001f)
        assertEquals(40f, bbox.width, 0.001f)
        assertEquals(60f, bbox.height, 0.001f)
    }

    @Test
    fun testProfileCanvas_PerimeterAndAreaCalculations() {
        // Hollow Box 40x40
        val points = listOf(
            com.example.mesh3d.Point2D(0f, 0f),
            com.example.mesh3d.Point2D(40f, 0f),
            com.example.mesh3d.Point2D(40f, 40f),
            com.example.mesh3d.Point2D(0f, 40f)
        )
        val closedPerimeter = com.example.ui.canvas.ProfileCalculations.computePerimeter(points, isClosed = true)
        assertEquals(160f, closedPerimeter, 0.01f)

        val openPerimeter = com.example.ui.canvas.ProfileCalculations.computePerimeter(points, isClosed = false)
        assertEquals(120f, openPerimeter, 0.01f)

        val area = com.example.ui.canvas.ProfileCalculations.computeShoelaceArea(points)
        assertEquals(1600f, area, 0.01f)

        // Luas solid aluminium untuk tebal 1.2 mm: approx 160 * 1.2 - 4 * 1.2^2 = 192 - 5.76 = 186.24 mm²
        val alumArea = com.example.ui.canvas.ProfileCalculations.computeAluminiumArea(points, thicknessMm = 1.2f, isClosed = true)
        assertTrue(alumArea > 150f && alumArea < 220f)

        // Estimasi berat per meter: Area * 0.00270 kg/m
        val weight = com.example.ui.canvas.ProfileCalculations.computeWeightPerMeter(alumArea)
        assertTrue(weight > 0.4f && weight < 0.6f) // ~0.5 kg/m, typical for 40x40 aluminium tube!
    }

    @Test
    fun testProfileCanvas_NearestPointAndSegmentDetection() {
        val points = listOf(
            com.example.mesh3d.Point2D(0f, 0f),
            com.example.mesh3d.Point2D(50f, 0f),
            com.example.mesh3d.Point2D(50f, 50f),
            com.example.mesh3d.Point2D(0f, 50f)
        )

        // Titik dekat (49f, 1f) harus mendeteksi indeks 1 (50, 0)
        val nearest = com.example.ui.canvas.ProfileCalculations.findNearestPointIndex(
            points = points,
            query = com.example.mesh3d.Point2D(49f, 1f),
            thresholdMm = 5f
        )
        assertEquals(1, nearest)

        // Titik jauh (100f, 100f) harus null
        val none = com.example.ui.canvas.ProfileCalculations.findNearestPointIndex(
            points = points,
            query = com.example.mesh3d.Point2D(100f, 100f),
            thresholdMm = 5f
        )
        assertNull(none)

        // Titik pada segmen tengah (25f, 0.5f) harus mendeteksi segmen indeks 0 (antara P0 dan P1)
        val seg = com.example.ui.canvas.ProfileCalculations.findNearestSegmentIndex(
            points = points,
            query = com.example.mesh3d.Point2D(25f, 0.5f),
            isClosed = true,
            thresholdMm = 3f
        )
        assertEquals(0, seg)
    }

    @Test
    fun testProfileCanvas_PresetsRegistry() {
        val presets = com.example.ui.canvas.ProfilePresetRegistry.presets
        assertTrue(presets.isNotEmpty())
        assertTrue(presets.any { it.name.contains("Hollow Box") })
        assertTrue(presets.any { it.name.contains("L-Angle") || it.name.contains("Siku") })
        assertTrue(presets.any { it.name.contains("Kusen") })

        for (preset in presets) {
            assertTrue(preset.points.size >= 3)
            assertTrue(preset.thicknessMm > 0.5f)
            val bbox = com.example.ui.canvas.ProfileCalculations.computeBoundingBox(preset.points)
            assertTrue(bbox.width > 0f)
            assertTrue(bbox.height > 0f)
        }
    }

    @Test
    fun testExtrusion3D_MeshGenerationFromContour() {
        val contour = listOf(
            com.example.mesh3d.Point2D(0f, 0f),
            com.example.mesh3d.Point2D(50f, 0f),
            com.example.mesh3d.Point2D(50f, 50f),
            com.example.mesh3d.Point2D(0f, 50f)
        )
        val mesh = com.example.mesh3d.HollowExtruder.generateHollowMesh(
            contour = contour,
            thickness = 1.5,
            panjangBatang = 1200.0,
            isClosed = true
        )

        // Harus memiliki vertices, normals, dan segitiga 3D
        assertTrue(mesh.vertices.isNotEmpty())
        assertTrue(mesh.normals.isNotEmpty())
        assertTrue(mesh.triangles.isNotEmpty())
        assertTrue(mesh.indices.isNotEmpty())

        // Cek bahwa panjang Z mencapai 1200mm
        val maxZ = mesh.vertices.maxOf { it.z }
        val minZ = mesh.vertices.minOf { it.z }
        assertEquals(0f, minZ, 0.01f)
        assertEquals(1200f, maxZ, 0.01f)

        // Cek bahwa lebar dan tinggi penampang X,Y mencakup profil 50mm beserta ketebalan dinding
        val maxX = mesh.vertices.maxOf { it.x }
        val maxY = mesh.vertices.maxOf { it.y }
        assertTrue(maxX >= 50f && maxX <= 53f)
        assertTrue(maxY >= 50f && maxY <= 53f)
    }

    @Test
    fun testExtrusion3D_MaterialFinishes() {
        val renderer = com.example.mesh3d.opengl.ExtrusionGLRenderer()
        renderer.setMaterial("Silver")
        assertEquals(0.72f, renderer.materialColor[0], 0.05f)

        renderer.setMaterial("Black")
        assertEquals(0.18f, renderer.materialColor[0], 0.05f)

        renderer.setMaterial("Brown")
        assertEquals(0.36f, renderer.materialColor[0], 0.05f)

        renderer.setMaterial("Gold")
        assertEquals(0.85f, renderer.materialColor[0], 0.05f)
    }

    @Test
    fun testExtrusion3D_AssemblyPartConversion() {
        val contour = listOf(
            com.example.mesh3d.Point2D(0f, 0f),
            com.example.mesh3d.Point2D(40f, 0f),
            com.example.mesh3d.Point2D(40f, 40f),
            com.example.mesh3d.Point2D(0f, 40f)
        )
        val bbox = com.example.ui.canvas.ProfileCalculations.computeBoundingBox(contour)
        val part = com.example.mesh3d.AssemblyPart(
            name = "Profil Kustom",
            lengthMm = 1500f,
            widthMm = bbox.width,
            heightMm = bbox.height,
            thicknessMm = 1.2f,
            color = "Black"
        )

        assertEquals("Profil Kustom", part.name)
        assertEquals(1500f, part.lengthMm, 0.01f)
        assertEquals(40f, part.widthMm, 0.01f)
        assertEquals(40f, part.heightMm, 0.01f)
        assertEquals("Black", part.color)
    }

    @Test
    fun testInventoryDashboard_LowStockAlertIdentification() {
        val items = listOf(
            com.example.data.local.InventoryItem(
                id = 1,
                productId = 1,
                sectionNumber = "SF-001 Open Back",
                quantityStock = 3,
                minStockAlert = 5
            ),
            com.example.data.local.InventoryItem(
                id = 2,
                productId = 2,
                sectionNumber = "SD-9055 Kusen Pintu Geser",
                quantityStock = 20,
                minStockAlert = 5
            ),
            com.example.data.local.InventoryItem(
                id = 3,
                productId = 3,
                sectionNumber = "CW-201 Kusen Jendela",
                quantityStock = 0,
                minStockAlert = 5
            )
        )

        val lowStockItems = items.filter { it.quantityStock <= it.minStockAlert }
        assertEquals(2, lowStockItems.size)
        assertTrue(lowStockItems.any { it.sectionNumber == "SF-001 Open Back" })
        assertTrue(lowStockItems.any { it.sectionNumber == "CW-201 Kusen Jendela" })
        assertFalse(lowStockItems.any { it.sectionNumber == "SD-9055 Kusen Pintu Geser" })

        // Cek item habis
        val outOfStock = items.filter { it.quantityStock <= 0 }
        assertEquals(1, outOfStock.size)
        assertEquals("CW-201 Kusen Jendela", outOfStock.first().sectionNumber)
    }

    @Test
    fun testInventoryDashboard_SearchAndFilterLogic() {
        val product1 = com.example.data.local.Product(
            id = 1,
            sectionNumber = "SF-001 Open Back",
            name = "Kusen Shop Front 4 Inch",
            category = com.example.data.local.ProductCategory.SHOP_FRONT,
            unitPrice = 48000L
        )
        val product2 = com.example.data.local.Product(
            id = 2,
            sectionNumber = "SD-9055",
            name = "Daun Pintu Geser",
            category = com.example.data.local.ProductCategory.SLIDING_DOOR,
            unitPrice = 62000L
        )

        val list = listOf(
            com.example.data.local.InventoryWithProduct(
                inventoryItem = com.example.data.local.InventoryItem(
                    id = 1,
                    productId = 1,
                    sectionNumber = "SF-001 Open Back",
                    warehouseLocation = "Rak Profil A",
                    batchNumber = "BATCH-2026-101",
                    quantityStock = 4,
                    minStockAlert = 5
                ),
                product = product1
            ),
            com.example.data.local.InventoryWithProduct(
                inventoryItem = com.example.data.local.InventoryItem(
                    id = 2,
                    productId = 2,
                    sectionNumber = "SD-9055",
                    warehouseLocation = "Rak Profil B",
                    batchNumber = "BATCH-2026-102",
                    quantityStock = 18,
                    minStockAlert = 5
                ),
                product = product2
            )
        )

        // Filter by section query
        val matchSection = list.filter { it.inventoryItem.sectionNumber.contains("SF-001", ignoreCase = true) }
        assertEquals(1, matchSection.size)
        assertEquals("SF-001 Open Back", matchSection.first().inventoryItem.sectionNumber)

        // Filter by warehouse location
        val matchLocation = list.filter { it.inventoryItem.warehouseLocation.contains("Rak Profil B", ignoreCase = true) }
        assertEquals(1, matchLocation.size)
        assertEquals("SD-9055", matchLocation.first().inventoryItem.sectionNumber)

        // Filter by category
        val matchCat = list.filter { it.product?.category == com.example.data.local.ProductCategory.SLIDING_DOOR }
        assertEquals(1, matchCat.size)
        assertEquals("SD-9055", matchCat.first().inventoryItem.sectionNumber)

        // Filter by low stock only
        val matchLow = list.filter { it.inventoryItem.quantityStock <= it.inventoryItem.minStockAlert }
        assertEquals(1, matchLow.size)
        assertEquals("SF-001 Open Back", matchLow.first().inventoryItem.sectionNumber)
    }

    @Test
    fun testInventoryDashboard_ValuationCalculation() {
        val qtyStock = 10
        val lengthMeters = 6.0
        val pricePerMeter = 50000L

        val totalValue = (qtyStock * lengthMeters * pricePerMeter).toLong()
        assertEquals(3000000L, totalValue) // Rp 3.000.000
    }

    @Test
    fun testCuttingStockViewModel_GreedyBinPackingAndReceiptCalculation() {
        val viewModel = com.example.cuttingstock.CuttingStockViewModel(repository = null)

        // Initial state has 2x 2100mm and 2x 900mm
        val initialState = viewModel.uiState.value
        assertNotNull(initialState.optimizationResult)
        assertEquals(2, initialState.optimizationResult?.totalBarsNeeded)

        // Set profile with known hargaPerMeter: 50,000 / meter
        val profile = com.example.data.local.ProdukProfilEntity(
            id = 1,
            sectionNo = "CA-001",
            kategori = "Kusen 3 Inch",
            thickness = 1.0,
            hargaPerMeter = 50000L,
            warna = "Silver"
        )
        viewModel.selectProfile(profile)

        // Clear cuts and add specific test cuts:
        // 1x 3000mm, 1x 2800mm -> In 1 bar: 3000 + 3mm kerf + 2800 = 5803mm <= 6000mm -> 1 bar!
        viewModel.clearAllCutRequests()
        viewModel.addCutRequest(3000.0, 1, "Kusen Atas")
        viewModel.addCutRequest(2800.0, 1, "Kusen Bawah")

        val state1 = viewModel.uiState.value
        assertEquals(1, state1.optimizationResult?.totalBarsNeeded)
        assertEquals(2, state1.optimizationResult?.totalPiecesCount)
        assertEquals(5800.0, state1.optimizationResult?.totalRequestedLengthMm ?: 0.0, 0.01)

        // Material subtotal for 1 bar (6m * 50,000) = 300,000
        val receipt1 = state1.receiptCalculation
        assertNotNull(receipt1)
        assertEquals(300000L, receipt1?.materialSubtotal)

        // Set cutting fee Rp 5,000 per cut, 2 cuts = 10,000
        viewModel.setCuttingFeePerCut(5000L)
        // Set discount Rp 20,000
        viewModel.setDiscountAmount(20000L)
        // Subtotal taxable = 300,000 + 10,000 - 20,000 = 290,000
        // Set tax rate 10% -> 29,000 tax -> Grand Total = 319,000
        viewModel.setTaxRate(10.0)

        val receipt2 = viewModel.uiState.value.receiptCalculation
        assertNotNull(receipt2)
        assertEquals(10000L, receipt2?.cuttingFeeTotal)
        assertEquals(20000L, receipt2?.discountAmount)
        assertEquals(29000L, receipt2?.taxAmount)
        assertEquals(319000L, receipt2?.grandTotal)

        // Cash payment and change calculation
        viewModel.setCashReceived(350000L)
        viewModel.setPaymentMethod(com.example.data.local.PaymentMethod.CASH)

        val receipt3 = viewModel.uiState.value.receiptCalculation
        assertNotNull(receipt3)
        assertEquals(350000L, receipt3?.cashReceived)
        assertEquals(31000L, receipt3?.changeAmount) // 350,000 - 319,000 = 31,000
    }

    @Test
    fun testCuttingStockViewModel_GreedyBestFitDecreasingMultiBar() {
        val viewModel = com.example.cuttingstock.CuttingStockViewModel(repository = null)
        viewModel.clearAllCutRequests()

        // Pieces: 3x 2500mm, 2x 1500mm
        // Sorted descending: 2500, 2500, 2500, 1500, 1500
        // Bar 1: 2500 + 3 + 2500 = 5003 (rem 997). 1500 doesn't fit in 997.
        // Bar 2: 2500 + 3 + 1500 + 3 + 1500 = 5506 (rem 494). Fits!
        // Total bars needed: 2 bars of 6000mm!
        viewModel.addCutRequest(2500.0, 3, "Tiang Partisi")
        viewModel.addCutRequest(1500.0, 2, "Ambang Partisi")

        val state = viewModel.uiState.value
        val opt = state.optimizationResult
        assertNotNull(opt)
        assertEquals(2, opt?.totalBarsNeeded)
        assertEquals(5, opt?.totalPiecesCount)
        assertEquals(10500.0, opt?.totalRequestedLengthMm ?: 0.0, 0.01)

        // Efficiency = 10500 / 12000 * 100% = 87.5%
        assertEquals(87.5, opt?.overallEfficiencyPct ?: 0.0, 0.5)
    }
}



