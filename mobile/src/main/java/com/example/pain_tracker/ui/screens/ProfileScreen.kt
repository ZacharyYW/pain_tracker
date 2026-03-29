package com.example.pain_tracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pain_tracker.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser

    // Extract dynamic user data based on who is signed in
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "anonymous user"

    // Helper to show temporary functionality for unbuilt screens
    val showToast = { message: String ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "profile",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgColor,
                    scrolledContainerColor = BgColor
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // ── user info header ──────────────────────────────────────────────────
            item {
                UserInfoCard(
                    name = displayName
                )
            }

            // ── account settings ──────────────────────────────────────────────────
            item {
                ProfileSection(title = "account settings") {
                    ProfileMenuItem(
                        icon = Icons.Default.Person,
                        text = "edit profile",
                        onClick = { showToast("edit profile coming soon") }
                    )
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.Lock,
                        text = "privacy & security",
                        onClick = { showToast("privacy settings coming soon") }
                    )
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.Notifications,
                        text = "notifications",
                        onClick = { showToast("notification preferences coming soon") }
                    )
                }
            }

            // ── support & about ───────────────────────────────────────────────────
            item {
                ProfileSection(title = "support") {
                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        text = "contact us",
                        onClick = { showToast("support center coming soon") }
                    )
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        text = "about & terms",
                        onClick = { showToast("perennial v1.0.0 terms") }
                    )
                }
            }

            // ── sign out button ───────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgColor)
                        .border(1.dp, PinkAccent, RoundedCornerShape(14.dp))
                        .clickable {
                            // 1. Sign out of Firebase
                            auth.signOut()
                            // 2. Clear Google Sign In cache (so it prompts account choice next time)
                            GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                            // 3. Trigger app state change to go back to LoginScreen
                            onSignOut()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = PinkAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "sign out",
                            color = PinkAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // version number indicator
                Text(
                    text = "version 1.0.0",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun UserInfoCard(name: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(0.5.dp, Border, RoundedCornerShape(14.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar generator based on first letter of name
        val initial = if (name.isNotBlank()) name.first().toString().uppercase() else "?"

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Surface2)
                .border(1.5.dp, BgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = TextOnSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = name.lowercase(),
            color = TextOnSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = title.lowercase(),
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgColor)
                .border(0.5.dp, Border, RoundedCornerShape(14.dp))
        ) {
            content()
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text.lowercase(),
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}