package com.nmamit.attendance.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.SubjectAttendanceDto
import com.nmamit.attendance.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfSimulatorScreen(
    subjects: List<SubjectAttendanceDto>,
    onNavigateBack: () -> Unit
) {
    var selectedSubjectIndex by remember { mutableIntStateOf(0) }
    var classesToAttend by remember { mutableFloatStateOf(0f) }
    var classesToMiss by remember { mutableFloatStateOf(0f) }

    val currentSubject = subjects.getOrNull(selectedSubjectIndex)

    val currentAttended = currentSubject?.attendedInt ?: 0
    val currentConducted = currentSubject?.conductedInt ?: 0
    val currentPercent = currentSubject?.percentage ?: 0.0

    val projectedAttended = currentAttended + classesToAttend.toInt()
    val projectedConducted = currentConducted + classesToAttend.toInt() + classesToMiss.toInt()
    val projectedPercent = if (projectedConducted > 0) {
        (projectedAttended.toDouble() / projectedConducted) * 100.0
    } else 0.0

    val delta = projectedPercent - currentPercent
    val isProjectedDanger = projectedPercent < 75.0
    val projectedColor = when {
        isProjectedDanger -> RedDanger
        projectedPercent < 85.0 -> AmberWarning
        else -> GreenSuccess
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What-If Attendance Simulator", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (subjects.isEmpty()) {
                Text(
                    text = "No subjects available. Please load attendance first.",
                    color = TextSecondary,
                    modifier = Modifier.padding(20.dp)
                )
                return@Scaffold
            }

            // Subject Selector Dropdown
            var expanded by remember { mutableStateOf(false) }

            Text(text = "Select Course to Simulate", fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = "${currentSubject?.subCode} - ${currentSubject?.subName}",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DarkSurfaceCard)
                ) {
                    subjects.forEachIndexed { index, sub ->
                        DropdownMenuItem(
                            text = { Text("${sub.subCode} - ${sub.subName}", color = TextPrimary) },
                            onClick = {
                                selectedSubjectIndex = index
                                classesToAttend = 0f
                                classesToMiss = 0f
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Simulation Projection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Projected Attendance",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", projectedPercent),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = projectedColor
                    )
                    Text(
                        text = "${if (delta >= 0) "+" else ""}${String.format(Locale.US, "%.1f%%", delta)} vs Current (${String.format(Locale.US, "%.1f%%", currentPercent)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (delta >= 0) GreenSuccess else RedDanger
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkSurfaceBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Classes Attended", fontSize = 11.sp, color = TextSecondary)
                            Text("$projectedAttended / $projectedConducted", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Status", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = if (isProjectedDanger) "CRITICAL" else if (projectedPercent < 85.0) "WARNING" else "SAFE",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = projectedColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Slider 1: Classes to Attend
            Text(
                text = "Attend Next Classes: +${classesToAttend.toInt()}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = GreenSuccess
            )
            Slider(
                value = classesToAttend,
                onValueChange = { classesToAttend = it },
                valueRange = 0f..20f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = GreenSuccess,
                    activeTrackColor = GreenSuccess,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Slider 2: Classes to Bunk / Miss
            Text(
                text = "Miss / Bunk Next Classes: +${classesToMiss.toInt()}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = RedDanger
            )
            Slider(
                value = classesToMiss,
                onValueChange = { classesToMiss = it },
                valueRange = 0f..20f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = RedDanger,
                    activeTrackColor = RedDanger,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Reset Button
            OutlinedButton(
                onClick = {
                    classesToAttend = 0f
                    classesToMiss = 0f
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Text("Reset Simulation Sliders", color = TextSecondary)
            }
        }
    }
}
