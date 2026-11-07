package com.example.ui.screens.quote_edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ClientEntity
import com.example.ui.components.CalculatorDialog
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.QuoteItemEntity
import com.example.domain.model.QuoteStatus
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SlateLightBg
import com.example.ui.theme.SlateMuted
import com.example.ui.viewmodel.QuoteEditorViewModel
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditorScreen(
    viewModel: QuoteEditorViewModel,
    onNavigateBack: () -> Unit,
    onQuoteSaved: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableClients by viewModel.availableClients.collectAsStateWithLifecycle()
    val availableCatalog by viewModel.availableCatalog.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showClientPickerSheet by remember { mutableStateOf(false) }
    var showCatalogPickerSheet by remember { mutableStateOf(false) }
    var clientSearch by remember { mutableStateOf("") }
    var catalogSearch by remember { mutableStateOf("") }
    var applyCalculatorResult by remember { mutableStateOf<((Double) -> Unit)?>(null) }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.quoteId > 0) "Editar Orçamento" else "Novo Orçamento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveQuote { savedId -> onQuoteSaved(savedId) } },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !uiState.isSaving,
                        modifier = Modifier.testTag("save_quote_top_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (uiState.isSaving) "Salvando..." else "Salvar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(SlateLightBg)
        ) {
            // Header: Quote Number & Status
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
                            OutlinedTextField(
                                value = uiState.quoteNumber,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Número automático do orçamento") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quote_number_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status Selector
                        Text("Status da Proposta", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val statusList = listOf(
                                QuoteStatus.PENDING,
                                QuoteStatus.APPROVED,
                                QuoteStatus.COMPLETED,
                                QuoteStatus.REJECTED
                            )
                            statusList.forEach { s ->
                                FilterChip(
                                    selected = uiState.status == s.code,
                                    onClick = { viewModel.onStatusChanged(s.code) },
                                    label = { Text(s.label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NavyPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Validity Presets
                        Text("Validade do Orçamento", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(7, 15, 30).forEach { days ->
                                OutlinedButton(
                                    onClick = { viewModel.onValidityDaysPresetSelected(days) },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("+$days dias", fontSize = 11.sp)
                                }
                            }
                        }
                        Text(
                            text = "Válido até: ${Formatters.formatDate(uiState.validUntil)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NavyPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Client Section
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dados do Cliente", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            TextButton(
                                onClick = { showClientPickerSheet = true },
                                modifier = Modifier.testTag("select_client_button")
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Selecionar Cliente", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.clientName,
                            onValueChange = { viewModel.onClientNameChanged(it) },
                            label = { Text("Nome do Cliente *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("client_name_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.clientDocument,
                                onValueChange = { viewModel.onClientDocumentChanged(it) },
                                label = { Text("CPF / CNPJ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("client_doc_input")
                            )
                            OutlinedTextField(
                                value = uiState.clientPhone,
                                onValueChange = { viewModel.onClientPhoneChanged(it) },
                                label = { Text("Telefone / WhatsApp") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("client_phone_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.clientEmail,
                            onValueChange = { viewModel.onClientEmailChanged(it) },
                            label = { Text("E-mail") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.clientAddress,
                            onValueChange = { viewModel.onClientAddressChanged(it) },
                            label = { Text("Endereço / Local da Prestação") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Items Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Itens do Orçamento (${uiState.items.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )

                    Row {
                        OutlinedButton(
                            onClick = { showCatalogPickerSheet = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_add_from_catalog")
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Catálogo", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.addItem() },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_add_item")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Item", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Items List
            itemsIndexed(uiState.items) { index, item ->
                QuoteItemRowCard(
                    index = index,
                    item = item,
                    onUpdateItem = { updated -> viewModel.updateItem(index, updated) },
                    onRemove = { viewModel.removeItem(index) },
                    onOpenCalculator = { apply -> applyCalculatorResult = apply }
                )
            }

            // Financial Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resumo Financeiro", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Subtotal
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal dos Itens", color = SlateMuted, fontSize = 14.sp)
                            Text(Formatters.formatCurrency(uiState.subtotal), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = CardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Discount Row
                        Text("Desconto", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SlateMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Toggle R$ or %
                            FilterChip(
                                selected = uiState.discountType == "FIXED",
                                onClick = { viewModel.onDiscountTypeChanged("FIXED") },
                                label = { Text("R$ (Fixo)") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White)
                            )
                            FilterChip(
                                selected = uiState.discountType == "PERCENTAGE",
                                onClick = { viewModel.onDiscountTypeChanged("PERCENTAGE") },
                                label = { Text("% (Percentual)") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White)
                            )

                            EditableDecimalField(
                                value = uiState.discountValue,
                                onValueChange = viewModel::onDiscountValueChanged,
                                placeholder = "0",
                                onCalculator = {
                                    applyCalculatorResult = { amount -> viewModel.onDiscountValueChanged(amount) }
                                },
                                modifier = Modifier.weight(1f).testTag("discount_input")
                            )
                        }

                        if (uiState.discountCalculated > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "- " + Formatters.formatCurrency(uiState.discountCalculated),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shipping / Extra Fee
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Frete / Deslocamento (R$):", fontSize = 13.sp, color = SlateMuted, modifier = Modifier.weight(1.2f))
                            EditableDecimalField(
                                value = uiState.shippingFee,
                                onValueChange = viewModel::onShippingFeeChanged,
                                placeholder = "0,00",
                                onCalculator = {
                                    applyCalculatorResult = { amount -> viewModel.onShippingFeeChanged(amount) }
                                },
                                modifier = Modifier.weight(1f).testTag("shipping_fee_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Total Geral Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyPrimary, shape = RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL GERAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    text = Formatters.formatCurrency(uiState.total),
                                    color = EmeraldAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }
                        }
                    }
                }
            }

            // Payment Terms & Notes
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Condições de Pagamento e Observações", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = uiState.paymentMethod,
                            onValueChange = { viewModel.onPaymentMethodChanged(it) },
                            label = { Text("Forma de Pagamento (ex: PIX, Boleto, Cartão)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.paymentTerms,
                            onValueChange = { viewModel.onPaymentTermsChanged(it) },
                            label = { Text("Condições (ex: 50% entrada e 50% na conclusão)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = { viewModel.onNotesChanged(it) },
                            label = { Text("Observações e Termos de Garantia") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Save Action at Bottom
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { viewModel.saveQuote { savedId -> onQuoteSaved(savedId) } },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_quote_bottom_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar Orçamento", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Modal Sheet: Pick Client
    if (showClientPickerSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showClientPickerSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Selecionar Cliente Cadastrado", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientSearch,
                    onValueChange = { clientSearch = it },
                    label = { Text("Buscar por nome ou telefone") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val filteredClients = availableClients.filter {
                    clientSearch.isBlank() || it.name.contains(clientSearch, ignoreCase = true) ||
                        it.phone.filter(Char::isDigit).contains(clientSearch.filter(Char::isDigit).takeIf(String::isNotBlank) ?: "___")
                }

                if (filteredClients.isEmpty()) {
                    Text("Nenhum cliente encontrado. Você pode informar apenas o nome no orçamento.", color = SlateMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(filteredClients.size) { i ->
                            val client = filteredClients[i]
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.onClientSelected(client)
                                        showClientPickerSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = NavyPrimary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(client.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        if (client.phone.isNotBlank()) Text(Formatters.formatPhone(client.phone), fontSize = 12.sp, color = SlateMuted)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Sheet: Pick From Catalog
    if (showCatalogPickerSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCatalogPickerSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Adicionar Item do Catálogo", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = catalogSearch,
                    onValueChange = { catalogSearch = it },
                    label = { Text("Buscar serviço ou produto") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val filteredCatalog = availableCatalog.filter {
                    catalogSearch.isBlank() || it.title.contains(catalogSearch, ignoreCase = true) ||
                        it.category.contains(catalogSearch, ignoreCase = true) ||
                        it.description.contains(catalogSearch, ignoreCase = true)
                }

                if (filteredCatalog.isEmpty()) {
                    Text("Nenhum item encontrado para essa busca.", color = SlateMuted, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(filteredCatalog.size) { i ->
                            val item = filteredCatalog[i]
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.addCatalogItem(item)
                                        // Mantenha a seleção aberta para adicionar vários itens de uma vez.
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("${if (item.type == "SERVICE") "Serviço" else "Produto"} • ${item.category}", fontSize = 11.sp, color = SlateMuted)
                                    }
                                    Text(
                                        "${Formatters.formatCurrency(item.unitPrice)} / ${item.unit}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EmeraldAccent
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { showCatalogPickerSheet = false }) {
                    Text("Concluir seleção de itens")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (applyCalculatorResult != null) {
        CalculatorDialog(
            onDismiss = { applyCalculatorResult = null },
            onUseResult = { amount ->
                applyCalculatorResult?.invoke(amount)
                applyCalculatorResult = null
            }
        )
    }
}

@Composable
fun QuoteItemRowCard(
    index: Int,
    item: QuoteItemEntity,
    onUpdateItem: (QuoteItemEntity) -> Unit,
    onRemove: () -> Unit,
    onOpenCalculator: ((Double) -> Unit) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row Title and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Item #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(
                value = item.description,
                onValueChange = { onUpdateItem(item.copy(description = it)) },
                label = { Text("Descrição do Item ou Serviço *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Unit, Qtd, Unit Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableDecimalField(
                    value = item.quantity,
                    onValueChange = { qty -> onUpdateItem(item.copy(quantity = qty)) },
                    placeholder = "Qtd",
                    label = "Qtd",
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = item.unit,
                    onValueChange = { onUpdateItem(item.copy(unit = it)) },
                    label = { Text("Unidade") },
                    placeholder = { Text("un, hr, m²") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                EditableDecimalField(
                    value = item.unitPrice,
                    onValueChange = { price -> onUpdateItem(item.copy(unitPrice = price)) },
                    placeholder = "0,00",
                    label = "Vlr. Unit (R$)",
                    onCalculator = {
                        onOpenCalculator { price -> onUpdateItem(item.copy(unitPrice = price)) }
                    },
                    modifier = Modifier.weight(1.3f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtotal of this item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val totalItem = item.quantity * item.unitPrice
                Text(
                    text = "Total: " + Formatters.formatCurrency(totalItem),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = EmeraldAccent
                )
            }
        }
    }
}


/** Mantém o texto parcial do teclado ("1,", campo vazio etc.) sem zerar o valor a cada tecla. */
@Composable
private fun EditableDecimalField(
    value: Double,
    onValueChange: (Double) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    onCalculator: (() -> Unit)? = null
) {
    fun render(value: Double): String = if (value == 0.0) "" else
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')

    var text by remember { mutableStateOf(render(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        // Não interrompe a digitação "1,", mas recebe resultados aplicados pela calculadora.
        val currentTextValue = text.replace(',', '.').toDoubleOrNull()
        if (!focused || (currentTextValue != null && currentTextValue != value)) {
            text = render(value)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.matches(Regex("""\d{0,12}([,.]\d{0,4})?"""))) {
                text = input
                input.replace(',', '.').toDoubleOrNull()?.let { parsed ->
                    if (parsed.isFinite() && parsed >= 0.0) onValueChange(parsed)
                }
            }
        },
        modifier = modifier.onFocusChanged { state ->
            if (focused && !state.isFocused) {
                if (text.isBlank()) onValueChange(0.0)
                text = render(text.replace(',', '.').toDoubleOrNull() ?: value)
            }
            focused = state.isFocused
        },
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = onCalculator?.let { action ->
            { IconButton(onClick = action) {
                Icon(Icons.Default.Calculate, contentDescription = "Abrir calculadora")
            } }
        }
    )
}
