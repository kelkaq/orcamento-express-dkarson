package com.example.ui.screens.company

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.entity.CompanyProfileEntity
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SlateLightBg
import com.example.ui.theme.SlateMuted
import com.example.ui.viewmodel.CompanyViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyScreen(
    viewModel: CompanyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val profile by viewModel.companyProfile.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Form fields initialized with profile
    var tradeName by remember(profile) { mutableStateOf(profile.tradeName) }
    var legalName by remember(profile) { mutableStateOf(profile.legalName) }
    var document by remember(profile) { mutableStateOf(profile.document) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var email by remember(profile) { mutableStateOf(profile.email) }
    var address by remember(profile) { mutableStateOf(profile.address) }
    var pixKey by remember(profile) { mutableStateOf(profile.pixKey) }
    var pixKeyType by remember(profile) { mutableStateOf(profile.pixKeyType) }
    var defaultValidityDays by remember(profile) { mutableStateOf(profile.defaultValidityDays.toString()) }
    var defaultPaymentTerms by remember(profile) { mutableStateOf(profile.defaultPaymentTerms) }
    var defaultNotes by remember(profile) { mutableStateOf(profile.defaultNotes) }

    // Photo picker for company logo (zero permissions required!)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveLogoFromUri(context, uri)
        }
    }

    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingValidationResult by remember { mutableStateOf<com.example.util.BackupValidationResult?>(null) }

    // Document picker for restoring JSON backup
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val validation = viewModel.validateBackup(context, uri)
            if (validation.isValid) {
                pendingRestoreUri = uri
                pendingValidationResult = validation
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(validation.errorMessage ?: "Arquivo de backup inválido.")
                }
            }
        }
    }

    LaunchedEffect(statusMessage, errorMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Minha Empresa & Ajustes", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyPrimary)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(SlateLightBg)
        ) {
            // Company Logo & Branding Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Logotipo da Empresa no PDF",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val logoFile = profile.logoPath?.let { File(it) }
                        if (logoFile != null && logoFile.exists()) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = logoFile),
                                    contentDescription = "Logotipo da Empresa",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Trocar Logo", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { viewModel.removeLogo() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Remover", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Business, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_select_logo")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Carregar Logotipo", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Business Information Form
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Dados Comerciais", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = tradeName,
                            onValueChange = { tradeName = it },
                            label = { Text("Nome Fantasia ou Nome do Profissional") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("company_name_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = legalName,
                            onValueChange = { legalName = it },
                            label = { Text("Razão Social (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = document,
                                onValueChange = { document = it },
                                label = { Text("CNPJ ou CPF") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("company_doc_input")
                            )

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Telefone / WhatsApp") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f).testTag("company_phone_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-mail Comercial") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Endereço Completo ou Cidade/UF") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // PIX Details
                        Text("Dados Bancários & PIX", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pixKey,
                                onValueChange = { pixKey = it },
                                label = { Text("Chave PIX") },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f).testTag("company_pix_input")
                            )

                            OutlinedTextField(
                                value = pixKeyType,
                                onValueChange = { pixKeyType = it },
                                label = { Text("Tipo (CPF, Tel, etc)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Default Terms
                        Text("Padrões para Novos Orçamentos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = defaultValidityDays,
                            onValueChange = { defaultValidityDays = it },
                            label = { Text("Validade padrão em dias") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = defaultPaymentTerms,
                            onValueChange = { defaultPaymentTerms = it },
                            label = { Text("Condições padrão de pagamento") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = defaultNotes,
                            onValueChange = { defaultNotes = it },
                            label = { Text("Observações e termos padrão de garantia") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val updated = profile.copy(
                                    tradeName = tradeName.trim(),
                                    legalName = legalName.trim(),
                                    document = document.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    address = address.trim(),
                                    pixKey = pixKey.trim(),
                                    pixKeyType = pixKeyType.trim().ifBlank { "Chave" },
                                    defaultValidityDays = defaultValidityDays.toIntOrNull() ?: 15,
                                    defaultPaymentTerms = defaultPaymentTerms.trim(),
                                    defaultNotes = defaultNotes.trim()
                                )
                                viewModel.updateProfile(updated)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_save_company")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar Dados da Empresa", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Backup & Restore Section
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = NavyPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup e Restauração", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Seus orçamentos, clientes e catálogo são armazenados com segurança 100% no seu aparelho. Exporte uma cópia completa de backup ou restaure seus dados a qualquer momento.",
                            fontSize = 12.sp,
                            color = SlateMuted
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.exportBackup(context) { file, uri ->
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Backup Orçamento Express - ${file.name}")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Salvar / Compartilhar Backup"))
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_export_backup")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exportar", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { restoreFileLauncher.launch("application/json") },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_restore_backup")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restaurar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Identidade real do desenvolvedor, sem compra simulada.
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = NavyPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Orçamento Express", fontWeight = FontWeight.Bold, color = NavyPrimary)
                            Text("Desenvolvido por DKARSON", fontSize = 12.sp, color = SlateMuted)
                        }
                    }
                }
            }

            // Security, Privacy & Google Play compliance
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Privacidade e armazenamento", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "O aplicativo armazena os orçamentos no aparelho e funciona offline. O compartilhamento de PDFs e os backups escolhidos pelo usuário podem enviar informações a outros serviços.",
                            fontSize = 12.sp,
                            color = SlateMuted
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.testTag("btn_view_privacy_policy")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver Termos e Política de Privacidade DKARSON", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Política de Privacidade", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    item {
                        Text(
                            text = "POLÍTICA DE PRIVACIDADE — ORÇAMENTO EXPRESS\n" +
                                    "Desenvolvido por: DKARSON\n\n" +
                                    "1. ARMAZENAMENTO E TRATAMENTO DE DADOS\n" +
                                    "O Orçamento Express mantém seus dados comerciais no banco local do dispositivo. Dependendo das configurações do aparelho, o sistema Android poderá realizar cópias de segurança desses dados por serviços do fabricante ou do sistema.\n\n" +
                                    "2. TRATAMENTO E TRANSMISSÃO DE DADOS\n" +
                                    "As funções locais do aplicativo não exigem uma conta ou servidor da DKARSON. A geração de PDFs ocorre no aparelho. Serviços de backup do sistema e aplicativos escolhidos para compartilhar documentos possuem políticas próprias.\n\n" +
                                    "3. COMPARTILHAMENTO DE DOCUMENTOS\n" +
                                    "O envio de orçamentos (por e-mail, WhatsApp ou outros canais) é executado única e exclusivamente mediante ação voluntária do usuário através do menu padrão de compartilhamento do Android.\n\n" +
                                    "4. BACKUP, RESTAURAÇÃO E EXCLUSÃO\n" +
                                    "O usuário possui controle pleno para exportar backups integrais de seus dados, mesclar ou substituir bases com geração obrigatória de backup preventivo de segurança, bem como excluir qualquer cliente, produto ou orçamento a qualquer momento.\n\n" +
                                    "5. PUBLICIDADE E PAGAMENTOS\n" +
                                    "Esta compilação não integra anúncios de terceiros nem habilita compras dentro do aplicativo. Qualquer integração futura exigirá atualização das informações de privacidade antes da publicação.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = SlateMuted
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    // Restore Options Dialog
    val currentUri = pendingRestoreUri
    val currentValidation = pendingValidationResult
    if (currentUri != null && currentValidation != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRestoreUri = null
                pendingValidationResult = null
            },
            title = { Text("Restaurar Arquivo de Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Arquivo validado com sucesso:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (currentValidation.exportDate > 0) {
                        Text("• Exportado em: ${com.example.util.Formatters.formatDateTime(currentValidation.exportDate)}", fontSize = 12.sp, color = SlateMuted)
                    }
                    if (currentValidation.companyName.isNotBlank()) {
                        Text("• Empresa: ${currentValidation.companyName}", fontSize = 12.sp, color = SlateMuted)
                    }
                    Text("• Conteúdo: ${currentValidation.quotesCount} orçamentos, ${currentValidation.clientsCount} clientes, ${currentValidation.productsCount} itens.", fontSize = 12.sp, color = SlateMuted)

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Como deseja restaurar este arquivo?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Substituir: apaga os dados atuais e restaura exatamente o backup (um backup de segurança preventivo será salvo automaticamente no aparelho).\n\nMesclar: mantém os registros atuais e adiciona os novos dados.",
                        fontSize = 11.5.sp,
                        color = SlateMuted
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.restoreBackup(context, currentUri, replaceExisting = true) {
                                pendingRestoreUri = null
                                pendingValidationResult = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Substituir Tudo (com Backup Preventivo)")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.restoreBackup(context, currentUri, replaceExisting = false) {
                                pendingRestoreUri = null
                                pendingValidationResult = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mesclar com Dados Atuais")
                    }

                    TextButton(
                        onClick = {
                            pendingRestoreUri = null
                            pendingValidationResult = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar")
                    }
                }
            },
            dismissButton = {}
        )
    }
}
