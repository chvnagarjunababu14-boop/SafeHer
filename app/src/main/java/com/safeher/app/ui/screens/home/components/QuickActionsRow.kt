package com.safeher.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safeher.app.R
import com.safeher.app.ui.components.SectionHeader
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerDimens
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPinkContainer
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerStatusSafe
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted
import com.safeher.app.ui.theme.SafeHerTextPrimary

@Composable
fun QuickActionsRow(
    onShareStatusClick: () -> Unit,
    onCheckInClick: () -> Unit,
    onSafetyTipsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SafeHerDimens.screenHorizontal, vertical = SafeHerDimens.spaceXxs)
    ) {
        SectionHeader(title = stringResource(R.string.quick_actions_title))

        Spacer(modifier = Modifier.height(SafeHerDimens.spaceSm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SafeHerDimens.spaceXs)
        ) {
            QuickActionItem(
                title = stringResource(R.string.quick_share_status),
                subtitle = "Share status",
                icon = Icons.Default.Share,
                iconTint = SafeHerPurplePrimary,
                iconContainerColor = SafeHerPurpleContainer,
                onClick = onShareStatusClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionItem(
                title = stringResource(R.string.quick_check_in),
                subtitle = "Check-in ping",
                icon = Icons.Default.CheckCircle,
                iconTint = SafeHerStatusSafe,
                iconContainerColor = SafeHerStatusSafe.copy(alpha = 0.12f),
                onClick = onCheckInClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionItem(
                title = stringResource(R.string.quick_safety_tips),
                subtitle = "Safety guides",
                icon = Icons.Default.Lightbulb,
                iconTint = SafeHerPinkAccent,
                iconContainerColor = SafeHerPinkContainer,
                onClick = onSafetyTipsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = SafeHerDimens.touchMin * 2)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, SafeHerBorderLavender, MaterialTheme.shapes.medium)
            .clickable(
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = SafeHerSurfaceWhite,
        shadowElevation = SafeHerDimens.elevationLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SafeHerDimens.spaceXs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(SafeHerDimens.iconSm)
                )
            }

            Spacer(modifier = Modifier.height(SafeHerDimens.spaceXs))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(SafeHerDimens.spaceXxxs))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
