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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeher.app.R
import com.safeher.app.ui.components.RoleBadge
import com.safeher.app.ui.components.SafeHerRoleTone
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerDimens
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun HomeHeader(
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRoleBadge: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = SafeHerDimens.screenHorizontal, vertical = SafeHerDimens.spaceXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(SafeHerDimens.brandMark)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SafeHerPurplePrimary, SafeHerPinkAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = stringResource(R.string.app_name),
                    tint = SafeHerSurfaceWhite,
                    modifier = Modifier.size(SafeHerDimens.iconMd)
                )
            }

            Spacer(modifier = Modifier.width(SafeHerDimens.spaceSm))

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.greeting_user),
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeHerTextSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showRoleBadge) {
                RoleBadge(tone = SafeHerRoleTone.USER)
                Spacer(modifier = Modifier.width(SafeHerDimens.spaceXs))
            }
            Box(
                modifier = Modifier
                    .size(SafeHerDimens.touchMin)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.profile_avatar_desc),
                        onClick = onProfileClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SafeHerSurfaceWhite)
                        .border(1.5.dp, SafeHerBorderLavender, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.profile_avatar_desc),
                        tint = SafeHerPurplePrimary,
                        modifier = Modifier.size(SafeHerDimens.iconMd)
                    )
                }
            }
        }
    }
}
