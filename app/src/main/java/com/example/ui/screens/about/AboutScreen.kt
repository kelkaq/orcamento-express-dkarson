package com.example.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.billing.PlanTier
import com.example.ui.theme.NavyPrimary

/** Mostra dados reais do build, nunca versões ou assinaturas inventadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    tier: PlanTier,
    isDemo: Boolean,
    onlineConfigured: Boolean,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPlan: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Sobre o aplicativo", color = androidx.compose.ui.graphics.Color.White) },
            navigationIcon = { TextButton(onClick = onBack) {
                Text("Voltar", color = androidx.compose.ui.graphics.Color.White)
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyPrimary)
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("about_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Orçamento Express", fontWeight = FontWeight.Bold)
                        Text("Desenvolvido por DKARSON")
                        Text("Versão instalada: ${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})",
                            modifier = Modifier.testTag("about_app_version"))
                        Text("Ambiente: " + if (BuildConfig.DEBUG) "teste/homologação" else "produção")
                        Text("Pacote: ${BuildConfig.APPLICATION_ID}")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Seu plano e seus dados", fontWeight = FontWeight.Bold)
                        Text("Plano neste aparelho: " + when (tier) {
                            PlanTier.FREE -> "Gratuito"
                            PlanTier.OFFLINE -> "Pro Offline"
                            PlanTier.ONLINE -> "Pro Online"
                        } + if (isDemo) " (somente demonstração)" else "")
                        Text("Armazenamento principal: banco local no dispositivo (Room).")
                        Text("Nuvem: " + if (onlineConfigured) "servidor configurado; exige acesso e assinatura válida" else
                            "não configurada nesta compilação")
                        Text("Entrar com Google ou e-mail é opcional para uso local. A recuperação em outro aparelho requer cópia de segurança.")
                        Text("Cada instalação tem um único espaço de trabalho local. Sair da conta não oculta nem apaga os dados deste celular.")
                        OutlinedButton(
                            onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()
                        ) { Text("Minha conta e cópias de segurança") }
                        OutlinedButton(
                            onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()
                        ) { Text("Ver planos e restaurar compras") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Privacidade e atendimento", fontWeight = FontWeight.Bold)
                        Text("Os orçamentos ficam no aparelho. Recursos online só são usados quando configurados e autorizados.")
                        Text("Antes de trocar ou remover o aplicativo, exporte um backup em Empresa. A instalação de teste não compartilha os dados da versão de produção.")
                        Text("Para suporte, procure o canal de atendimento indicado na página oficial da loja quando o aplicativo for publicado.")
                    }
                }
            }
        }
    }
}
