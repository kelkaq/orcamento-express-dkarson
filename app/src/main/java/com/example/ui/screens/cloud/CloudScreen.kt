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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    var recoveryInput by remember { mutableStateOf("") }
    var newRecoveryKey by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
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
                        Text("Acesse sem pagar por e-mails: crie uma conta com chave de recuperação " +
                            "ou utilize Google quando disponível. Você pode recuperar dados em outro celular " +
                            "e acompanhar a aprovação dos seus orçamentos.")
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
                            Text("Sem e-mail obrigatório: guarde a chave de recuperação em um local seguro. "+
                                "Se perder a chave e não estiver conectado, não será possível recuperar a conta.")
                            Button(
                                onClick = { work {
                                    val account = client.createPrivateAccount()
                                    email = account.label
                                    logged = true
                                    newRecoveryKey = account.recoveryKey
                                    billingManager.onlinePurchaseToken()?.let { token ->
                                        runCatching { client.linkSubscription(token) }
                                    }
                                    "Sua conta foi criada. Copie a chave de recuperação antes de continuar."
                                } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_create_private_account")
                            ) { Text("Criar minha conta gratuita sem e-mail") }
                            OutlinedTextField(
                                value = recoveryInput,
                                onValueChange = { recoveryInput = it.trim().take(60) },
                                label = { Text("Chave de recuperação de outra instalação") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_recovery_key_input")
                            )
                            OutlinedButton(
                                onClick = { work {
                                    val account = client.recoverPrivateAccount(recoveryInput)
                                    email = account.label
                                    logged = true
                                    recoveryInput = ""
                                    billingManager.onlinePurchaseToken()?.let { token ->
                                        runCatching { client.linkSubscription(token) }
                                    }
                                    "Acesso restaurado com segurança."
                                } },
                                enabled = !busy && recoveryInput.length == 43,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_recover_private_account")
                            ) { Text("Entrar usando minha chave salva") }
                            Text("Os dados locais pertencem a um único espaço de trabalho neste aparelho. " +
                                "Antes de trocar de conta, exporte um backup e use uma instalação limpa.")

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
                            val result = runCatching { client.signOut() }
                            logged = false; sent = false; code = ""
                            result.getOrElse {
                                "Conta desconectada deste aparelho. O servidor não confirmou o encerramento; " +
                                    "a sessão remota expira em até 30 dias."
                            }
                        } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sair desta conta") }
                    OutlinedButton(
                        onClick = { work {
                            val rotated = client.rotatePrivateKey()
                            newRecoveryKey = rotated.recoveryKey
                            "Chave alterada. Copie a nova chave; a anterior foi invalidada."
                        } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("cloud_rotate_recovery_key")
                    ) { Text("Trocar minha chave de recuperação") }
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
                            Text("A assinatura Google Play é associada a esta conta após verificação segura do pagamento.")
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
    newRecoveryKey?.let { secret ->
        AlertDialog(
            onDismissRequest = { /* A confirmação explícita evita sumir sem salvar a chave. */ },
            title = { Text("Salve sua chave de recuperação") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use esta chave para entrar novamente ou recuperar seus backups em outro celular. Ela não será exibida outra vez.")
                    Text(secret, modifier = Modifier.testTag("cloud_recovery_key_display"))
                    Text("Guarde em um gerenciador de senhas ou lugar seguro. Quem possuir a chave pode acessar sua conta.")
                }
            },
            confirmButton = {
                Button(onClick = { newRecoveryKey = null }) { Text("Já salvei minha chave") }
            },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(secret)) }) {
                    Text("Copiar chave")
                }
            }
        )
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
