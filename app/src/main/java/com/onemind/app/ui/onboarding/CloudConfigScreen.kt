package com.onemind.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudConfigScreen(
    baseUrl: String,
    apiKey: String,
    modelName: String,
    supportsVision: Boolean,
    testResult: CloudTestResult?,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onVisionToggle: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Provider") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configure your cloud AI provider",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Works with OpenAI, Groq, Together.ai, local Ollama, or any OpenAI-compatible API.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = onModelNameChanged,
                label = { Text("Model Name") },
                placeholder = { Text("gpt-4o-mini") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Supports vision (image input)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = supportsVision,
                    onCheckedChange = onVisionToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test connection button + result
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()
                        && testResult != CloudTestResult.TESTING
                ) {
                    Text("Test Connection")
                }

                when (testResult) {
                    CloudTestResult.TESTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    CloudTestResult.SUCCESS -> Text("Connected!", color = MaterialTheme.colorScheme.primary)
                    CloudTestResult.FAILED -> Text("Failed", color = MaterialTheme.colorScheme.error)
                    null -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = testResult == CloudTestResult.SUCCESS
            ) {
                Text("Use This Provider")
            }
        }
    }
}
