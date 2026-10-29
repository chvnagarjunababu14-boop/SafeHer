package com.safeher.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerDimens
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSosCrimson
import com.safeher.app.ui.theme.SafeHerSosTrack
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerStatusWarning
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

enum class SafeHerRoleTone { USER, GUARDIAN }

enum class SafeHerStatusTone { NEUTRAL, SAFE, WARNING, EMERGENCY, INFO }

@Composable
fun SoftSurface(
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(SafeHerDimens.spaceMd),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (bordered) Modifier.border(1.dp, SafeHerBorderLavender, MaterialTheme.shapes.medium)
                else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        color = SafeHerSurfaceWhite,
        shadowElevation = SafeHerDimens.elevationLow,
        content = {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(SafeHerDimens.spaceXxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(SafeHerDimens.spaceSm))
            trailing()
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    tone: SafeHerStatusTone = SafeHerStatusTone.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        SafeHerStatusTone.SAFE -> SafeHerStatusSafe.copy(alpha = 0.14f) to SafeHerStatusSafe
        SafeHerStatusTone.WARNING -> SafeHerStatusWarning.copy(alpha = 0.16f) to Color(0xFFB45309)
        SafeHerStatusTone.EMERGENCY -> SafeHerSosTrack to SafeHerSosCrimson
        SafeHerStatusTone.INFO -> SafeHerPurpleContainer to SafeHerPurpleDark
        SafeHerStatusTone.NEUTRAL -> SafeHerPurpleContainer.copy(alpha = 0.55f) to SafeHerTextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RoleBadge(
    tone: SafeHerRoleTone,
    modifier: Modifier = Modifier
) {
    val (label, bg, fg) = when (tone) {
        SafeHerRoleTone.USER -> Triple("User", SafeHerPurpleContainer, SafeHerPurpleDark)
        SafeHerRoleTone.GUARDIAN -> Triple("Guardian", SafeHerPinkContainer, SafeHerPurpleDark)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    SoftSurface(modifier = modifier, contentPadding = PaddingValues(SafeHerDimens.spaceXl)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SafeHerPurpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(SafeHerDimens.iconLg)
                )
            }
            Spacer(modifier = Modifier.height(SafeHerDimens.spaceMd))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(SafeHerDimens.spaceXs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = SafeHerTextSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(SafeHerDimens.spaceMd))
                SafeHerPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: SafeHerStatusTone = SafeHerStatusTone.INFO
) {
    val bg = when (tone) {
        SafeHerStatusTone.WARNING -> SafeHerStatusWarning.copy(alpha = 0.12f)
        SafeHerStatusTone.EMERGENCY -> SafeHerSosTrack
        else -> SafeHerPurpleContainer
    }
    val fg = when (tone) {
        SafeHerStatusTone.WARNING -> Color(0xFFB45309)
        SafeHerStatusTone.EMERGENCY -> SafeHerSosCrimson
        else -> SafeHerPurpleDark
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .padding(SafeHerDimens.spaceSm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = fg
        )
    }
}

@Composable
fun SafeHerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = SafeHerPurplePrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(SafeHerDimens.touchMin),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = SafeHerSurfaceWhite,
            disabledContainerColor = SafeHerTextMuted.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SafeHerSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(SafeHerDimens.touchMin),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeHerPurplePrimary)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SafeHerTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SafeHerPurplePrimary
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
