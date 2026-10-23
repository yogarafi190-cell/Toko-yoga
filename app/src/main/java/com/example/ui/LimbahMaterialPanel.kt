package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuttingstock.CuttingOptimizationResult
import com.example.cuttingstock.StockBarAllocation
import java.util.Locale

@Composable
fun LimbahMaterialPanel(
    optResult: CuttingOptimizationResult,
    modifier: Modifier = Modifier
) {
    if (optResult.totalBarsNeeded == 0 || optResult.barAllocations.isEmpty()) {
        return
    }

    var selectedBarNumber by remember { mutableStateOf<Int?>(null) }
    val selectedBar = optResult.barAllocations.find { it.barNumber == selectedBarNumber }

    // Hitung klasifikasi limbah
    val reusableOffcuts = optResult.barAllocations.filter { it.remainingWasteMm >= 500.0 }
    val scrapWaste = optResult.barAllocations.filter { it.remainingWasteMm < 500.0 }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_informasi_limbah"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Informasi & Grafik Sisa Material (Limbah)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                }

                Surface(
                    color = if (optResult.overallWastePct > 20.0) Color(0x33EF4444) else Color(0x3310B981),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Total Limbah: ${optResult.totalWasteLengthMm.toInt()} mm (${String.format(Locale.US, "%.1f", optResult.overallWastePct)}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (optResult.overallWastePct > 20.0) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Batang Perbandingan Keseluruhan (Total Material vs Total Limbah)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Perbandingan Efisiensi Keseluruhan:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                        "${optResult.totalRequestedLengthMm.toInt()} mm terpakai / ${optResult.totalMaterialConsumedMm.toInt()} mm total",
                        fontSize = 10.5.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }

                val usedWeight = (optResult.overallEfficiencyPct / 100.0).toFloat().coerceIn(0.02f, 0.98f)
                val wasteWeight = (optResult.overallWastePct / 100.0).toFloat().coerceIn(0.02f, 0.98f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    // Terpakai (Biru / Cyan)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(usedWeight)
                            .background(Color(0xFF0284C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${String.format(Locale.US, "%.1f", optResult.overallEfficiencyPct)}% Terpakai",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    // Sisa Limbah (Merah / Oranye)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(wasteWeight)
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${String.format(Locale.US, "%.1f", optResult.overallWastePct)}% Limbah",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // =================================================================
            // GRAFIK BATANG SEDERHANA SISA LIMBAH PER BATANG
            // =================================================================
            Text(
                "Grafik Batang Sisa Material (Scrap Tiap Batang):",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE2E8F0)
            )

            // Diagram Batang Tegak Sederhana
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B111E))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("grafik_batang_limbah")
            ) {
                // Garis skala latar belakang
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x22FFFFFF)))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x15FFFFFF)))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x33FFFFFF)))
                }

                // Barisan batang grafik
                val maxWasteInList = optResult.barAllocations.maxOfOrNull { it.remainingWasteMm } ?: 1.0
                val chartMax = maxOf(maxWasteInList, optResult.standardBarLengthMm * 0.4).coerceAtLeast(500.0)

                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    items(optResult.barAllocations, key = { it.barNumber }) { bar ->
                        val isSelected = selectedBarNumber == bar.barNumber
                        val wasteFraction = (bar.remainingWasteMm / chartMax).toFloat().coerceIn(0.04f, 1.0f)
                        val animatedFraction by animateFloatAsState(targetValue = wasteFraction, label = "barHeight")

                        val barColor = when {
                            bar.remainingWasteMm >= 800.0 -> Color(0xFFEF4444) // Limbah panjang (> 800 mm)
                            bar.remainingWasteMm >= 300.0 -> Color(0xFFF59E0B) // Limbah sedang (300-800 mm)
                            else -> Color(0xFF10B981)                          // Limbah minimal (< 300 mm)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(54.dp)
                                .clickable {
                                    selectedBarNumber = if (isSelected) null else bar.barNumber
                                }
                        ) {
                            // Angka milimeter sisa di atas batang
                            Text(
                                "${bar.remainingWasteMm.toInt()}mm",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFFF59E0B) else barColor
                            )
                            Spacer(Modifier.height(3.dp))

                            // Batang vertikal visual
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.65f)
                                    .fillMaxWidth(0.65f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedFraction)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (isSelected) Color(0xFFFBBF24) else barColor)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = Color.White,
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                        )
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            // Label Nomor Batang di bawah
                            Surface(
                                color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "Btg ${bar.barNumber}",
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Info Detail Batang yang Dipilih (Jika ada klik di grafik)
            if (selectedBar != null) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Detail Batang #${selectedBar.barNumber}: Sisa ${selectedBar.remainingWasteMm.toInt()} mm (${String.format(Locale.US, "%.1f", 100 - selectedBar.efficiencyPct)}% limbah)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Potongan: ${selectedBar.cuts.joinToString { "${it.toInt()}mm" }} | Kerf pisau: ${selectedBar.kerfLossTotalMm.toInt()}mm",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        TextButton(
                            onClick = { selectedBarNumber = null },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Tutup", fontSize = 10.sp)
                        }
                    }
                }
            }

            // Rekomendasi Penggunaan Sisa Material (Workshop Offcut Guidance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sisa yang masih bisa dimanfaatkan
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Recycling,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Sisa Reusable (≥500mm)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text(
                                "${reusableOffcuts.size} potongan (${reusableOffcuts.sumOf { it.remainingWasteMm }.toInt()} mm)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                // Limbah potongan pendek (Scrap murni)
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Scrap Buang (<500mm)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text(
                                "${scrapWaste.size} potongan (${scrapWaste.sumOf { it.remainingWasteMm }.toInt()} mm)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }
    }
}
