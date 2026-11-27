package com.karuhundeveloper.notification.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.karuhundeveloper.notification.R
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.ui.component.AppIcon
import com.karuhundeveloper.notification.ui.component.EmptyState
import com.karuhundeveloper.notification.ui.component.InlineNotice
import com.karuhundeveloper.notification.ui.component.LoadingBlock
import com.karuhundeveloper.notification.ui.component.NoticeTone
import com.karuhundeveloper.notification.ui.component.PrimaryBar
import com.karuhundeveloper.notification.ui.component.ScreenHeader
import com.karuhundeveloper.notification.ui.component.SectionLabel
import com.karuhundeveloper.notification.ui.theme.RelayMonoSmall
import com.karuhundeveloper.notification.ui.theme.RelayShapes
import com.karuhundeveloper.notification.ui.theme.RelayTheme
import com.karuhundeveloper.notification.ui.viewmodel.AppInfoItem
import com.karuhundeveloper.notification.ui.viewmodel.AppPickerViewModel
import com.karuhundeveloper.notification.ui.viewmodel.RulesViewModel

private val HTTP_METHODS = listOf("POST", "GET", "PUT", "PATCH", "DELETE")
private val gson = Gson()

@Composable
fun AddRuleScreen(
    onNavigateBack: () -> Unit,
    viewModel: RulesViewModel = viewModel(),
    appPickerViewModel: AppPickerViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = RelayTheme.colors

    var ruleName by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var apiUrl by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("POST") }
    var autoRetry by remember { mutableStateOf(false) }
    var customHeaders by remember { mutableStateOf("") }

    val allApps by appPickerViewModel.installedApps.collectAsStateWithLifecycle()
    val isAppsLoading by appPickerViewModel.isLoading.collectAsStateWithLifecycle()
    val appsError by appPickerViewModel.error.collectAsStateWithLifecycle()
    var appSearchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    var ruleNameError by remember { mutableStateOf<String?>(null) }
    var targetAppsError by remember { mutableStateOf<String?>(null) }
    var apiUrlError by remember { mutableStateOf<String?>(null) }
    var headersError by remember { mutableStateOf<String?>(null) }

    val appLabels = remember(allApps) {
        allApps.associate { it.packageName to it.name }
    }

    fun validate(): Boolean {
        var ok = true

        ruleNameError = if (ruleName.isBlank()) {
            ok = false
            context.getString(R.string.rule_name_required)
        } else {
            null
        }

        targetAppsError = if (selectedApps.isEmpty()) {
            ok = false
            context.getString(R.string.sources_required)
        } else {
            null
        }

        apiUrlError = when {
            apiUrl.isBlank() -> {
                ok = false
                context.getString(R.string.api_url_required)
            }

            !apiUrl.startsWith("http://") && !apiUrl.startsWith("https://") -> {
                ok = false
                context.getString(R.string.api_url_invalid_scheme)
            }

            else -> null
        }

        headersError = if (customHeaders.isNotBlank()) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(customHeaders, type)
                null
            } catch (e: JsonSyntaxException) {
                ok = false
                context.getString(R.string.headers_invalid_json)
            }
        } else {
            null
        }

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
        containerColor = colors.background,
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.new_rule),
                subtitle = stringResource(R.string.configure_forwarding_rule),
                onBack = onNavigateBack
            )
        },
        bottomBar = {
            PrimaryBar(
                label = stringResource(R.string.save_rule),
                onClick = { onSave() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            FieldLabel(stringResource(R.string.rule_name))
            Spacer(Modifier.height(6.dp))
            RelayTextField(
                value = ruleName,
                onValueChange = {
                    ruleName = it
                    ruleNameError = null
                },
                placeholder = stringResource(R.string.rule_name_placeholder),
                error = ruleNameError,
                imeAction = ImeAction.Next
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel(text = stringResource(R.string.sources))
            Spacer(Modifier.height(12.dp))
            SourceSelector(
                selectedApps = selectedApps,
                labelFor = { packageName -> appLabels[packageName] ?: packageName.substringAfterLast('.') },
                onOpenPicker = { showAppPicker = true },
                onRemoveApp = { packageName -> selectedApps = selectedApps - packageName },
                error = targetAppsError
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel(text = stringResource(R.string.destination))
            Spacer(Modifier.height(12.dp))

            FieldLabel(stringResource(R.string.method))
            Spacer(Modifier.height(6.dp))
            MethodSelector(
                selected = selectedMethod,
                onSelect = { selectedMethod = it }
            )

            Spacer(Modifier.height(12.dp))
            FieldLabel(stringResource(R.string.endpoint_url))
            Spacer(Modifier.height(6.dp))
            RelayTextField(
                value = apiUrl,
                onValueChange = {
                    apiUrl = it
                    apiUrlError = null
                },
                placeholder = stringResource(R.string.api_url_placeholder),
                error = apiUrlError,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                capitalization = KeyboardCapitalization.None
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel(text = stringResource(R.string.delivery))
            Spacer(Modifier.height(12.dp))
            RetryRow(
                autoRetry = autoRetry,
                onCheckedChange = { autoRetry = it }
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel(text = stringResource(R.string.headers))
            Spacer(Modifier.height(12.dp))
            RelayTextField(
                value = customHeaders,
                onValueChange = {
                    customHeaders = it
                    headersError = null
                },
                placeholder = stringResource(R.string.headers_json_placeholder),
                error = headersError,
                minLines = 4,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Default,
                capitalization = KeyboardCapitalization.None
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.headers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            allApps = allApps,
            isLoading = isAppsLoading,
            error = appsError,
            selectedApps = selectedApps,
            searchQuery = appSearchQuery,
            onSearchQueryChange = { appSearchQuery = it },
            onToggleApp = { packageName ->
                selectedApps = if (packageName in selectedApps) {
                    selectedApps - packageName
                } else {
                    selectedApps + packageName
                }
                targetAppsError = null
            },
            onRetry = appPickerViewModel::refresh,
            onDismiss = {
                showAppPicker = false
                appSearchQuery = ""
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = RelayTheme.colors.ink
    )
}

@Composable
private fun RelayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String? = null,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    modifier: Modifier = Modifier
) {
    val colors = RelayTheme.colors

    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted.copy(alpha = 0.7f)
                )
            },
            isError = error != null,
            minLines = minLines,
            singleLine = minLines == 1,
            shape = RelayShapes.small,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = capitalization
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.ink,
                unfocusedBorderColor = colors.hairline,
                cursorColor = colors.ink,
                errorBorderColor = colors.error,
                errorContainerColor = colors.surface,
                errorTextColor = colors.ink,
                focusedPlaceholderColor = colors.inkMuted.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = colors.inkMuted.copy(alpha = 0.7f)
            )
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error
            )
        }
    }
}

