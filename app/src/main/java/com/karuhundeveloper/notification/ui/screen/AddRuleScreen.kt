package com.karuhundeveloper.notification.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.ui.component.MethodBadge
import com.karuhundeveloper.notification.ui.theme.*
import com.karuhundeveloper.notification.ui.viewmodel.AppPickerViewModel
import com.karuhundeveloper.notification.ui.viewmodel.RulesViewModel

private val HTTP_METHODS = listOf("POST", "GET", "PUT", "PATCH", "DELETE")
private val gson = Gson()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleScreen(
    onNavigateBack: () -> Unit,
    viewModel: RulesViewModel = viewModel(),
    appPickerViewModel: AppPickerViewModel = viewModel()
) {
    // ── Form state ────────────────────────────────────────────────────────────
    var ruleName by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var apiUrl by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("POST") }
    var autoRetry by remember { mutableStateOf(false) }
    var customHeaders by remember { mutableStateOf("") }

    // ── App Picker state ──────────────────────────────────────────────────────
    val allApps by appPickerViewModel.installedApps.collectAsStateWithLifecycle()
    val isAppsLoading by appPickerViewModel.isLoading.collectAsStateWithLifecycle()
    var appSearchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    // ── Validation error state ────────────────────────────────────────────────
    var ruleNameError by remember { mutableStateOf<String?>(null) }
    var targetAppsError by remember { mutableStateOf<String?>(null) }
    var apiUrlError by remember { mutableStateOf<String?>(null) }
    var headersError by remember { mutableStateOf<String?>(null) }

    // ── Method dropdown ───────────────────────────────────────────────────────
    var methodDropdownExpanded by remember { mutableStateOf(false) }

    // ── Snackbar ──────────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }

    fun validate(): Boolean {
        var ok = true

        ruleNameError = if (ruleName.isBlank()) { ok = false; "Rule name is required" } else null

        targetAppsError = if (selectedApps.isEmpty()) {
            ok = false; "At least one app must be selected"
        } else null

        apiUrlError = when {
            apiUrl.isBlank() -> { ok = false; "API URL is required" }
            !apiUrl.startsWith("http://") && !apiUrl.startsWith("https://") ->
                { ok = false; "URL must start with http:// or https://" }
            else -> null
        }

        headersError = if (customHeaders.isNotBlank()) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(customHeaders, type)
                null
            } catch (e: JsonSyntaxException) {
                ok = false; "Invalid JSON — must be a flat {\"key\": \"value\"} object"
            }
        } else null

        return ok
    }

    fun onSave() {
        if (!validate()) return

        val parsedHeaders: Map<String, String> = if (customHeaders.isBlank()) {
            emptyMap()
        } else {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(customHeaders, type) ?: emptyMap()
        }

        viewModel.insertRule(
            InterceptRule(
                ruleName = ruleName.trim(),
                targetApps = selectedApps.toList(),
                apiUrl = apiUrl.trim(),
                apiMethod = selectedMethod,
                customHeaders = parsedHeaders,
                autoRetry = autoRetry
            )
        )
        onNavigateBack()
    }

    Scaffold(
        containerColor = NavyDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New Rule", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Text("Configure a forwarding rule", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(IndigoAccent, CyanAccent)))
                    ) {
                        TextButton(
                            onClick = { onSave() },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Identification ────────────────────────────────────────────────
            FormSection(title = "Identification", icon = Icons.Default.Label) {
                RuleTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it; ruleNameError = null },
                    label = "Rule Name",
                    placeholder = "e.g. WhatsApp → Slack webhook",
                    error = ruleNameError,
                    imeAction = ImeAction.Next
                )
            }

            // ── Target Apps ───────────────────────────────────────────────────
            FormSection(title = "Target Apps", icon = Icons.Default.Apps) {
                AppSelector(
                    selectedApps = selectedApps,
                    onOpenPicker = { showAppPicker = true },
                    onRemoveApp = { pkg -> selectedApps = selectedApps - pkg },
                    isError = targetAppsError != null
                )

                AnimatedVisibility(visible = targetAppsError != null, enter = fadeIn() + expandVertically()) {
                    targetAppsError?.let {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(it, fontSize = 11.sp, color = ErrorRed)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "💡  To test the pipeline, select this app and use the Send Dummy button.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            // ── Endpoint ──────────────────────────────────────────────────────
            FormSection(title = "API Endpoint", icon = Icons.Default.Link) {
                RuleTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it; apiUrlError = null },
                    label = "API URL",
                    placeholder = "https://hooks.slack.com/services/…",
                    error = apiUrlError,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.None
                )

                Spacer(Modifier.height(8.dp))

                // Method dropdown
                Text(
                    "HTTP METHOD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IndigoLight,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = methodDropdownExpanded,
                    onExpandedChange = { methodDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMethod,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodDropdownExpanded)
                        },
                        leadingIcon = {
                            MethodBadge(method = selectedMethod)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ruleTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = methodDropdownExpanded,
                        onDismissRequest = { methodDropdownExpanded = false },
                        containerColor = NavyElevated
                    ) {
                        HTTP_METHODS.forEach { method ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MethodBadge(method = method)
                                        Spacer(Modifier.width(10.dp))
                                        Text(method, color = TextPrimary)
                                    }
                                },
                                onClick = {
                                    selectedMethod = method
                                    methodDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            FormSection(title = "Delivery", icon = Icons.Default.Refresh) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic retry", modifier = Modifier.weight(1f), color = TextPrimary)
                    Switch(checked = autoRetry, onCheckedChange = { autoRetry = it })
                }
                Text("Enable only when the endpoint deduplicates X-Delivery-Id. Network errors and HTTP 408/429/5xx retry up to 10 attempts.", color = TextSecondary, fontSize = 12.sp)
            }

            // ── Custom Headers ────────────────────────────────────────────────
            FormSection(title = "Custom Headers", icon = Icons.Default.Code) {
                RuleTextField(
                    value = customHeaders,
                    onValueChange = { customHeaders = it; headersError = null },
                    label = "Headers JSON (optional)",
                    placeholder = "{\n  \"Authorization\": \"Bearer token\",\n  \"X-Source\": \"android\"\n}",
                    error = headersError,
                    minLines = 4,
                    imeAction = ImeAction.Default,
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None
                )
                AnimatedVisibility(
                    visible = headersError == null && customHeaders.isNotBlank(),
                    enter = fadeIn() + expandVertically()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Valid JSON", fontSize = 11.sp, color = SuccessGreen)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            allApps = allApps,
            isLoading = isAppsLoading,
            selectedApps = selectedApps,
            searchQuery = appSearchQuery,
            onSearchQueryChange = { appSearchQuery = it },
            onToggleApp = { pkg ->
                selectedApps = if (pkg in selectedApps) selectedApps - pkg else selectedApps + pkg
                targetAppsError = null
            },
            onDismiss = {
                showAppPicker = false
                appSearchQuery = ""
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared form sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndigoLight,
                    letterSpacing = 0.8.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ruleTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor       = TextPrimary,
    unfocusedTextColor     = TextPrimary,
    focusedBorderColor     = IndigoAccent,
    unfocusedBorderColor   = Divider,
    focusedLabelColor      = IndigoLight,
    unfocusedLabelColor    = TextSecondary,
    cursorColor            = IndigoAccent,
    focusedContainerColor  = NavyElevated,
    unfocusedContainerColor= NavyElevated,
    errorBorderColor       = ErrorRed,
    errorLabelColor        = ErrorRed,
    errorTextColor         = TextPrimary
)

@Composable
private fun RuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    error: String? = null,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
            isError = error != null,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ruleTextFieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = capitalization
            )
        )
        AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically()) {
            error?.let {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, fontSize = 11.sp, color = ErrorRed)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppSelector(
    selectedApps: Set<String>,
    onOpenPicker: () -> Unit,
    onRemoveApp: (String) -> Unit,
    isError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyElevated)
            .border(
                width = 1.dp,
                color = if (isError) ErrorRed else Divider,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onOpenPicker() }
            .padding(12.dp)
    ) {
        if (selectedApps.isEmpty()) {
            Text(
                "Tap to select target apps...",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedApps.forEach { pkg ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveApp(pkg) },
                        label = {
                            Text(
                                pkg.substringAfterLast('.'),
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(14.dp),
                                tint = TextSecondary
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = NavyDeep,
                            labelColor = TextPrimary
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            selected = true,
                            enabled = true,
                            borderColor = Divider,
                            borderWidth = 1.dp,
                            selectedBorderColor = IndigoAccent,
                            selectedBorderWidth = 1.dp
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    allApps: List<com.karuhundeveloper.notification.ui.viewmodel.AppInfoItem>,
    isLoading: Boolean,
    selectedApps: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.8f),
        content = {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NavyCard,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Select Target Apps",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search apps...", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IndigoLight) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ruleTextFieldColors(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    if (isLoading && allApps.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = IndigoAccent)
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No apps found", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isSelected = app.packageName in selectedApps
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) IndigoAccent.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { onToggleApp(app.packageName) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(NavyElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            app.name.firstOrNull()?.toString() ?: "?",
                                            color = IndigoLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(app.packageName, color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleApp(app.packageName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = IndigoAccent,
                                            uncheckedColor = Divider
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Done", color = IndigoAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}
