package com.example.pain_tracker.ui.screens

import android.R.attr.background
import android.R.id.background
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.pain_tracker.R // Ensure you import your R file
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider


// ── colour palette ────────────────────────────────────────────────────────────
private val BgColor      = Color(0xFFFCF4EC) //cream 0xFFFCF4EC
private val Surface1    = Color(0xFF7A9B6A)
private val Surface2    = Color(0xFF725241)
private val Border      = Color(0xFF6B3820)
private val TextPrimary = Color(0xFF6B3820)

private val TextOnSurface = Color(0xFFFFFFFF)

private val TextMuted   = Color(0xFF887D7D)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { authTask ->
                        isLoading = false
                        if (authTask.isSuccessful) onLoginSuccess()
                        else errorMessage = authTask.exception?.localizedMessage ?: "Google Auth Failed"
                    }
            } catch (e: ApiException) {
                isLoading = false
                errorMessage = "Google Sign-In failed: ${e.message}"
            }
        } else {
            isLoading = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        // --- FERN 1: Top Left ---
        Image(
            painter = painterResource(id = R.drawable.fern_bg), // your png name
            contentDescription = null,
            modifier = Modifier
                .size(600.dp) // Make it big so it "peeks" in
                .align(Alignment.TopStart)
                .offset(x = (-190).dp, y = (-330).dp) // Push it slightly off-screen
                .graphicsLayer(rotationZ = 170f, alpha = 0.5f),
                 // Rotate and fade
            contentScale = ContentScale.Fit
        )

        // --- FERN 2: Bottom Right ---
        Image(
            painter = painterResource(id = R.drawable.fern_bg),
            contentDescription = null,
            modifier = Modifier
                .size(650.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 170.dp, y = 230.dp) // Peek out from bottom
                .graphicsLayer(rotationZ = -5f, alpha = 0.5f), // Flip it around
            contentScale = ContentScale.Fit
        )
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "perennial", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "bloom through the seasons", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)

        Spacer(modifier = Modifier.height(32.dp))

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Border,
                unfocusedBorderColor = Surface2, // Use Surface2 for the border
                cursorColor = Border,
                focusedLabelColor = Border
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Border,
                unfocusedBorderColor = Surface2, // Use Surface2 for the border
                cursorColor = Border,
                focusedLabelColor = Border
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    isLoading = true
                    errorMessage = null
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) onLoginSuccess()
                            else errorMessage = task.exception?.localizedMessage ?: "login failed"
                        }
                } else {
                    errorMessage = "please enter email and password."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Surface1,
                contentColor = TextOnSurface
            )
        ) {
            Text("sign in with email")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "— OR —", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                isLoading = true
                errorMessage = null
                googleSignInClient.signOut().addOnCompleteListener {
                    googleAuthLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            border = BorderStroke(1.dp, Surface1), // Matches your Dashboard borders
            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgColor,contentColor = Surface1)
        ) {
            Text("sign in with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                isLoading = true
                errorMessage = null
                auth.signInAnonymously()
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) onLoginSuccess()
                        else errorMessage = task.exception?.localizedMessage ?: "anonymous login failed"
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors( containerColor = Color.Transparent, contentColor = TextMuted)
        ) {
            Text("continue without logging in")
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
    }
}