package com.gitcodera.presentation.navigation

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitcodera.data.model.Codespace
import com.gitcodera.data.model.Repository
import com.gitcodera.data.model.isValidCodespacesSecretName
import com.gitcodera.R
import com.gitcodera.presentation.auth.LoginViewModel
import com.gitcodera.presentation.codespace.CodespaceViewModel
import com.gitcodera.presentation.codespace.CodespaceWebView
import com.gitcodera.presentation.codespace.codespaceUrl
import com.gitcodera.presentation.repos.RepoViewModel
import com.gitcodera.presentation.settings.SettingsViewModel
import java.util.Locale

private enum class AppTab(val label: String) {
    Home("Home"),
    Repositories("Repos"),
    Codespaces("Codespaces"),
    Settings("Settings"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppNavigation(
    oauthCallback: Uri? = null,
    onOAuthCallbackHandled: () -> Unit = {},
) {
    val loginViewModel: LoginViewModel = hiltViewModel()
    val repoViewModel: RepoViewModel = hiltViewModel()
    val codespaceViewModel: CodespaceViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val login by loginViewModel.state.collectAsState()
    val repos by repoViewModel.state.collectAsState()
    val codespaces by codespaceViewModel.state.collectAsState()
    val settings by settingsViewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var editorUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRepository by remember { mutableStateOf<Repository?>(null) }
    var selectedCodespace by remember { mutableStateOf<Codespace?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(oauthCallback) {
        oauthCallback?.let {
            loginViewModel.handleBackendCallback(it)
            onOAuthCallbackHandled()
        }
    }
    LaunchedEffect(login.browserAuthorizationUrl) {
        val url = login.browserAuthorizationUrl ?: return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            loginViewModel.clearBrowserAuthorizationUrl()
        } catch (error: Exception) {
            loginViewModel.authorizationLaunchFailed(
                error.message ?: "Could not open GitHub sign-in.",
            )
        }
    }

    LaunchedEffect(login.user, tab) {
        if (login.user != null) {
            when (tab) {
                AppTab.Home -> {
                    repoViewModel.load()
                    codespaceViewModel.load()
                }
                AppTab.Repositories -> repoViewModel.load()
                AppTab.Codespaces -> codespaceViewModel.load()
                AppTab.Settings -> {
                    repoViewModel.load()
                    settingsViewModel.loadSecrets()
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(
        repos.error,
        repos.message,
        codespaces.error,
        codespaces.message,
        settings.error,
        settings.message,
    ) {
        val message = repos.error ?: repos.message ?: codespaces.error ?: codespaces.message
            ?: settings.error ?: settings.message
        if (message != null) snackbar.showSnackbar(message)
        if (repos.error != null || repos.message != null) repoViewModel.clearMessages()
        if (codespaces.error != null) codespaceViewModel.clearError()
        if (settings.error != null || settings.message != null) settingsViewModel.clearMessage()
    }

    if (login.isLoading && login.user == null && login.authorization == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Connecting to GitHub…")
        }
        return
    }

    if (login.user == null) {
        LoginScreen(
            isLoading = login.isLoading,
            authorization = login.authorization,
            isAuthorizing = login.isAuthorizing,
            backendOAuthConfigured = loginViewModel.backendOAuthConfigured,
            backendHandoffPending = login.backendHandoffPending,
            error = login.error,
            onSignIn = loginViewModel::startLogin,
            onOpenVerification = { url ->
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            },
            onRetry = if (login.backendHandoffPending) {
                loginViewModel::retryBackendHandoff
            } else {
                loginViewModel::startLogin
            },
        )
        return
    }

    val currentUser = login.user ?: return
    if (editorUrl != null) {
        CodespaceWebView(url = editorUrl!!, onClose = { editorUrl = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.gitcodera_logo),
                            contentDescription = "GitCoderA logo",
                            modifier = Modifier.size(32.dp),
                        )
                        Text("GitCoderA")
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AsyncImage(
                            model = currentUser.avatarUrl,
                            contentDescription = "${currentUser.login} avatar",
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                        Text(currentUser.login)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { destination ->
                    val icon = when (destination) {
                        AppTab.Home -> Icons.Default.Home
                        AppTab.Repositories -> Icons.Default.Storage
                        AppTab.Codespaces -> Icons.Default.Code
                        AppTab.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination },
                        icon = { Icon(icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            AppTab.Home -> HomeScreen(
                user = currentUser.login,
                repoCount = repos.repositories.size,
                codespaceCount = codespaces.codespaces.size,
                modifier = Modifier.padding(padding),
                onRepositories = { tab = AppTab.Repositories },
                onCodespaces = { tab = AppTab.Codespaces },
            )
            AppTab.Repositories -> RepositoryScreen(
                repositories = repos.repositories,
                isLoading = repos.isLoading,
                isCreating = repos.isCreating,
                modifier = Modifier.padding(padding),
                onRefresh = repoViewModel::load,
                onCreate = repoViewModel::create,
                onCreateCodespace = { repo -> codespaceViewModel.create(repo.fullName) },
                onDetails = { selectedRepository = it },
            )
            AppTab.Codespaces -> CodespacesScreen(
                codespaces = codespaces.codespaces,
                isLoading = codespaces.isLoading,
                modifier = Modifier.padding(padding),
                onRefresh = codespaceViewModel::load,
                onOpen = { codespace ->
                    val targetUrl = codespaceUrl(codespace)
                    if (targetUrl == null) {
                        coroutineScope.launch {
                            snackbar.showSnackbar("GitHub did not provide a valid Codespace URL.")
                        }
                    } else {
                        editorUrl = targetUrl
                    }
                },
                onStart = codespaceViewModel::start,
                onStop = codespaceViewModel::stop,
                onDelete = codespaceViewModel::delete,
                onDetails = { selectedCodespace = it },
            )
            AppTab.Settings -> SettingsScreen(
                user = currentUser.login,
                repositories = repos.repositories,
                secrets = settings.secrets.map { it.name }.toSet(),
                isLoading = settings.isLoading,
                isSaving = settings.isSaving,
                message = settings.message,
                modifier = Modifier.padding(padding),
                onSaveKey = settingsViewModel::saveSecret,
                onDeleteKey = settingsViewModel::deleteSecret,
                onRefresh = settingsViewModel::loadSecrets,
                onSignOut = {
                    CookieManager.getInstance().removeAllCookies {
                        CookieManager.getInstance().flush()
                        repoViewModel.resetAll()
                        codespaceViewModel.resetAll()
                        loginViewModel.signOut()
                    }
                },
            )
        }
    }
    selectedRepository?.let { repository ->
        RepositoryDetailsDialog(
            repository = repository,
            onDismiss = { selectedRepository = null },
            onOpenRepository = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repository.htmlUrl)))
                } catch (_: android.content.ActivityNotFoundException) {
                    coroutineScope.launch {
                        snackbar.showSnackbar("No browser is available to open the repository.")
                    }
                }
            },
            onCreateCodespace = {
                codespaceViewModel.create(repository.fullName)
                selectedRepository = null
                tab = AppTab.Codespaces
            },
        )
    }
    selectedCodespace?.let { codespace ->
        CodespaceDetailsDialog(
            codespace = codespace,
            onDismiss = { selectedCodespace = null },
            onOpen = {
                val targetUrl = codespaceUrl(codespace)
                if (targetUrl == null) {
                    coroutineScope.launch {
                        snackbar.showSnackbar("GitHub did not provide a valid Codespace URL.")
                    }
                } else {
                    editorUrl = targetUrl
                }
                selectedCodespace = null
            },
        )
    }
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    authorization: com.gitcodera.domain.repository.DeviceAuthorization?,
    isAuthorizing: Boolean,
    backendOAuthConfigured: Boolean,
    backendHandoffPending: Boolean,
    error: String?,
    onSignIn: () -> Unit,
    onOpenVerification: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.gitcodera_logo),
            contentDescription = "GitCoderA logo",
            modifier = Modifier.size(96.dp).padding(bottom = 12.dp),
        )
        Text("GitCoderA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Your mobile GitHub development environment", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        if (authorization != null) {
            Text("Enter this code on GitHub:", style = MaterialTheme.typography.titleMedium)
            Text(
                authorization.userCode,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(12.dp),
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = { onOpenVerification(authorization.verificationUri) }) {
                Text("Open ${authorization.verificationUri}")
            }
            Spacer(Modifier.height(12.dp))
            if (isAuthorizing) CircularProgressIndicator()
        }
        if (isAuthorizing && authorization == null) {
            Text(
                if (backendHandoffPending) {
                    "Complete sign-in in the secure browser tab. Return here after GitHub redirects back."
                } else {
                    "Waiting for GitHub authorization…"
                },
            )
        }
        Button(
            onClick = if (error != null && backendHandoffPending) onRetry
                else if (authorization == null && !backendHandoffPending) onSignIn else onRetry,
            enabled = !isLoading && (!isAuthorizing || error != null),
        ) {
            Text(
                when {
                    error != null && backendHandoffPending -> "Retry secure handoff"
                    authorization != null -> "Check authorization"
                    backendOAuthConfigured -> "Sign in securely with GitHub"
                    else -> "Sign in with GitHub Device Flow"
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    user: String,
    repoCount: Int,
    codespaceCount: Int,
    modifier: Modifier = Modifier,
    onRepositories: () -> Unit,
    onCodespaces: () -> Unit,
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Welcome, $user", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth().clickable(onClick = onRepositories)) {
            Column(Modifier.padding(20.dp)) {
                Text("Repositories", style = MaterialTheme.typography.titleLarge)
                Text("$repoCount loaded · browse or create a repository")
            }
        }
        Card(Modifier.fillMaxWidth().clickable(onClick = onCodespaces)) {
            Column(Modifier.padding(20.dp)) {
                Text("Codespaces", style = MaterialTheme.typography.titleLarge)
                Text("$codespaceCount loaded · manage your cloud development environments")
            }
        }
    }
}

@Composable
private fun RepositoryScreen(
    repositories: List<Repository>,
    isLoading: Boolean,
    isCreating: Boolean,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onCreate: (String, String, Boolean, Boolean, String, String) -> Unit,
    onCreateCodespace: (Repository) -> Unit,
    onDetails: (Repository) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Repositories", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                TextButton(onClick = { showCreate = true }) { Text("New") }
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search repositories") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (isLoading || isCreating) LinearProgressIndicatorCompat()
        if (!isLoading && repositories.isEmpty()) {
            Text("No repositories found. Create one or refresh the list.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(repositories.filter {
                it.fullName.contains(search, ignoreCase = true) ||
                    it.description.orEmpty().contains(search, ignoreCase = true)
            }, key = { it.id }) { repository ->
                Card(
                    Modifier.fillMaxWidth().clickable { onDetails(repository) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(repository.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(repository.description ?: if (repository.isPrivate) "Private repository" else "Public repository")
                        TextButton(onClick = { onCreateCodespace(repository) }) {
                            Text("Create Codespace")
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateRepositoryDialog(
            isCreating = isCreating,
            onDismiss = { showCreate = false },
            onCreate = { name, description, isPrivate, readme, gitignore, license ->
                onCreate(name, description, isPrivate, readme, gitignore, license)
                showCreate = false
            },
        )
    }
}

@Composable
private fun RepositoryDetailsDialog(
    repository: Repository,
    onDismiss: () -> Unit,
    onOpenRepository: () -> Unit,
    onCreateCodespace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(repository.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(repository.description?.takeIf(String::isNotBlank) ?: "No description")
                Text(if (repository.isPrivate) "Private repository" else "Public repository")
                repository.defaultBranch?.let { Text("Default branch: $it") }
                repository.language?.let { Text("Language: $it") }
                repository.updatedAt?.let { Text("Updated: $it") }
                if (repository.archived) Text("Archived repositories cannot start a Codespace.")
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateCodespace, enabled = !repository.archived) {
                Text("Create Codespace")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenRepository) { Text("GitHub") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun CreateRepositoryDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean, Boolean, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(true) }
    var readme by rememberSaveable { mutableStateOf(true) }
    var gitignore by rememberSaveable { mutableStateOf("") }
    var license by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create repository") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") })
                OutlinedTextField(gitignore, { gitignore = it }, label = { Text(".gitignore template (optional)") }, singleLine = true)
                OutlinedTextField(license, { license = it }, label = { Text("License template (optional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Private")
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text("README")
                    Switch(checked = readme, onCheckedChange = { readme = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description, isPrivate, readme, gitignore, license) },
                enabled = !isCreating,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LinearProgressIndicatorCompat() {
    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun CodespacesScreen(
    codespaces: List<Codespace>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpen: (Codespace) -> Unit,
    onStart: (String) -> Unit,
    onStop: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDetails: (Codespace) -> Unit,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Codespaces", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (isLoading) LinearProgressIndicatorCompat()
        if (!isLoading && codespaces.isEmpty()) {
            Text("No Codespaces found. Create one from a repository.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(codespaces, key = { it.name }) { codespace ->
                Card(
                    Modifier.fillMaxWidth().clickable { onDetails(codespace) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(codespace.name, style = MaterialTheme.typography.titleMedium)
                        Text("${codespace.repository?.fullName ?: "Repository"} · ${codespace.state}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpen(codespace) }) { Text("Open in app") }
                            if (codespace.state.equals("Shutdown", ignoreCase = true) ||
                                codespace.state.equals("Stopped", ignoreCase = true)
                            ) {
                                OutlinedButton(onClick = { onStart(codespace.name) }) { Text("Start") }
                            } else if (codespace.state.equals("Available", ignoreCase = true) ||
                                codespace.state.equals("Running", ignoreCase = true)
                            ) {
                                OutlinedButton(onClick = { onStop(codespace.name) }) { Text("Stop") }
                            }
                            OutlinedButton(onClick = { onDelete(codespace.name) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodespaceDetailsDialog(
    codespace: Codespace,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val canOpen = codespace.state.equals("Available", ignoreCase = true) ||
        codespace.state.equals("Running", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(codespace.displayName ?: codespace.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("State: ${codespace.state}")
                Text("Repository: ${codespace.repository?.fullName ?: "Unknown"}")
                codespace.gitStatus?.let { status ->
                    val changes = buildList {
                        if (status.hasUncommittedChanges == true) add("uncommitted changes")
                        if (status.hasUntrackedFiles == true) add("untracked files")
                        if (status.hasUnpushedChanges == true) add("unpushed changes")
                    }
                    if (changes.isNotEmpty()) Text("Git: ${changes.joinToString()}")
                }
                codespace.machine?.let { machine ->
                    Text("Machine: ${machine.displayName ?: machine.name ?: "Unknown"}")
                    machine.cpus?.let { Text("CPUs: $it") }
                    machine.memoryInBytes?.let { Text("Memory: ${formatBytes(it)}") }
                    machine.storageInBytes?.let { Text("Storage: ${formatBytes(it)}") }
                }
                codespace.location?.let { Text("Location: $it") }
                codespace.devcontainerPath?.let { Text("Dev container: $it") }
                codespace.createdAt?.let { Text("Created: $it") }
                codespace.lastUsedAt?.let { Text("Last used: $it") }
                if (!canOpen) Text("Start this Codespace before opening its editor.")
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen, enabled = canOpen) { Text("Open editor") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val kib = bytes / 1_024.0
    if (kib < 1_024) return "%.1f MiB".format(Locale.ROOT, kib)
    return "%.1f GiB".format(Locale.ROOT, kib / 1_024)
}

@Composable
private fun SettingsScreen(
    user: String,
    repositories: List<Repository>,
    secrets: Set<String>,
    isLoading: Boolean,
    isSaving: Boolean,
    message: String?,
    modifier: Modifier = Modifier,
    onSaveKey: (String, String, Long) -> Unit,
    onDeleteKey: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    var secretName by rememberSaveable { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var selectedRepositoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var repositoryMenuExpanded by remember { mutableStateOf(false) }
    val selectedRepository = repositories.firstOrNull { it.id == selectedRepositoryId }
        ?: repositories.firstOrNull()
    val validSecretName = isValidCodespacesSecretName(secretName.trim())

    LaunchedEffect(message) {
        if (message != null) secretValue = ""
    }
    LaunchedEffect(repositories) {
        if (selectedRepositoryId == null) selectedRepositoryId = repositories.firstOrNull()?.id
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Signed in as $user") }
        item { Text("Codespaces secrets") }
        item {
            Text(
                "Secrets are encrypted with GitHub's public key and sent directly to GitHub. " +
                    "Their values cannot be read back after saving.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedRepository?.fullName ?: "Select a repository",
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { repositoryMenuExpanded = true }) { Text("Choose") }
                    DropdownMenu(
                        expanded = repositoryMenuExpanded,
                        onDismissRequest = { repositoryMenuExpanded = false },
                    ) {
                        repositories.forEach { repository ->
                            DropdownMenuItem(
                                text = { Text(repository.fullName) },
                                onClick = {
                                    selectedRepositoryId = repository.id
                                    repositoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = secretName,
                onValueChange = { secretName = it.uppercase(Locale.ROOT) },
                label = { Text("Secret name (for example, ANTHROPIC_API_KEY)") },
                supportingText = {
                    if (secretName.isNotBlank() && !validSecretName) {
                        Text("Use uppercase letters, digits and underscores; start with a letter or underscore.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = secretValue,
                onValueChange = { secretValue = it },
                label = { Text("Secret value") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = {
                    val repositoryId = selectedRepository?.id ?: return@Button
                    onSaveKey(secretName.trim(), secretValue, repositoryId)
                },
                enabled = !isSaving && selectedRepository != null &&
                    validSecretName && secretValue.isNotBlank(),
            ) {
                Text(if (isSaving) "Saving…" else "Save to GitHub")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Existing secrets")
                TextButton(onClick = onRefresh, enabled = !isLoading && !isSaving) { Text("Refresh") }
            }
        }
        if (isLoading) item { LinearProgressIndicatorCompat() }
        if (!isLoading && secrets.isEmpty()) item { Text("No Codespaces secrets found.") }
        items(secrets.sorted()) { name ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name)
                TextButton(onClick = { onDeleteKey(name) }, enabled = !isSaving) {
                    Text("Delete")
                }
            }
        }
        item {
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
