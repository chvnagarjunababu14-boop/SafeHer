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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.R
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPinkAccent
import com.safeher.app.ui.theme.SafeHerPurpleDark
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextPrimary
import com.safeher.app.ui.theme.SafeHerTextSecondary

@Composable
fun HomeHeader(
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shield brand mark
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SafeHerSurfaceWhite)
                    .border(1.dp, SafeHerBorderLavender, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = stringResource(R.string.app_name),
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = SafeHerPurpleDark,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SafeHerPinkAccent)
                    )
                }

                // Neutral non-fabricated greeting
                Text(
                    text = stringResource(R.string.greeting_user),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeHerTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Profile / Settings Action Button (Min 48dp touch target)
        Box(
            modifier = Modifier
                .size(48.dp)
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
                    .border(1.5.dp, SafeHerPurplePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.profile_avatar_desc),
                    tint = SafeHerPurplePrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