@Composable
private fun MethodSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = RelayTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HTTP_METHODS.forEach { method ->
            val isSelected = method == selected
            Surface(
                onClick = { onSelect(method) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                shape = RelayShapes.extraSmall,
                color = if (isSelected) colors.ink else colors.surface,
                border = if (isSelected) null else BorderStroke(1.dp, colors.hairline)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = method,
                        style = RelayMonoSmall,
                        color = if (isSelected) colors.background else colors.inkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSelector(
    selectedApps: Set<String>,
    labelFor: (String) -> String,
    onOpenPicker: () -> Unit,
    onRemoveApp: (String) -> Unit,
    error: String?
) {
    val colors = RelayTheme.colors
    val borderColor = if (error != null) colors.error else colors.hairline

    Column {
        if (selectedApps.isEmpty()) {
            Surface(
                onClick = onOpenPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RelayShapes.small,
                color = colors.surface,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.select_target_apps_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkMuted
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RelayShapes.small)
                    .border(1.dp, borderColor, RelayShapes.small)
            ) {
                selectedApps.forEachIndexed { index, packageName ->
                    if (index > 0) HorizontalDivider(color = colors.hairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = packageName,
                            label = labelFor(packageName),
                            size = 30.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = labelFor(packageName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = packageName,
                                style = RelayMonoSmall,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onRemoveApp(packageName) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove),
                                tint = colors.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.hairline)
                Surface(
                    onClick = onOpenPicker,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_apps),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.ink
                        )
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error
            )
        }
    }
}

@Composable
private fun RetryRow(
    autoRetry: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = RelayTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.automatic_retry),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.automatic_retry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = autoRetry,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.ink,
                checkedBorderColor = colors.ink,
                uncheckedThumbColor = colors.inkMuted,
                uncheckedTrackColor = colors.surfaceRaised,
                uncheckedBorderColor = colors.hairline
            )
        )
    }
}

@Composable
private fun AppPickerDialog(
    allApps: List<AppInfoItem>,
    isLoading: Boolean,
    error: String?,
    selectedApps: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = RelayTheme.colors
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RelayShapes.medium,
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.select_target_apps),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = colors.ink
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                RelayTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.search_apps),
                    imeAction = ImeAction.Search,
                    capitalization = KeyboardCapitalization.None
                )
                Spacer(Modifier.height(12.dp))

                when {
                    isLoading && allApps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingBlock(label = stringResource(R.string.loading_apps))
                        }
                    }

                    error != null && allApps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            InlineNotice(
                                title = stringResource(R.string.error_reading_apps),
                                body = error,
                                tone = NoticeTone.Error,
                                actionLabel = stringResource(R.string.try_again),
                                onAction = onRetry
                            )
                        }
                    }

                    filteredApps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                title = stringResource(R.string.no_apps_found),
                                body = stringResource(R.string.no_apps_found_body)
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppPickerRow(
                                    app = app,
                                    selected = app.packageName in selectedApps,
                                    onToggle = { onToggleApp(app.packageName) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.hairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.selected_apps_count,
                            selectedApps.size,
                            selectedApps.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onDismiss,
                        shape = RelayShapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.background
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.done),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: AppInfoItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val colors = RelayTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RelayShapes.small)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            packageName = app.packageName,
            label = app.name,
            size = 32.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = RelayMonoSmall,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.ink,
                checkmarkColor = colors.background,
                uncheckedColor = colors.hairline
            )
        )
    }
}
