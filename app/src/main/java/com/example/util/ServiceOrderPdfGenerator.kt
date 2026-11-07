package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.QuoteWithItems
import com.example.data.entity.ServiceOrderEntity
import java.io.File
import java.io.FileOutputStream

/** Ordem de serviço comercial independente do recibo e sem duplicar valor financeiro. */
object ServiceOrderPdfGenerator {
    fun generate(
        context: Context,
        quoteData: QuoteWithItems,
        order: ServiceOrderEntity,
        company: CompanyProfileEntity
    ): File {
        require(order.quoteId == quoteData.quote.id)
        val document = PdfDocument()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 43, 69)
            textSize = 11f
        }
        val title = company.tradeName.ifBlank { company.legalName.ifBlank { "Prestador de serviço" } }
        val output = mutableListOf<Pair<String, Boolean>>()

        fun line(content: String = "", bold: Boolean = false) {
            output.add(content to bold)
        }
        fun wrap(label: String, content: String) {
            if (content.isBlank()) return
            line(label, true)
            val words = content.replace("\r", "").replace("\n", " \n ").split(Regex("\\s+"))
            var buffer = ""
            ink.typeface = Typeface.DEFAULT
            ink.textSize = 11f
            for (word in words) {
                if (word == "\n") {
                    line(buffer)
                    buffer = ""
                    continue
                }
                val next = if (buffer.isBlank()) word else "$buffer $word"
                if (ink.measureText(next) > 505f && buffer.isNotBlank()) {
                    line(buffer)
                    buffer = word
                } else buffer = next
            }
            if (buffer.isNotBlank()) line(buffer)
            line()
        }

        val q = quoteData.quote
        line("Ordem de Serviço OS-${order.id.toString().padStart(5, '0')}", true)
        line("Orçamento de origem: ${q.quoteNumber}")
        line("Cliente: ${q.clientName}")
        if (q.clientPhone.isNotBlank()) line("Contato: ${Formatters.formatPhone(q.clientPhone)}")
        line("Valor do orçamento: ${Formatters.formatCurrency(q.total)}")
        line("Situação: " + when (order.status) {
            "COMPLETED" -> "Concluída"
            "IN_PROGRESS" -> "Em execução"
            else -> "Agendada"
        })
        if (order.scheduledAt != null) line("Data prevista: ${Formatters.formatDate(order.scheduledAt)}")
        if (order.completedAt != null) line("Conclusão: ${Formatters.formatDate(order.completedAt)}")
        if (order.responsible.isNotBlank()) line("Responsável: ${order.responsible}")
        line()
        line("SERVIÇOS VINCULADOS", true)
        quoteData.items.forEach { item ->
            wrap("", "${item.description} • ${item.quantity} ${item.unit}")
        }
        wrap("EXECUÇÃO / DESCRIÇÃO", order.description)
        wrap("MATERIAIS UTILIZADOS", order.materials)
        wrap("OBSERVAÇÕES", order.notes)
        line("Documento de ordem de serviço. Não é nota fiscal nem recibo de pagamento.")
        line("Assinatura do responsável: _________________________________")

        try {
            var index = 0
            var pageNo = 0
            do {
                pageNo++
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                val canvas = page.canvas
                ink.color = Color.rgb(13, 43, 69)
                canvas.drawRect(0f, 0f, 595f, 9f, ink)
                ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                ink.textSize = 16f
                canvas.drawText(title.take(55), 42f, 47f, ink)
                ink.textSize = 11f
                var y = 84f
                while (index < output.size && y < 778f) {
                    val (content, bold) = output[index++]
                    ink.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    ink.textSize = if (bold) 12f else 11f
                    ink.color = if (bold) Color.rgb(13, 43, 69) else Color.rgb(35, 43, 55)
                    if (content.isNotBlank()) canvas.drawText(content.take(115), 42f, y, ink)
                    y += if (bold) 22f else 17f
                }
                ink.color = Color.GRAY
                ink.typeface = Typeface.DEFAULT
                ink.textSize = 8f
                canvas.drawText("Orçamento Express • OS ${order.id} • Página $pageNo", 42f, 814f, ink)
                document.finishPage(page)
            } while (index < output.size)
            val dir = File(context.cacheDir, "docs").apply { mkdirs() }
            return File(dir, "os_${q.id}_${order.id}.pdf").also { file ->
                FileOutputStream(file).use { document.writeTo(it) }
            }
        } finally {
            document.close()
        }
    }
}
