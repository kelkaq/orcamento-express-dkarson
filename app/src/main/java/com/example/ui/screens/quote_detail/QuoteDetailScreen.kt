package com.example.ui.screens.quote_detail

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.ServiceOrderEntity
import com.example.data.entity.QuoteCostEntity
import com.example.cloud.CloudClient
import com.example.util.ServiceOrderPdfGenerator
import com.example.data.entity.QuoteWithItems
import com.example.data.repository.QuoteRepository
import com.example.domain.model.QuoteStatus
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SlateLightBg
import com.example.ui.theme.SlateMuted
import com.example.util.Formatters
import com.example.util.PdfGenerator
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDetailScreen(
    quoteId: Long,
    repository: QuoteRepository,
    onNavigateBack: () -> Unit,
    onEditQuote: (Long) -> Unit,
    canUsePro: Boolean = false,
    onUpgrade: () -> Unit = {},
    onCreateAgenda: (Long) -> Unit = {},
    cloudClient: CloudClient? = null,
    canUseOnline: Boolean = false,
    onOpenCloud: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var quoteWithItems by remember { mutableStateOf<QuoteWithItems?>(null) }
    var companyProfile by remember { mutableStateOf(CompanyProfileEntity()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCostDialog by remember { mutableStateOf(false) }
    var costMaterials by remember { mutableStateOf("") }
    var costLabor by remember { mutableStateOf("") }
    var costTravel by remember { mutableStateOf("") }
    var costOther by remember { mutableStateOf("") }
    val costFlow = remember(quoteId) { repository.observeQuoteCosts(quoteId) }
    val costRecord by costFlow.collectAsStateWithLifecycle(initialValue = null)
    var showServiceOrderDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var orderResponsible by remember { mutableStateOf("") }
    var orderDescription by remember { mutableStateOf("") }
    var orderMaterials by remember { mutableStateOf("") }
    var orderNotes by remember { mutableStateOf("") }
    var orderStatus by remember { mutableStateOf("SCHEDULED") }
    var orderScheduledAt by remember { mutableStateOf<Long?>(null) }
    var savingOrder by remember { mutableStateOf(false) }
    val serviceOrderFlow = remember(quoteId) { repository.observeServiceOrder(quoteId) }
    val serviceOrder by serviceOrderFlow.collectAsStateWithLifecycle(initialValue = null)

    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentMethodText by remember { mutableStateOf("PIX") }
    var paymentNotesText by remember { mutableStateOf("") }
    var paymentDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var savingPayment by remember { mutableStateOf(false) }
    val paymentFlow = remember(quoteId) { repository.observePayments(quoteId) }
    val payments by paymentFlow.collectAsStateWithLifecycle(initialValue = emptyList())


    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(quoteId) {
        val q = repository.getQuoteWithItems(quoteId)
        val c = repository.getCompanyProfile()
        quoteWithItems = q
        companyProfile = c
        isLoading = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = quoteWithItems?.quote?.quoteNumber ?: "Detalhes do Orçamento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { onEditQuote(quoteId) }, enabled = payments.isEmpty()) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = payments.isEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyPrimary)
            }
            return@Scaffold
        }

        val data = quoteWithItems
        if (data == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Orçamento não encontrado.", color = SlateMuted)
            }
            return@Scaffold
        }

        val quote = data.quote
        val items = data.items
        val statusEnum = QuoteStatus.fromCode(quote.status)
        val totalCents = BigDecimal.valueOf(quote.total)
            .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        val receivedCents = payments.sumOf { it.amountCents }
        val remainingCents = (totalCents - receivedCents).coerceAtLeast(0)


        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(SlateLightBg)
        ) {
            // Status and Action Bar
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(quote.quoteNumber, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                Text("Emissão: ${Formatters.formatDate(quote.issueDate)}", fontSize = 12.sp, color = SlateMuted)
                                Text("Válido até: ${Formatters.formatDate(quote.validUntil)}", fontSize = 12.sp, color = SlateMuted)
                            }
                            StatusBadge(status = statusEnum)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Status quick toggles
                        Text("Atualizar Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(QuoteStatus.PENDING, QuoteStatus.APPROVED, QuoteStatus.COMPLETED).forEach { s ->
                                FilterChip(
                                    selected = quote.status == s.code,
                                    onClick = {
                                        scope.launch {
                                            try {
                                                repository.updateQuoteStatus(quote.id, s.code)
                                                quoteWithItems = repository.getQuoteWithItems(quote.id)
                                                snackbarHostState.showSnackbar("Status atualizado para ${s.label}!")
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Não é possível alterar o status.")
                                            }
                                        }
                                    },
                                    label = { Text(s.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NavyPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Share Actions Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val file = PdfGenerator.generateQuotePdf(context, data, companyProfile)
                                    val uri = PdfGenerator.getFileUri(context, file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Orçamento ${quote.quoteNumber}")
                                        putExtra(Intent.EXTRA_TEXT, "Segue o orçamento ${quote.quoteNumber} para sua avaliação.")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Compartilhar Orçamento PDF"))
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erro: ${e.localizedMessage}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_share_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gerar PDF")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val file = PdfGenerator.generateQuotePdf(context, data, companyProfile)
                                    val uri = PdfGenerator.getFileUri(context, file)
                                    val message = "Olá, ${quote.clientName.ifBlank { "Cliente" }}! Segue o orçamento ${quote.quoteNumber} no valor de ${Formatters.formatCurrency(quote.total)}, válido até ${Formatters.formatDate(quote.validUntil)}."

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, message)
                                        setPackage("com.whatsapp")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }

                                    try {
                                        context.startActivity(shareIntent)
                                    } catch (_: Exception) {
                                        val fallback = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_TEXT, message)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(fallback, "Compartilhar no WhatsApp"))
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erro: ${e.localizedMessage}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_send_whatsapp")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }
            }

            // A agenda pertence ao fluxo comercial e pode ser usada gratuitamente.
            item {
                OutlinedButton(
                    onClick = { onCreateAgenda(quoteId) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                        .testTag("quote_schedule_followup")
                ) { Text("Agendar serviço ou retorno ao cliente") }
            }

            if (quote.status == "PENDING") {
                item {
                    OutlinedButton(
                        onClick = {
                            val cloud = cloudClient
                            if (!canUseOnline || cloud == null || !cloud.configured || !cloud.isLoggedIn()) {
                                onOpenCloud()
                            } else {
                                scope.launch {
                                    try {
                                        val url = cloud.createApprovalLink(data)
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT,
                                                "Olá! Segue o link para analisar e responder ao orçamento " +
                                                "${quote.quoteNumber}: " + url)
                                        }
                                        context.startActivity(Intent.createChooser(share,
                                            "Compartilhar link de aprovação"))
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(
                                            e.localizedMessage ?: "Não foi possível criar o link."
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                            .testTag("share_approval_link")
                    ) {
                        Text(if (canUseOnline) "Enviar link de aprovação ao cliente"
                            else "Aprovação online por link • Pro Online")
                    }
                }
            }

            // Custos internos: nunca saem no PDF do cliente.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Custos e lucro do serviço", fontWeight = FontWeight.Bold, color = NavyPrimary)
                        costRecord?.let { costs ->
                            Text("Materiais: ${Formatters.formatCurrency(costs.materialsCents / 100.0)}")
                            Text("Mão de obra: ${Formatters.formatCurrency(costs.laborCents / 100.0)}")
                            Text("Deslocamento: ${Formatters.formatCurrency(costs.travelCents / 100.0)}")
                            Text("Outros custos: ${Formatters.formatCurrency(costs.otherCents / 100.0)}")
                            Text("Custo total: ${Formatters.formatCurrency(costs.totalCents / 100.0)}")
                            Text("Lucro estimado: ${Formatters.formatCurrency(quote.total - costs.totalCents / 100.0)}",
                                fontWeight = FontWeight.Bold)
                            Text("Antes de impostos e despesas não informadas. Estes valores não aparecem no PDF.",
                                fontSize = 11.sp, color = SlateMuted)
                        } ?: Text("Informe os custos para acompanhar o lucro deste serviço.", color = SlateMuted)
                        OutlinedButton(onClick = {
                            if (!canUsePro) onUpgrade() else {
                                val costs = costRecord
                                costMaterials = costs?.let { BigDecimal.valueOf(it.materialsCents, 2).toPlainString() }.orEmpty()
                                costLabor = costs?.let { BigDecimal.valueOf(it.laborCents, 2).toPlainString() }.orEmpty()
                                costTravel = costs?.let { BigDecimal.valueOf(it.travelCents, 2).toPlainString() }.orEmpty()
                                costOther = costs?.let { BigDecimal.valueOf(it.otherCents, 2).toPlainString() }.orEmpty()
                                showCostDialog = true
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("edit_real_costs")) {
                            Text(if (canUsePro) "Registrar / corrigir custos" else "Custos e lucro • Pro")
                        }
                    }
                }
            }

            // Orçamento convertido em serviço executável (sem receita duplicada).
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ordem de Serviço e Modelos", color = NavyPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (serviceOrder != null) {
                            Text("OS-${serviceOrder!!.id.toString().padStart(5, '0')} • " + when (serviceOrder!!.status) {
                                "COMPLETED" -> "Concluída"
                                "IN_PROGRESS" -> "Em execução"
                                else -> "Agendada"
                            }, fontWeight = FontWeight.SemiBold)
                            if (serviceOrder!!.scheduledAt != null) {
                                Text("Prevista para ${Formatters.formatDate(serviceOrder!!.scheduledAt!!)}", color = SlateMuted)
                            }
                        }
                        if (quote.status in listOf("APPROVED", "COMPLETED")) {
                            Button(
                                onClick = {
                                    if (!canUsePro) {
                                        onUpgrade()
                                    } else {
                                    val order = serviceOrder
                                    orderResponsible = order?.responsible.orEmpty()
                                    orderDescription = order?.description ?: items.joinToString("; ") { it.description }
                                    orderMaterials = order?.materials.orEmpty()
                                    orderNotes = order?.notes.orEmpty()
                                    orderStatus = order?.status ?: "SCHEDULED"
                                    orderScheduledAt = order?.scheduledAt
                                    showServiceOrderDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("service_order_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                            ) { Text(if (!canUsePro) "Ordem de Serviço • Pro" else
                                if (serviceOrder == null) "Converter em Ordem de Serviço" else "Editar Ordem de Serviço") }
                        }
                        serviceOrder?.let { order ->
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val file = ServiceOrderPdfGenerator.generate(context, data, order, companyProfile)
                                            val uri = PdfGenerator.getFileUri(context, file)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_TEXT, "Segue a ordem de serviço OS-${order.id.toString().padStart(5, '0')} referente a ${quote.quoteNumber}.")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Compartilhar Ordem de Serviço"))
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(e.localizedMessage ?: "Erro ao gerar OS.")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("share_service_order")
                            ) { Text("Gerar e compartilhar OS em PDF") }
                        }
                        OutlinedButton(
                            onClick = {
                                if (!canUsePro) onUpgrade() else {
                                    templateName = ""
                                    showTemplateDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_as_template_button")
                        ) { Text(if (canUsePro) "Salvar este orçamento como modelo"
                            else "Salvar como modelo • Pro") }
                    }
                }
            }

            // Recebimentos reais e recibos. Aprovação da proposta não equivale a pagamento.
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recebimentos e Recibos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recebido", color = SlateMuted)
                            Text(Formatters.formatCurrency(receivedCents / 100.0), color = EmeraldAccent, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Saldo pendente", color = SlateMuted)
                            Text(Formatters.formatCurrency(remainingCents / 100.0), fontWeight = FontWeight.Bold)
                        }
                        if (remainingCents == 0L && payments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Pagamento integral registrado", color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (quote.status in listOf("APPROVED", "COMPLETED") && remainingCents > 0L) {
                            Button(
                                onClick = {
                                    if (!canUsePro) {
                                        onUpgrade()
                                    } else {
                                    paymentAmountText = BigDecimal.valueOf(remainingCents, 2).toPlainString().replace('.', ',')
                                    paymentMethodText = quote.paymentMethod.ifBlank { "PIX" }
                                    paymentNotesText = ""
                                    paymentDateMillis = System.currentTimeMillis()
                                    showPaymentDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("register_payment_button")
                            ) {
                                Text(if (canUsePro) "Registrar pagamento recebido"
                                    else "Registrar pagamento • Pro")
                            }
                        } else if (quote.status !in listOf("APPROVED", "COMPLETED")) {
                            Text("Aprove o orçamento para registrar um recebimento.", color = SlateMuted, fontSize = 12.sp)
                        }

                        payments.forEach { payment ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)
                            Text(
                                "Recibo REC-${quote.id}-${payment.id} • ${Formatters.formatDate(payment.paidAt)}",
                                fontSize = 12.sp, color = SlateMuted
                            )
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Formatters.formatCurrency(payment.amountCents / 100.0), fontWeight = FontWeight.Bold)
                                    Text(payment.method, color = SlateMuted, fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        try {
                                            val file = PdfGenerator.generateReceiptPdf(context, quote, payment, companyProfile)
                                            val uri = PdfGenerator.getFileUri(context, file)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "Recibo REC-${quote.id}-${payment.id}")
                                                putExtra(Intent.EXTRA_TEXT, "Segue o recibo do pagamento de ${Formatters.formatCurrency(payment.amountCents / 100.0)} referente ao orçamento ${quote.quoteNumber}.")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Enviar recibo PDF"))
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Não foi possível gerar o recibo: ${e.localizedMessage}")
                                        }
                                    }
                                }, modifier = Modifier.testTag("share_receipt_${payment.id}")) {
                                    Text("Recibo PDF")
                                }
                            }
                        }
                        if (payments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Por segurança, orçamentos com recebimentos não podem ser editados ou excluídos.", fontSize = 11.sp, color = SlateMuted)
                        }
                    }
                }
            }

            // Client Info Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cliente", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(quote.clientName.ifBlank { "Não informado" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                        if (quote.clientDocument.isNotBlank()) {
                            Text("CPF/CNPJ: ${Formatters.formatDocument(quote.clientDocument)}", fontSize = 13.sp, color = SlateMuted)
                        }
                        if (quote.clientPhone.isNotBlank()) {
                            Text("Telefone: ${Formatters.formatPhone(quote.clientPhone)}", fontSize = 13.sp, color = SlateMuted)
                        }
                        if (quote.clientEmail.isNotBlank()) {
                            Text("E-mail: ${quote.clientEmail}", fontSize = 13.sp, color = SlateMuted)
                        }
                        if (quote.clientAddress.isNotBlank()) {
                            Text("Endereço: ${quote.clientAddress}", fontSize = 13.sp, color = SlateMuted)
                        }
                    }
                }
            }

            // Items Table Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Itens e Serviços (${items.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        items.forEachIndexed { idx, it ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(it.description, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    val qtyFormatted = if (it.quantity % 1.0 == 0.0) it.quantity.toInt().toString() else it.quantity.toString()
                                    Text("$qtyFormatted ${it.unit} x ${Formatters.formatCurrency(it.unitPrice)}", fontSize = 12.sp, color = SlateMuted)
                                }
                                Text(
                                    Formatters.formatCurrency(it.totalPrice),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (idx < items.size - 1) {
                                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = CardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Totals Summary
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = SlateMuted, fontSize = 13.sp)
                            Text(Formatters.formatCurrency(quote.subtotal), fontSize = 13.sp)
                        }

                        if (quote.discountCalculated > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Desconto", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                Text("- " + Formatters.formatCurrency(quote.discountCalculated), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (quote.shippingFee > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Frete / Deslocamento", color = SlateMuted, fontSize = 13.sp)
                                Text("+ " + Formatters.formatCurrency(quote.shippingFee), fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyPrimary, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL GERAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(Formatters.formatCurrency(quote.total), color = EmeraldAccent, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        }
                    }
                }
            }

            // Payment and Notes Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Condições de Pagamento", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (quote.paymentMethod.isNotBlank()) {
                            Text("Forma: ${quote.paymentMethod}", fontSize = 13.sp)
                        }
                        if (quote.paymentTerms.isNotBlank()) {
                            Text("Condições: ${quote.paymentTerms}", fontSize = 13.sp)
                        }
                        if (companyProfile.pixKey.isNotBlank()) {
                            Text("Chave PIX (${companyProfile.pixKeyType}): ${companyProfile.pixKey}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = EmeraldAccent)
                        }

                        if (quote.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Observações / Garantia:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SlateMuted)
                            Text(quote.notes, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showCostDialog) {
        AlertDialog(
            onDismissRequest = { showCostDialog = false },
            title = { Text("Custos deste orçamento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("Materiais (R$)", costMaterials, { v: String -> costMaterials = v }),
                        Triple("Mão de obra (R$)", costLabor, { v: String -> costLabor = v }),
                        Triple("Deslocamento (R$)", costTravel, { v: String -> costTravel = v }),
                        Triple("Outros (R$)", costOther, { v: String -> costOther = v })
                    ).forEach { (label, value, setter) ->
                        OutlinedTextField(
                            value = value, onValueChange = setter,
                            label = { Text(label) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            fun read(text: String): Long {
                                val normalized = text.trim().let {
                                    if (',' in it) it.replace(".", "").replace(',', '.') else it
                                }
                                return com.example.pricing.PricingEngine.cents(
                                    normalized.toDoubleOrNull() ?: 0.0
                                )
                            }
                            repository.saveQuoteCosts(
                                quoteId, read(costMaterials), read(costLabor),
                                read(costTravel), read(costOther)
                            )
                            showCostDialog = false
                            snackbarHostState.showSnackbar("Custos atualizados.")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.localizedMessage ?: "Custos inválidos.")
                        }
                    }
                }) { Text("Salvar custos") }
            },
            dismissButton = {
                TextButton(onClick = { showCostDialog = false }) { Text("Cancelar") }
            }
        )
    }

    val orderQuote = quoteWithItems?.quote
    if (showServiceOrderDialog && orderQuote != null) {
        AlertDialog(
            onDismissRequest = { if (!savingOrder) showServiceOrderDialog = false },
            title = { Text("Ordem de Serviço") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = orderResponsible,
                        onValueChange = { orderResponsible = it },
                        label = { Text("Responsável") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = orderDescription, onValueChange = { orderDescription = it },
                        label = { Text("Descrição da execução") }, maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = orderMaterials, onValueChange = { orderMaterials = it },
                        label = { Text("Materiais utilizados") }, maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = orderNotes, onValueChange = { orderNotes = it },
                        label = { Text("Observações") }, maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply {
                                timeInMillis = orderScheduledAt ?: System.currentTimeMillis()
                            }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    orderScheduledAt = Calendar.getInstance().apply {
                                        set(year, month, day, 12, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                },
                                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text("Previsão: " + (orderScheduledAt?.let { Formatters.formatDate(it) } ?: "Selecionar data")) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SCHEDULED" to "Agendada", "IN_PROGRESS" to "Execução", "COMPLETED" to "Concluída")
                            .forEach { (code, label) ->
                                FilterChip(
                                    selected = orderStatus == code, onClick = { orderStatus = code },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !savingOrder,
                    onClick = {
                        savingOrder = true
                        scope.launch {
                            try {
                                repository.saveServiceOrder(
                                    orderQuote.id, orderResponsible, orderDescription,
                                    orderMaterials, orderNotes, orderScheduledAt, orderStatus
                                )
                                quoteWithItems = repository.getQuoteWithItems(orderQuote.id)
                                showServiceOrderDialog = false
                                snackbarHostState.showSnackbar("Ordem de serviço salva.")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.localizedMessage ?: "Erro ao salvar OS.")
                            } finally { savingOrder = false }
                        }
                    }
                ) { Text(if (savingOrder) "Salvando..." else "Salvar OS") }
            },
            dismissButton = {
                TextButton(onClick = { showServiceOrderDialog = false }, enabled = !savingOrder) { Text("Cancelar") }
            }
        )
    }

    if (showTemplateDialog && orderQuote != null) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("Salvar modelo reutilizável") },
            text = {
                OutlinedTextField(
                    value = templateName, onValueChange = { templateName = it },
                    label = { Text("Nome do modelo") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            repository.saveTemplateFromQuote(orderQuote.id, templateName)
                            showTemplateDialog = false
                            snackbarHostState.showSnackbar("Modelo salvo. Ele aparecerá no próximo orçamento.")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.localizedMessage ?: "Erro ao salvar modelo.")
                        }
                    }
                }) { Text("Salvar modelo") }
            },
            dismissButton = { TextButton(onClick = { showTemplateDialog = false }) { Text("Cancelar") } }
        )
    }

    val paymentDialogQuote = quoteWithItems?.quote
    val dialogRemainingCents = paymentDialogQuote?.let { q ->
        (BigDecimal.valueOf(q.total).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact() -
            payments.sumOf { it.amountCents }).coerceAtLeast(0)
    } ?: 0L
    if (showPaymentDialog && paymentDialogQuote != null) {
        AlertDialog(
            onDismissRequest = { if (!savingPayment) showPaymentDialog = false },
            title = { Text("Registrar recebimento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Registre apenas um pagamento que você já recebeu. O aplicativo não verifica pagamentos bancários.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = { Text("Valor recebido (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paymentMethodText,
                        onValueChange = { paymentMethodText = it },
                        label = { Text("Forma de pagamento") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paymentNotesText,
                        onValueChange = { paymentNotesText = it },
                        label = { Text("Observação (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = paymentDateMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                paymentDateMillis = Calendar.getInstance().apply {
                                    set(year, month, day, 12, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("Data do recebimento: ${Formatters.formatDate(paymentDateMillis)}")
                    }
                    Text("Após salvar, o recibo ficará disponível neste orçamento.", color = SlateMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = !savingPayment,
                    onClick = {
                        val normalized = paymentAmountText.trim().let { raw ->
                            if (',' in raw) raw.replace(".", "").replace(',', '.') else raw
                        }
                        val cents = runCatching {
                            normalized.toBigDecimal().movePointRight(2)
                                .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
                        }.getOrNull()
                        if (cents == null || cents <= 0L || cents > dialogRemainingCents || paymentMethodText.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("Informe um valor válido até o saldo pendente e a forma de pagamento.") }
                        } else {
                            savingPayment = true
                            scope.launch {
                                try {
                                    repository.registerPayment(paymentDialogQuote.id, cents, paymentDateMillis, paymentMethodText, paymentNotesText)
                                    showPaymentDialog = false
                                    snackbarHostState.showSnackbar("Recebimento salvo. Toque em Recibo PDF para enviar ao cliente.")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Pagamento não registrado: ${e.localizedMessage}")
                                } finally {
                                    savingPayment = false
                                }
                            }
                        }
                    }
                ) { Text(if (savingPayment) "Salvando..." else "Confirmar recebimento") }
            },
            dismissButton = {
                TextButton(enabled = !savingPayment, onClick = { showPaymentDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir Orçamento?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente remover este orçamento? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        quoteWithItems?.quote?.let { q ->
                            scope.launch {
                                try {
                                    repository.deleteQuote(q)
                                    showDeleteConfirm = false
                                    onNavigateBack()
                                } catch (e: Exception) {
                                    showDeleteConfirm = false
                                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Não é possível excluir o orçamento.")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
