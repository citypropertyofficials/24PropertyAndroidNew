package com.example.myapplication.ui.screens.login

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.example.myapplication.BuildConfig
import com.example.myapplication.R
import org.koin.androidx.compose.koinViewModel
import com.example.myapplication.ui.common.AppFullScreenLoading
import com.example.myapplication.ui.common.AppPrimaryButton
import com.example.myapplication.ui.theme.PrimaryEnd
import com.example.myapplication.ui.theme.PrimaryStart
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (needsProfileCompletion: Boolean) -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState

    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.NavigateToMain -> {
                    onLoginSuccess(event.needsProfileCompletion)
                }
                is LoginEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PrimaryStart.copy(alpha = 0.15f),
                            PrimaryEnd.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            when (val state = uiState.value) {
                is LoginUiState.Loading -> {
                    AppFullScreenLoading()
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Logo
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(PrimaryStart, PrimaryEnd)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = stringResource(id = R.string.app_name),
                                modifier = Modifier.size(80.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(id = R.string.login_tagline),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        AppPrimaryButton(
                            text = stringResource(id = R.string.sign_in_with_google),
                            onClick = {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                (context as? Activity)?.let { activity ->
                                    scope.launch {
                                        try {
                                            val result = credentialManager.getCredential(
                                                request = request,
                                                context = activity
                                            )
                                            handleSignIn(result, viewModel)
                                        } catch (e: Exception) {
                                            Log.e("LoginScreen", "Credential error: ${e.message}")
                                            snackbarHostState.showSnackbar(
                                                e.localizedMessage ?: "Sign-in failed"
                                            )
                                        }
                                    }
                                }
                            },
                            isLoading = uiState.value is LoginUiState.Loading
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(id = R.string.login_footer),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun handleSignIn(
    result: GetCredentialResponse,
    viewModel: LoginViewModel
) {
    when (val credential = result.credential) {
        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    viewModel.signInWithGoogle(idToken)
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("LoginScreen", "Invalid Google ID token response", e)
                }
            }
        }
        else -> {
            Log.w("LoginScreen", "Unexpected credential type: ${credential.type}")
        }
    }
}
