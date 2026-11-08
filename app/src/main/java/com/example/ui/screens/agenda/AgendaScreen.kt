package com.example.ui.screens.agenda

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.AgendaEventEntity
import com.example.data.repository.QuoteRepository
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SlateMuted
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun agendaDate(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date(value))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    repository: QuoteRepository,
    initialQuoteId: Long? = null,
    onViewQuote: (Long) -> Unit
) {
    val events by repository.agendaEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showEditor by remember(initialQuoteId) { mutableStateOf(initialQuoteId != null) }
    var editing by remember { mutableStateOf<AgendaEventEntity?>(null) }
    var linkedQuote by remember(initialQuoteId) { mutableStateOf<Long?>(initialQuoteId) }
    var title by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("FOLLOW_UP") }
    var notes by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf(System.currentTimeMillis() + 86_400_000L) }
    var remindHours by remember { mutableStateOf(2) }
    var showAll by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            snackbar.showSnackbar(
                if (granted) "Lembretes ativados. O Android entrega avisos conforme sua programação."
                else "Avisos do sistema desativados. Os compromissos continuam visíveis na agenda."
            )
        }
    }
    fun startEdit(value: AgendaEventEntity?) {
        editing = value
        linkedQuote = value?.quoteId
        title = value?.title.orEmpty()
        name = value?.clientName.orEmpty()
        phone = value?.clientPhone.orEmpty()
        kind = value?.kind ?: "FOLLOW_UP"
        notes = value?.notes.orEmpty()
        scheduledAt = value?.scheduledAt ?: System.currentTimeMillis() + 86_400_000L
        remindHours = if (value?.remindAt == null) -1 else
            ((value.scheduledAt - value.remindAt) / 3_600_000L).toInt().coerceAtLeast(0)
        showEditor = true
    }

    LaunchedEffect(initialQuoteId) {
        val id = initialQuoteId ?: return@LaunchedEffect
        val quote = repository.getQuoteWithItems(id)?.quote ?: return@LaunchedEffect
        linkedQuote = id
        name = quote.clientName
        phone = quote.clientPhone
        title = "Retornar orçamento ${quote.quoteNumber}"
        kind = "FOLLOW_UP"
        scheduledAt = System.currentTimeMillis() + 86_400_000L
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Agenda e retornos", color = androidx.compose.ui.graphics.Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { startEdit(null) },
                modifier = Modifier.testTag("agenda_new_event")) {
                Text("+", modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Meus compromissos", fontWeight = FontWeight.Bold)
                    Text("Agendamentos e retornos comerciais", color = SlateMuted)
                }
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Pendentes" else "Ver todos")
                }
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                OutlinedButton(
                    onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) { Text("Permitir avisos de compromissos") }
            }
            val visible = if (showAll) events else events.filter { it.status == "PENDING" }
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("agenda_list"),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (visible.isEmpty()) item {
                    Text("Sem compromissos nesta visualização. Toque em + para agendar um serviço ou retorno.",
                        modifier = Modifier.padding(20.dp), color = SlateMuted)
                }
                items(visible, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(entry.title, fontWeight = FontWeight.Bold)
                            Text(agendaDate(entry.scheduledAt), color = NavyPrimary)
                            if (entry.clientName.isNotBlank()) Text(entry.clientName)
                            Text(
                                when (entry.status) {
                                    "DONE" -> "Concluído"
                                    "CANCELED" -> "Cancelado"
                                    else -> if (entry.scheduledAt < System.currentTimeMillis()) "Pendente — vencido" else "Pendente"
                                },
                                color = SlateMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { startEdit(entry) }) { Text("Editar") }
                                if (entry.status == "PENDING") TextButton(onClick = {
                                    scope.launch {
                                        runCatching { repository.saveAgendaEvent(entry.copy(status = "DONE")) }
                                            .onFailure { snackbar.showSnackbar("Não foi possível concluir o compromisso.") }
                                    }
                                }) { Text("Concluir") }
                                entry.quoteId?.let { quoteId ->
                                    TextButton(onClick = { onViewQuote(quoteId) }) { Text("Orçamento") }
                                }
                            }
                            if (entry.kind == "FOLLOW_UP" && entry.status == "PENDING") {
                                OutlinedButton(onClick = {
                                    val message = "Olá${if(entry.clientName.isBlank()) "" else " ${entry.clientName}"}, " +
                                        "tudo bem? Estou entrando em contato para saber se conseguiu avaliar o orçamento. " +
                                        "Fico à disposição para esclarecer suas dúvidas."
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Enviar mensagem ao cliente"))
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Preparar mensagem de acompanhamento")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(85.dp)) }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { if (!saving) showEditor = false },
            title = { Text(if (editing == null) "Novo compromisso" else "Editar compromisso") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        label = { Text("Título do compromisso") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Cliente") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it },
                        label = { Text("Telefone (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = kind == "FOLLOW_UP", onClick = { kind = "FOLLOW_UP" },
                            label = { Text("Retorno") })
                        FilterChip(selected = kind == "SERVICE", onClick = { kind = "SERVICE" },
                            label = { Text("Serviço") })
                    }
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = scheduledAt }
                        DatePickerDialog(context, { _, year, month, day ->
                            val selection = Calendar.getInstance().apply {
                                timeInMillis = scheduledAt
                                set(year, month, day)
                            }
                            scheduledAt = selection.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Data: ${agendaDate(scheduledAt)}") }
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = scheduledAt }
                        TimePickerDialog(context, { _, hour, minute ->
                            scheduledAt = Calendar.getInstance().apply {
                                timeInMillis = scheduledAt
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                            }.timeInMillis
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ajustar horário") }
                    Text("Avisar antes do compromisso:")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(-1 to "Sem aviso", 2 to "2 h", 24 to "1 dia").forEach { (hours, label) ->
                            FilterChip(selected = remindHours == hours, onClick = { remindHours = hours },
                                label = { Text(label) })
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it },
                        label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Text("Avisos são aproximados e dependem das permissões do Android.",
                        color = SlateMuted)
                }
            },
            confirmButton = {
                Button(enabled = !saving && title.isNotBlank(), onClick = {
                    saving = true
                    scope.launch {
                        try {
                            repository.saveAgendaEvent(AgendaEventEntity(
                                id = editing?.id ?: 0,
                                quoteId = linkedQuote,
                                kind = kind,
                                title = title,
                                clientName = name,
                                clientPhone = phone,
                                scheduledAt = scheduledAt,
                                remindAt = if (remindHours < 0) null else scheduledAt - remindHours * 3_600_000L,
                                status = editing?.status ?: "PENDING",
                                notes = notes
                            ))
                            showEditor = false
                            snackbar.showSnackbar("Compromisso salvo.")
                        } catch (error: Exception) {
                            snackbar.showSnackbar(error.localizedMessage ?: "Falha ao salvar.")
                        } finally { saving = false }
                    }
                }, modifier = Modifier.testTag("agenda_save_event")) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }, enabled = !saving) { Text("Cancelar") }
            }
        )
    }
}
