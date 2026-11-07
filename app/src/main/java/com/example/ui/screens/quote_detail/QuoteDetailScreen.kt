package com.example.ui.screens.quote_detail

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDetailScreen(
    quoteId: Long,
    repository: QuoteRepository,
    onNavigateBack: () -> Unit,
    onEditQuote: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var quoteWithItems by remember { mutableStateOf<QuoteWithItems?>(null) }
    var companyProfile by remember { mutableStateOf(CompanyProfileEntity()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentMethodText by remember { mutableStateOf("PIX") }
    var paymentNotesText by remember { mutableStateOf("") }
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
                                            repository.updateQuoteStatus(quote.id, s.code)
                                            quoteWithItems = repository.getQuoteWithItems(quote.id)
                                            snackbarHostState.showSnackbar("Status atualizado para ${s.label}!")
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
                                    paymentAmountText = BigDecimal.valueOf(remainingCents, 2).toPlainString().replace('.', ',')
                                    paymentMethodText = quote.paymentMethod.ifBlank { "PIX" }
                                    paymentNotesText = ""
                                    showPaymentDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("register_payment_button")
                            ) {
                                Text("Registrar pagamento recebido")
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
                    Text("Data do recebimento: hoje. Depois de salvar, o recibo ficará disponível neste orçamento.", color = SlateMuted, fontSize = 12.sp)
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
                                    repository.registerPayment(paymentDialogQuote.id, cents, System.currentTimeMillis(), paymentMethodText, paymentNotesText)
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
                                repository.deleteQuote(q)
                                showDeleteConfirm = false
                                onNavigateBack()
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
