package com.safeher.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun SafetyHeroBanner(
    guardianCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafeHerSurfaceWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Honest non-misleading UI Preview Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SafeHerPurpleContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.status_protected),
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeHerPurpleDark,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Dynamic Guardians Indicator from Local Storage
                val guardianLabel = when (guardianCount) {
                    0 -> stringResource(R.string.guardians_unconfigured)
                    1 -> "1 Guardian Added"
                    else -> "$guardianCount Guardians Added"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (guardianCount > 0) SafeHerPurpleContainer else SafeHerPinkContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = guardianLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (guardianCount > 0) SafeHerPurplePrimary else SafeHerPinkAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.hero_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = SafeHerTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.hero_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeHerTextSecondary,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Reassuring preview emblem
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(SafeHerPurpleContainer, SafeHerPinkContainer)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
