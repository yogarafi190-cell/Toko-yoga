package com.example.mesh3d

/**
 * Representasi snapshot perakitan rangka pada satu titik waktu
 * untuk sistem Undo/Redo (History Stack).
 */
data class AssemblySnapshot(
    val parts: List<AssemblyPart>,
    val selectedPartId: String? = null,
    val actionLabel: String = "Perubahan",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manajer tumpukan riwayat (Undo/Redo History Stack) untuk perakitan objek 3D Toko Yoga.
 * Mengikuti pola Memento/Command untuk pelacakan perubahan koordinat, rotasi,
 * penambahan/penghapusan part, serta pemuatan preset dan draft proyek.
 */
class AssemblyHistoryManager(
    val maxHistorySize: Int = 50
) {
    private val undoStack = mutableListOf<AssemblySnapshot>()
    private val redoStack = mutableListOf<AssemblySnapshot>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    /**
     * Merekam snapshot sebelum perubahan dilakukan.
     * Membersihkan redoStack karena percabangan aksi baru telah dibuat.
     */
    fun push(
        currentParts: List<AssemblyPart>,
        selectedPartId: String?,
        actionLabel: String
    ) {
        // Simpan salinan mendalam (deep copy) dari daftar part
        val snapshot = AssemblySnapshot(
            parts = currentParts.map { it.copy() },
            selectedPartId = selectedPartId,
            actionLabel = actionLabel
        )
        undoStack.add(snapshot)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    /**
     * Melakukan pembatalan aksi terakhir (Undo).
     * Menyimpan kondisi saat ini ke redoStack dan mengembalikan snapshot sebelumnya.
     */
    fun undo(
        currentParts: List<AssemblyPart>,
        currentSelectedPartId: String?
    ): AssemblySnapshot? {
        if (undoStack.isEmpty()) return null

        val previousSnapshot = undoStack.removeAt(undoStack.lastIndex)
        val currentToRedo = AssemblySnapshot(
            parts = currentParts.map { it.copy() },
            selectedPartId = currentSelectedPartId,
            actionLabel = previousSnapshot.actionLabel
        )
        redoStack.add(currentToRedo)
        return previousSnapshot
    }

    /**
     * Mengulang kembali aksi yang dibatalkan (Redo).
     * Menyimpan kondisi saat ini ke undoStack dan mengembalikan snapshot berikutnya.
     */
    fun redo(
        currentParts: List<AssemblyPart>,
        currentSelectedPartId: String?
    ): AssemblySnapshot? {
        if (redoStack.isEmpty()) return null

        val nextSnapshot = redoStack.removeAt(redoStack.lastIndex)
        val currentToUndo = AssemblySnapshot(
            parts = currentParts.map { it.copy() },
            selectedPartId = currentSelectedPartId,
            actionLabel = nextSnapshot.actionLabel
        )
        undoStack.add(currentToUndo)
        return nextSnapshot
    }

    fun peekUndoLabel(): String? = undoStack.lastOrNull()?.actionLabel

    fun peekRedoLabel(): String? = redoStack.lastOrNull()?.actionLabel

    /**
     * Menghapus seluruh riwayat undo dan redo (misalnya saat mereset kanvas).
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
