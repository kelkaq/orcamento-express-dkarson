package com.example.ui.screens.quotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteWithItems
import com.example.domain.model.QuoteStatus
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SlateMuted
import com.example.ui.viewmodel.CompanyViewModel
import com.example.ui.viewmodel.QuotesViewModel
import com.example.util.Formatters
import com.example.util.PdfGenerator
import com.example.ui.components.CalculatorDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(
    viewModel: QuotesViewModel,
    companyViewModel: CompanyViewModel,
    onNewQuote: () -> Unit,
    onEditQuote: (Long) -> Unit,
    onViewDetail: (Long) -> Unit,
    onRestoreData: () -> Unit,
    isPro: Boolean = false,
    onUpgrade: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val quotes by viewModel.quotesList.collectAsStateWithLifecycle()
    val savedHistory by viewModel.savedQuoteHistory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val paidQuoteIds by viewModel.paidQuoteIds.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.period.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationError()
        }
    }
    val companyProfile by companyViewModel.companyProfile.collectAsStateWithLifecycle()

    var quoteToDelete by remember { mutableStateOf<QuoteEntity?>(null) }
    var showQuickCalculator by remember { mutableStateOf(false) }
    var financeExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Orçamento Express",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (companyProfile.tradeName.isNotBlank()) companyProfile.tradeName else "Propostas Comerciais Profissionais",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showQuickCalculator = true },
                        modifier = Modifier.testTag("home_calculator_top_button")
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = Color.White,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Calculadora", color = Color.White, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewQuote,
                containerColor = EmeraldAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_new_quote")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo Orçamento")
            }
        }
    ) { innerPadding ->
        // Lista única: mesmo com o painel financeiro expandido, o usuário sempre pode
        // rolar até os orçamentos, sem uma segunda lista limitada a altura zero.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .testTag("home_main_scroll_history"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // O histórico é a ação principal da Home. Indicadores são compactos por padrão.
            item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Histórico de orçamentos", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        color = NavyPrimary, modifier = Modifier.testTag("home_history_title"))
                    Text(
                        "${savedHistory.size} orçamento(s) salvo(s) neste aplicativo",
                        fontSize = 11.sp, color = SlateMuted,
                        modifier = Modifier.testTag("home_saved_quote_count")
                    )
                }
                OutlinedButton(
                    onClick = {
                        viewModel.onSearchQueryChanged("")
                        viewModel.onStatusFilterChanged("ALL")
                    },
                    modifier = Modifier.testTag("home_show_all_quotes")
                ) { Text("Ver todos", fontSize = 12.sp) }
            }
            }

            item(key = "home_plan_banner") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isPro) "PLANO PRO ATIVO" else "PLANO GRATUITO",
                            color = NavyPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val used = savedHistory.count {
                            val (from, until) = com.example.billing.PlanPolicy.currentMonthBounds()
                            it.quote.createdAt >= from && it.quote.createdAt < until
                        }
                        Text(if (isPro) "Orçamentos ilimitados" else
                            "${used.coerceAtMost(5)}/5 novos orçamentos criados neste mês",
                            fontSize = 11.sp, color = SlateMuted)
                    }
                    if (!isPro) {
                        Button(onClick = onUpgrade,
                            modifier = Modifier.testTag("home_upgrade_pro")) {
                            Text("Conhecer Pro")
                        }
                    }
                }
            }

            // Histórico de verdade, independente de busca e filtros.
            // As propostas existentes ficam no alto da Home, como nas primeiras versões.
            if (savedHistory.isNotEmpty()) {
                item(key = "home_recent_history_header") {
                    Text(
                        "Últimos orçamentos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyPrimary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
                    )
                }
                items(savedHistory.take(3), key = { "recent_quote_${it.quote.id}" }) { entry ->
                    RecentSavedQuoteCard(
                        quoteWithItems = entry,
                        onClick = { onViewDetail(entry.quote.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                            .testTag("home_recent_quote_${entry.quote.id}")
                    )
                }
            }

            // O painel financeiro não deve empurrar o histórico para fora da tela.
            item {
            DashboardMetricsBanner(
                metrics = metrics,
                selectedPeriod = selectedPeriod,
                onPeriodChange = viewModel::onPeriodChanged,
                expanded = financeExpanded,
                onToggleExpanded = {
                    if (isPro) financeExpanded = !financeExpanded else onUpgrade()
                },
                locked = !isPro
            )
            }

            // Search & Filters
            item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Buscar por cliente ou número...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_quotes_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status Chips
                val filterOptions = listOf(
                    "ALL" to "Todos",
                    "PENDING" to "Pendentes",
                    "APPROVED" to "Aprovados",
                    "COMPLETED" to "Concluídos",
                    "REJECTED" to "Recusados"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filterOptions) { (code, label) ->
                        val isSelected = selectedStatus == code
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onStatusFilterChanged(code) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("filter_chip_$code")
                        )
                    }
                }
            }
            }

            // Quotes List
            if (quotes.isEmpty()) {
                item {
                EmptyQuotesView(
                    searchQuery = searchQuery,
                    selectedStatus = selectedStatus,
                    onNewQuote = onNewQuote,
                    onRestoreData = onRestoreData,
                    savedQuoteCount = savedHistory.size
                )
                }
            } else {
                    items(quotes, key = { it.quote.id }) { quoteWithItems ->
                        QuoteCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            quoteWithItems = quoteWithItems,
                            hasPayments = paidQuoteIds.contains(quoteWithItems.quote.id),
                            onCardClick = { onViewDetail(quoteWithItems.quote.id) },
                            onEdit = { onEditQuote(quoteWithItems.quote.id) },
                            onDuplicate = {
                                viewModel.duplicateQuote(quoteWithItems.quote.id) { newId ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Orçamento duplicado com sucesso!")
                                    }
                                }
                            },
                            onDelete = { quoteToDelete = quoteWithItems.quote },
                            onStatusChange = { newStatus ->
                                viewModel.updateStatus(quoteWithItems.quote.id, newStatus)
                            },
                            onSharePdf = {
                                scope.launch {
                                    try {
                                        val pdfFile = PdfGenerator.generateQuotePdf(context, quoteWithItems, companyProfile)
                                        val pdfUri = PdfGenerator.getFileUri(context, pdfFile)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, pdfUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Orçamento ${quoteWithItems.quote.quoteNumber}")
                                            putExtra(Intent.EXTRA_TEXT, "Segue em anexo o orçamento ${quoteWithItems.quote.quoteNumber} no valor de ${Formatters.formatCurrency(quoteWithItems.quote.total)}.")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Orçamento PDF"))
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Erro ao gerar PDF: ${e.localizedMessage}")
                                    }
                                }
                            },
                            onSendWhatsApp = {
                                scope.launch {
                                    try {
                                        val pdfFile = PdfGenerator.generateQuotePdf(context, quoteWithItems, companyProfile)
                                        val pdfUri = PdfGenerator.getFileUri(context, pdfFile)

                                        val message = "Olá, ${quoteWithItems.quote.clientName.ifBlank { "Cliente" }}! Segue o orçamento ${quoteWithItems.quote.quoteNumber} no valor de ${Formatters.formatCurrency(quoteWithItems.quote.total)}, válido até ${Formatters.formatDate(quoteWithItems.quote.validUntil)}."

                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, pdfUri)
                                            putExtra(Intent.EXTRA_TEXT, message)
                                            setPackage("com.whatsapp")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        try {
                                            context.startActivity(shareIntent)
                                        } catch (_: Exception) {
                                            // Fallback to generic share
                                            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, pdfUri)
                                                putExtra(Intent.EXTRA_TEXT, message)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(fallbackIntent, "Compartilhar Orçamento"))
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Erro ao compartilhar: ${e.localizedMessage}")
                                    }
                                }
                            }
                        )
                    }
            }
        }
    }

    if (showQuickCalculator) {
        CalculatorDialog(
            onDismiss = { showQuickCalculator = false },
            confirmLabel = "Copiar resultado",
            onUseResult = { result ->
                val value = java.math.BigDecimal.valueOf(result).stripTrailingZeros()
                    .toPlainString().replace('.', ',')
                clipboard.setText(AnnotatedString(value))
                showQuickCalculator = false
                scope.launch { snackbarHostState.showSnackbar("Resultado copiado. Você pode colar em um orçamento.") }
            }
        )
    }

    // Delete confirmation dialog
    quoteToDelete?.let { quote ->
        AlertDialog(
            onDismissRequest = { quoteToDelete = null },
            title = { Text("Excluir Orçamento?", fontWeight = FontWeight.Bold) },
            text = { Text("Tem certeza que deseja excluir o orçamento ${quote.quoteNumber} do cliente \"${quote.clientName}\"? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuote(quote)
                        quoteToDelete = null
                        scope.launch { snackbarHostState.showSnackbar("Orçamento excluído com sucesso.") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { quoteToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Cartão resumido do histórico salvo: origem não filtrada e link ao orçamento real. */
@Composable
private fun RecentSavedQuoteCard(
    quoteWithItems: QuoteWithItems,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quote = quoteWithItems.quote
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    quote.quoteNumber,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(QuoteStatus.fromCode(quote.status))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                quote.clientName.ifBlank { "Cliente não informado" },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Emissão: ${Formatters.formatDate(quote.issueDate)}",
                    fontSize = 11.sp, color = SlateMuted)
                Text(Formatters.formatCurrency(quote.total),
                    color = EmeraldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun DashboardMetricsBanner(
    metrics: com.example.data.repository.DashboardMetrics,
    selectedPeriod: String,
    onPeriodChange: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    locked: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Propostas aprovadas", fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f))
                    Text(Formatters.formatCurrency(metrics.totalApprovedRevenue), fontSize = 19.sp,
                        fontWeight = FontWeight.Bold, color = EmeraldAccent)
                }
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.testTag("financial_details_toggle")
                ) {
                    Text(if (locked) "Financeiro • Pro" else if (expanded) "Ocultar detalhes" else "Ver financeiro",
                        color = Color.White, fontSize = 12.sp)
                }
            }

            if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(listOf(
                    "ALL" to "Todos", "MONTH" to "Este mês",
                    "LAST_MONTH" to "Mês anterior", "YEAR" to "Este ano"
                )) { (code, label) ->
                    FilterChip(
                        selected = selectedPeriod == code,
                        onClick = { onPeriodChange(code) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recebido no período: ${Formatters.formatCurrency(metrics.totalReceived)}",
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Saldo a receber das propostas do período: ${Formatters.formatCurrency(metrics.totalReceivable)}",
                color = Color.White, fontSize = 12.sp)
            Text("Propostas pendentes: ${Formatters.formatCurrency(metrics.pendingValue)}",
                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(9.dp))
            val scale = maxOf(1.0, metrics.totalApprovedRevenue, metrics.totalReceived, metrics.totalReceivable)
            listOf(
                "Aprovado" to metrics.totalApprovedRevenue,
                "Recebido" to metrics.totalReceived,
                "A receber" to metrics.totalReceivable
            ).forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    Text(Formatters.formatCurrency(value), color = Color.White, fontSize = 11.sp)
                }
                LinearProgressIndicator(
                    progress = { (value / scale).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = EmeraldAccent,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(7.dp))
            }
            if (metrics.overdueCount > 0) {
                Text("${metrics.overdueCount} proposta(s) vencida(s) aguardando retorno.",
                    color = Color.Yellow, fontSize = 12.sp)
            }
            Text("As propostas usam a data de emissão; os recebimentos usam a data informada no pagamento.",
                fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Total", value = "${metrics.totalQuotes}")
                MetricItem(label = "Pendentes", value = "${metrics.pendingCount}")
                MetricItem(label = "Aprov./Concl.", value = "${metrics.approvedCount}")
            }
            } // expanded
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun QuoteCard(
    quoteWithItems: QuoteWithItems,
    hasPayments: Boolean = false,
    onCardClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    onSharePdf: () -> Unit,
    onSendWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quote = quoteWithItems.quote
    val items = quoteWithItems.items
    val statusEnum = QuoteStatus.fromCode(quote.status)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("quote_card_${quote.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Number + Status Badge + 3-dots Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quote.quoteNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NavyPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = statusEnum)
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp).testTag("quote_menu_button_${quote.id}")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opções")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar Orçamento") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            enabled = !hasPayments,
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicar") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { menuExpanded = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("Marcar como Aprovado") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldAccent) },
                            onClick = { menuExpanded = false; onStatusChange("APPROVED") }
                        )
                        DropdownMenuItem(
                            text = { Text("Marcar como Concluído") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { menuExpanded = false; onStatusChange("COMPLETED") }
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !hasPayments,
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            if (hasPayments) {
                Text("Recebimentos registrados • abrir para ver recibos",
                    color = EmeraldAccent, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Client Name & Contact
            Text(
                text = quote.clientName.ifBlank { "Cliente não especificado" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Items Summary
            val itemsSummary = if (items.isNotEmpty()) {
                val preview = items.take(2).joinToString(", ") { it.description }
                if (items.size > 2) "$preview (+${items.size - 2})" else preview
            } else "Nenhum item"

            Text(
                text = "${items.size} ${if (items.size == 1) "item" else "itens"}: $itemsSummary",
                fontSize = 12.sp,
                color = SlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dates and Total Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Emissão: ${Formatters.formatDate(quote.issueDate)}",
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                    Text(
                        text = "Válido até: ${Formatters.formatDate(quote.validUntil)}",
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                }

                Text(
                    text = Formatters.formatCurrency(quote.total),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = EmeraldAccent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSharePdf,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("quote_pdf_button_${quote.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSendWhatsApp,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("quote_whatsapp_button_${quote.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyQuotesView(
    searchQuery: String,
    selectedStatus: String,
    onNewQuote: () -> Unit,
    onRestoreData: () -> Unit,
    savedQuoteCount: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = NavyPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (savedQuoteCount > 0 || searchQuery.isNotBlank() || selectedStatus != "ALL")
                    "Nenhum resultado para os filtros"
                else "Nenhum orçamento cadastrado",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (savedQuoteCount > 0 || searchQuery.isNotBlank() || selectedStatus != "ALL")
                    "O histórico acima continua disponível. Toque em Ver todos para retirar os filtros."
                    else "Ao salvar seu primeiro orçamento, ele aparecerá automaticamente aqui.",
                fontSize = 14.sp,
                color = SlateMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (savedQuoteCount == 0 && searchQuery.isBlank() && selectedStatus == "ALL") {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onNewQuote,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criar Primeiro Orçamento")
                }
                if (com.example.BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRestoreData,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("empty_history_restore_backup")
                    ) {
                        Text("Recuperar histórico de outra versão", fontSize = 12.sp)
                    }
                    Text(
                        "Na instalação anterior, abra Empresa > Backup > Exportar. Nesta versão, entre em Empresa > Backup > Restaurar.",
                        color = SlateMuted,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
