package com.safeher.app.ui.screens.guardian

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianAlertDetailScreen(
    viewModel: GuardianAlertDetailViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Emergency SOS Alert",
                        fontWeight = FontWeight.Bold,
                        color = SafeHerSosCrimson
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SafeHerTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SafeHerSurfaceWhite)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SafeHerSosCrimson)
                }
            } else if (uiState.errorMessage != null && uiState.alertDetails == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = SafeHerSosCrimson, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage ?: "Failed to load alert details.",
                        color = SafeHerTextPrimary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadDetails() },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary)
                    ) {
                        Text("Retry")
                    }
                }
            } else {
                val details = uiState.alertDetails
                if (details != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        if (details.alertId.startsWith("demo_")) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🧪", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "DEMO SIMULATION: This is a simulated alert for evaluator testing.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SafeHerPurpleDark
                                    )
                                }
                            }
                        }

                        // 1. Emergency Header Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SafeHerSosTrack),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SafeHerSosCrimson),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "${details.senderDisplayName} needs help!",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SafeHerSosCrimson
                                    )
                                    val formattedTime = SimpleDateFormat("h:mm a, MMM d, yyyy", Locale.getDefault())
                                        .format(Date(details.createdAtMillis))
                                    Text(
                                        text = "Triggered: $formattedTime",
                                        fontSize = 12.sp,
                                        color = SafeHerTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Distress Message Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Distress Message",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SafeHerTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = details.messageText,
                                    fontSize = 15.sp,
                                    color = SafeHerTextPrimary,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Consented Location Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SafeHerSurfaceWhite),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafeHerPurplePrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Location Information",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SafeHerTextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (details.hasLocation && details.latitude != null && details.longitude != null) {
                                    val lat = String.format(Locale.US, "%.6f", details.latitude)
                                    val lng = String.format(Locale.US, "%.6f", details.longitude)
                                    val acc = details.accuracyMeters?.toInt() ?: 0

                                    Text(
                                        text = "Coordinates: $lat, $lng" + if (acc > 0) " (±${acc}m accuracy)" else "",
                                        fontSize = 13.sp,
                                        color = SafeHerTextPrimary
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = {
                                            val mapUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                                            context.startActivity(mapIntent)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open in Google Maps")
                                    }
                                } else {
                                    Text(
                                        text = "The sender did not include location coordinates with this alert, or location coordinates have expired under the 24-hour privacy retention policy.",
                                        fontSize = 12.sp,
                                        color = SafeHerTextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 4. Acknowledged status or action button
                        if (uiState.acknowledgeSuccess || details.isAcknowledgedByMe) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SafeHerPurpleContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeHerPurplePrimary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "You have acknowledged this alert. The sender's app now displays your confirmed acknowledgement.",
                                        fontSize = 13.sp,
                                        color = SafeHerPurpleDark,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.acknowledgeAlert() },
                                enabled = !uiState.isAcknowledging,
                                colors = ButtonDefaults.buttonColors(containerColor = SafeHerPurplePrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                if (uiState.isAcknowledging) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Acknowledge Alert (I Am Aware)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Disclaimer
                        Text(
                            text = "Disclaimer: Acknowledging confirms that you have seen this notification. It does not replace emergency services. If the sender is in immediate danger, call local emergency services (112) immediately.",
                            fontSize = 11.sp,
                            color = SafeHerTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
