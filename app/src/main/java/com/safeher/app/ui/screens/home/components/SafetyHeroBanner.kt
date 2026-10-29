package com.safeher.app.ui.screens.home.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
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
import com.safeher.app.R
import com.safeher.app.ui.components.SafeHerStatusTone
import com.safeher.app.ui.components.SoftSurface
import com.safeher.app.ui.components.StatusChip
import com.safeher.app.ui.theme.SafeHerDimens
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun SafetyHeroBanner(
    guardianCount: Int = 0,
    modifier: Modifier = Modifier
) {
    SoftSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SafeHerDimens.screenHorizontal, vertical = SafeHerDimens.spaceXxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                text = stringResource(R.string.status_protected),
                tone = SafeHerStatusTone.INFO
            )

            val guardianLabel = when (guardianCount) {
                0 -> stringResource(R.string.guardians_unconfigured)
                1 -> stringResource(R.string.guardians_one_added)
                else -> stringResource(R.string.guardians_n_added, guardianCount)
            }

            StatusChip(
                text = guardianLabel,
                tone = if (guardianCount > 0) SafeHerStatusTone.SAFE else SafeHerStatusTone.WARNING
            )
        }

        Spacer(modifier = Modifier.height(SafeHerDimens.spaceSm))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(SafeHerDimens.spaceXxs))
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(SafeHerDimens.spaceSm))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SafeHerPurpleContainer, SafeHerPinkContainer)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(SafeHerDimens.iconMd)
                )
            }
        }
    }
}
