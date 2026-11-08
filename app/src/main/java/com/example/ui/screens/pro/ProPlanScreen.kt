package com.example.ui.screens.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.billing.PlanPolicy
import com.example.billing.PlanTier
import com.example.billing.PlayBillingManager
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPlanScreen(
    manager: PlayBillingManager,
    onBack: () -> Unit,
    onOpenOnline: () -> Unit = {}
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Planos do Orçamento Express", color = androidx.compose.ui.graphics.Color.White) },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Text("Voltar", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyPrimary)
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Você escolhe como usar", fontWeight = FontWeight.Bold)
                Text("Não é necessário pagar mensalidade para emitir orçamentos. " +
                    "Comprar a licença offline é uma opção; a assinatura é somente para serviços online.")
            }
            if (BuildConfig.DEBUG) item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("HOMOLOGAÇÃO — NÃO É UMA COMPRA", fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("Neste aplicativo de teste, você pode experimentar todas as ferramentas do Pro Offline sem pagar. Não libera nuvem nem assinatura real.")
                        Button(
                            onClick = { manager.setDemoEnabled(!state.demoEnabled) },
                            modifier = Modifier.fillMaxWidth().testTag("debug_demo_toggle")
                        ) {
                            Text(if (state.demoEnabled) "Desativar Pro de teste" else "Ativar Pro de teste")
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("GRATUITO", fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("R$ 0")
                        Text("Até ${PlanPolicy.FREE_QUOTES_PER_MONTH} novos orçamentos por mês.")
                        Text("Calculadora simples, PDFs, clientes, histórico, agenda, lembretes e backup manual/local.")
                        Text("A consulta e edição de documentos já criados continuam disponíveis.")
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldAccent.copy(alpha=0.10f))
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("PRO OFFLINE — COMPRA ÚNICA", color = NavyPrimary,
                            fontWeight = FontWeight.Bold)
                        Text(state.offlinePrice ?: "Preço definido na Google Play",
                            fontWeight = FontWeight.Bold)
                        Text("Pague uma única vez. Orçamentos ilimitados, cálculo de preço e lucro por profissão, " +
                            "ordens de serviço, recibos, modelos e relatórios de custos. Funciona sem internet.")
                        if (state.tier != PlanTier.FREE) {
                            Text("Recursos offline já habilitados no seu plano.")
                        } else {
                            Button(
                                enabled = state.canBuyOffline && !state.isLoading,
                                onClick = { (context as? Activity)?.let(manager::buyOffline) },
                                modifier = Modifier.fillMaxWidth().testTag("buy_offline_once")
                            ) { Text("Comprar Pro Offline uma vez") }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("PRO ONLINE — ASSINATURA MENSAL", color = NavyPrimary,
                            fontWeight = FontWeight.Bold)
                        Text(
                            (state.onlinePrice ?: "Preço definido na Google Play") + " / mês",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Inclui tudo do Pro Offline e serviços continuados: cópias na nuvem, " +
                            "acesso do mesmo histórico em outros aparelhos, aprovação por link " +
                            "e acompanhamento de clientes.")
                        Text("Renovação automática pela Google Play. Pode cancelar quando quiser; " +
                            "os recursos online ficam disponíveis até o fim do período pago. " +
                            "Seus documentos locais nunca são apagados.")
                        if (state.isOnline) Text("Plano Online ativo.", color = NavyPrimary)
                        else Button(
                            enabled = state.canSubscribeOnline && !state.isLoading,
                            onClick = { (context as? Activity)?.let(manager::buyOnline) },
                            modifier = Modifier.fillMaxWidth().testTag("subscribe_online")
                        ) { Text("Assinar Pro Online") }
                        OutlinedButton(
                            onClick = onOpenOnline, modifier = Modifier.fillMaxWidth()
                        ) { Text("Abrir central online / sincronização") }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { manager.restorePurchases() },
                    modifier = Modifier.fillMaxWidth().testTag("restore_purchases")
                ) { Text("Restaurar minhas compras") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/account/subscriptions")))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Gerenciar ou cancelar assinatura na Google Play")
                }
                Text("Nenhum recurso pago pode ser cobrado enquanto os produtos e a " +
                    "verificação segura não estiverem ativos. Os valores são os mostrados " +
                    "pela Google Play no momento da compra.")
                Text(state.message)
            }
        }
    }
}
