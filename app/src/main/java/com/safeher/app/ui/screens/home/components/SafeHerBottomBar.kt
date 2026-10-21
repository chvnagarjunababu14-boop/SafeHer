package com.safeher.app.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.R
import com.safeher.app.ui.screens.home.HomeTab
import com.safeher.app.ui.theme.SafeHerBorderLavender
import com.safeher.app.ui.theme.SafeHerPurpleContainer
import com.safeher.app.ui.theme.SafeHerPurplePrimary
import com.safeher.app.ui.theme.SafeHerSurfaceWhite
import com.safeher.app.ui.theme.SafeHerTextMuted

data class NavItem(
    val tab: HomeTab,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun SafeHerBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(HomeTab.HOME, R.string.nav_home, Icons.Default.Home),
        NavItem(HomeTab.MAP, R.string.nav_map, Icons.Default.Map),
        NavItem(HomeTab.JOURNEYS, R.string.nav_journeys, Icons.AutoMirrored.Filled.DirectionsWalk),
        NavItem(HomeTab.CONTACTS, R.string.nav_contacts, Icons.Default.People),
        NavItem(HomeTab.MORE, R.string.nav_more, Icons.Default.MoreHoriz)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SafeHerBorderLavender, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = SafeHerSurfaceWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = selectedTab == item.tab
                val label = stringResource(item.labelRes)

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) SafeHerPurplePrimary else SafeHerTextMuted,
                    label = "BottomNavIconColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            role = Role.Tab,
                            onClickLabel = label,
                            onClick = { onTabSelected(item.tab) }
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SafeHerPurpleContainer else SafeHerSurfaceWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = iconColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
