package com.example.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.billing.PlayBillingManager
import com.example.cloud.CloudClient
import com.example.cloud.CloudSnapshot
import com.example.data.repository.QuoteRepository
import com.example.ui.theme.NavyPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    client: CloudClient,
    repository: QuoteRepository,
    billingManager: PlayBillingManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var logged by remember { mutableStateOf(client.isLoggedIn()) }
    var email by remember { mutableStateOf(client.email()) }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CloudSnapshot?>(null) }
    var lastResult by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val activityContext = LocalContext.current

    fun work(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                lastResult = block()
                snackbar.showSnackbar(lastResult)
            } catch (e: Exception) {
                lastResult = e.localizedMessage ?: "Operação online não concluída."
                snackbar.showSnackbar(lastResult)
            } finally { busy = false }
        }
    }

    fun googleLogin() {
        if (!client.googleLoginAvailable) return
        work {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activityContext)
                .getCredential(context = activityContext, request = request)
            val credential = result.credential as? CustomCredential
                ?: error("Credencial Google inválida.")
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                error("A conta selecionada não é uma identidade Google.")
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val message = if (logged) client.linkGoogleAccount(idToken) else client.googleLogin(idToken)
            logged = true
            email = client.email()
            val subscription = billingManager.onlinePurchaseToken()
            if (subscription != null) {
                runCatching { client.linkSubscription(subscription) }
            }
            message
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Minha conta e nuvem", color = androidx.compose.ui.graphics.Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Voltar", color = androidx.compose.ui.graphics.Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyPrimary)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Sincronização e aprovação de orçamentos", fontWeight = FontWeight.Bold)
                        Text("Entre com Google ou e-mail para identificar sua conta. O uso local continua disponível sem login. Você pode acessar cópias na nuvem, recuperar dados em outro celular " +
                            "e acompanhar respostas de clientes a links de aprovação.")
                        Text("A sincronização online exige o plano Pro Online e conexão com o servidor da DKARSON.")
                        if (!client.configured) {
                            Text("Servidor online ainda não configurado nesta compilação. " +
                                "Os recursos locais e seus orçamentos continuam funcionando normalmente.")
                        }
                    }
                }
            }
            if (client.configured && !logged) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Acessar minha conta", fontWeight = FontWeight.Bold)
                            if (client.googleLoginAvailable) {
                                Button(
                                    onClick = { googleLogin() },
                                    enabled = !busy, modifier = Modifier.fillMaxWidth()
                                        .testTag("cloud_google_sign_in")
                                ) { Text("Continuar com Google") }
                            }
                            Text("Ou entre com código no e-mail, sem criar senha:")
                            OutlinedTextField(
                                value = email, onValueChange = { email = it },
                                label = { Text("E-mail") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            if (sent) OutlinedTextField(
                                value = code, onValueChange = { code = it },
                                label = { Text("Código de seis dígitos") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (sent) work {
                                        val message = client.confirmCode(email, code)
                                        logged = true
                                        billingManager.onlinePurchaseToken()?.let { token ->
                                            runCatching { client.linkSubscription(token) }
                                        }
                                        message
                                    } else work {
                                        val message = client.requestCode(email)
                                        sent = true
                                        message
                                    }
                                },
                                enabled = !busy && (if (sent) code.length == 6 else email.contains('@')),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (sent) "Confirmar código" else "Enviar código") }
                        }
                    }
                }
            }
            if (logged) {
                item {
                    Text("Conectado: ${client.email()}", fontWeight = FontWeight.SemiBold)
                    if (client.googleLoginAvailable) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { googleLogin() },
                            modifier = Modifier.fillMaxWidth().testTag("cloud_google_link")
                        ) { Text("Vincular minha conta Google") }
                    }
                    OutlinedButton(
                        onClick = { work {
                            val message = client.signOut()
                            logged = false; sent = false; code = ""
                            message
                        } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sair desta conta") }
                    TextButton(
                        enabled = !busy,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().testTag("cloud_delete_account")
                    ) { Text("Excluir minha conta e backups online") }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Verificar seu plano Online", fontWeight = FontWeight.Bold)
                            Text("Sua assinatura precisa estar vinculada ao mesmo e-mail para autorizar a nuvem.")
                            Button(
                                onClick = {
                                    work {
                                        val token = billingManager.onlinePurchaseToken()
                                        if (token.isNullOrBlank()) {
                                            billingManager.restorePurchases()
                                            error("Abra o plano Online, confirme a assinatura pela Google Play e tente novamente.")
                                        }
                                        client.linkSubscription(token)
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_link_subscription")
                            ) { Text("Vincular assinatura Google Play") }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Backup em nuvem", fontWeight = FontWeight.Bold)
                            Text("O envio não substitui uma cópia mais nova de outro aparelho. " +
                                "Se houver conflito, baixe e confira antes.")
                            Button(
                                onClick = { work {
                                    "Backup na nuvem atualizado (revisão ${client.uploadBackup(repository)})."
                                } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_upload_backup")
                            ) { Text("Enviar minha cópia para a nuvem") }
                            OutlinedButton(
                                onClick = { work {
                                    preview = client.downloadPreview()
                                    "Cópia encontrada. Confira os dados antes de restaurar."
                                } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_download_backup")
                            ) { Text("Consultar cópia da nuvem") }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Respostas dos clientes", fontWeight = FontWeight.Bold)
                            Text("Quando o cliente aprovar ou recusar pelo link, confira o status aqui. " +
                                "Propostas já pagas ou alteradas não serão modificadas.")
                            OutlinedButton(
                                onClick = { work {
                                    val (applied, skipped) = client.applyApprovedDecisions(repository)
                                    "$applied resposta(s) aplicada(s); $skipped ignorada(s) por segurança."
                                } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_pull_approvals")
                            ) { Text("Buscar aprovações e recusas") }
                        }
                    }
                }
            }
            if (lastResult.isNotBlank()) {
                item { Text(lastResult) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir sua conta online?") },
            text = { Text("Exclui sua conta, sessão e backups hospedados no servidor. Não apaga seus orçamentos neste aparelho nem cancela a assinatura na Google Play. Não é possível desfazer.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    confirmDelete = false
                    work {
                        val message = client.deleteOnlineAccount()
                        logged = false; sent = false; code = ""
                        message
                    }
                }) { Text("Confirmar exclusão online") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Restaurar histórico da nuvem?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Revisão: ${data.revision}")
                    Text("Orçamentos: ${data.quotes}")
                    Text("Clientes: ${data.clients}")
                    if (data.exportDate > 0) Text(
                        "Data: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                            .format(Date(data.exportDate))
                    )
                    Text("ATENÇÃO: esta ação substituirá os dados locais pelos dados da nuvem. " +
                        "Um backup preventivo local será criado antes. Não prossiga se houve " +
                        "alterações locais que ainda não foram enviadas.")
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        preview = null
                        work { client.restoreSnapshot(repository, data) }
                    }, modifier = Modifier.testTag("cloud_confirm_replace")
                ) { Text("Confirmar substituição") }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) { Text("Cancelar") }
            }
        )
    }
}
