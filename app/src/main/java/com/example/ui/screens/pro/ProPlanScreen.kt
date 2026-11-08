package com.example.ui.screens.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billing.PlanPolicy
import com.example.billing.PlayBillingManager
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPlanScreen(manager: PlayBillingManager, onBack: () -> Unit) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTerms by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Orçamento Express Pro", color = androidx.compose.ui.graphics.Color.White) },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("Voltar", color = androidx.compose.ui.graphics.Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyPrimary)
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Escolha o seu plano", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("Seus orçamentos anteriores continuam disponíveis mesmo após o cancelamento.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("GRATUITO", fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("R$ 0,00")
                        Text("Até ${PlanPolicy.FREE_QUOTES_PER_MONTH} novos orçamentos por mês.")
                        Text("Cadastro de clientes, calculadora, PDFs de orçamentos e backup.")
                        Text("Pode consultar e compartilhar seus orçamentos existentes sem pagar.")
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldAccent.copy(alpha = 0.10f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PRO — ASSINATURA MENSAL", color = NavyPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.price != null) "${state.price} / mês" else
                                "Preço mensal disponível na Google Play",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Orçamentos ilimitados a cada mês; ordens de serviço, registro de pagamentos, recibos e modelos profissionais.")
                        Text("Ferramentas profissionais disponíveis durante a assinatura ativa.")
                        Text(
                            "Assinatura com renovação automática mensal; cobrada pela Google Play. " +
                                "Pode cancelar a qualquer momento pela Google Play, mantendo o acesso até o fim do período pago.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (state.isPro) {
                            Text("Seu plano Pro está ativo.", color = NavyPrimary, fontWeight = FontWeight.Bold)
                        } else {
                            Button(
                                onClick = { (context as? Activity)?.let(manager::buy) },
                                enabled = state.canSubscribe && !state.isLoading,
                                modifier = Modifier.fillMaxWidth().testTag("subscribe_pro_button")
                            ) { Text("Assinar pela Google Play") }
                        }
                        if (state.isLoading) Text("Consultando Google Play...")
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { manager.restorePurchases() },
                    modifier = Modifier.fillMaxWidth().testTag("restore_pro_purchase")
                ) { Text("Restaurar / verificar assinatura") }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/account/subscriptions")))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Gerenciar ou cancelar na Google Play") }
                TextButton(onClick = { showTerms = true }) { Text("Condições de assinatura") }
            }
        }
    }
    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text("Condições do plano Pro") },
            text = {
                Text(
                    "O plano Pro é uma assinatura mensal de renovação automática. " +
                        "O preço exato, os impostos aplicáveis e as condições de cobrança aparecem na Google Play antes da confirmação. " +
                        "Durante a assinatura ativa, você pode criar orçamentos ilimitados e usar OS, recibos e modelos. " +
                        "Cancelamento: Google Play > Pagamentos e assinaturas > Assinaturas. " +
                        "Ao cancelar, você mantém seus dados e documentos já criados; novas criações Pro deixam de estar disponíveis ao final do período pago. " +
                        "Os usuários gratuitos podem criar 5 novos orçamentos por mês civil. " +
                        "Este aplicativo não verifica transferências PIX nem oferece emissão de nota fiscal."
                )
            },
            confirmButton = { TextButton(onClick = { showTerms = false }) { Text("Entendi") } }
        )
    }
}
