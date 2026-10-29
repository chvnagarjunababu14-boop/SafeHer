package com.safeher.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerDimens
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun SafetyShortcutCard(
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
            .fillMaxWidth()
            .heightIn(min = 108.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, SafeHerBorderLavender, MaterialTheme.shapes.medium)
            .clickable(
                role = Role.Button,
                onClickLabel = "$title shortcut",
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = SafeHerSurfaceWhite,
        shadowElevation = SafeHerDimens.elevationLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SafeHerDimens.spaceSm)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(SafeHerDimens.iconMd)
                )
            }

            Spacer(modifier = Modifier.height(SafeHerDimens.spaceSm))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SafeHerTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(SafeHerDimens.spaceXxxs))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SafeHerTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
