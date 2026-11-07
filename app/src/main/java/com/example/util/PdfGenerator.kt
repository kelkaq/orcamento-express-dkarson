package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val MARGIN_BOTTOM = 810f

    // Columns X coordinates
    private const val COL_ITEM = 44f
    private const val COL_DESC = 74f
    private const val COL_TYPE = 275f
    private const val COL_QTD = 355f
    private const val COL_UNIT = 400f
    private const val COL_PRICE = 475f
    private const val COL_TOTAL = 551f

    private val primaryDark = Color.rgb(13, 43, 69)      // #0D2B45
    private val primaryEmerald = Color.rgb(0, 135, 90)   // #00875A
    private val textPrimary = Color.rgb(26, 32, 44)      // #1A202C
    private val textSecondary = Color.rgb(100, 116, 139) // #64748B
    private val borderLight = Color.rgb(226, 232, 240)   // #E2E8F0
    private val zebraLight = Color.rgb(248, 250, 252)    // #F8FAFC
    private val softCardBg = Color.rgb(241, 245, 249)    // #F1F5F9

    private data class MeasuredItem(
        val index: Int,
        val item: QuoteItemEntity,
        val lines: List<String>,
        val rowHeight: Float
    )

    fun calculatePageCount(items: List<QuoteItemEntity>): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val maxDescWidth = COL_TYPE - COL_DESC - 10f
        val measuredItems = items.mapIndexed { index, item ->
            val descLines = breakTextIntoLines(item.description, maxDescWidth, paint)
            val rowHeight = maxOf(20f, 10f + (descLines.size * 11f))
            MeasuredItem(index, item, descLines, rowHeight)
        }
        val pagesItems = mutableListOf<List<MeasuredItem>>()
        var currentPageItems = mutableListOf<MeasuredItem>()
        var currentY = 205f
        val summaryHeightNeeded = 220f

        for (mItem in measuredItems) {
            if (currentY + mItem.rowHeight > MARGIN_BOTTOM) {
                pagesItems.add(currentPageItems)
                currentPageItems = mutableListOf()
                currentY = 65f + 22f
            }
            currentPageItems.add(mItem)
            currentY += mItem.rowHeight
        }
        pagesItems.add(currentPageItems)

        val needsSeparateSummaryPage = (currentY + summaryHeightNeeded > MARGIN_BOTTOM)
        return if (needsSeparateSummaryPage) pagesItems.size + 1 else pagesItems.size
    }

    fun generateQuotePdf(
        context: Context,
        quoteWithItems: QuoteWithItems,
        company: CompanyProfileEntity
    ): File {
        val quote = quoteWithItems.quote
        val items = quoteWithItems.items

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Measure description lines and heights for each item
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val maxDescWidth = COL_TYPE - COL_DESC - 10f

        val measuredItems = items.mapIndexed { index, item ->
            val descLines = breakTextIntoLines(item.description, maxDescWidth, paint)
            val rowHeight = maxOf(20f, 10f + (descLines.size * 11f))
            MeasuredItem(index, item, descLines, rowHeight)
        }

        // 2. Paginate items
        // Page 1 available space: starts at ~200f after header + client box, ends at MARGIN_BOTTOM (810f)
        // Subsequent pages available space: starts at ~65f after mini-header + table header, ends at MARGIN_BOTTOM
        val pagesItems = mutableListOf<List<MeasuredItem>>()
        var currentPageItems = mutableListOf<MeasuredItem>()
        var currentY = 205f // Page 1 table start
        val summaryHeightNeeded = 220f // Height for totals, payment, notes, signatures

        for (mItem in measuredItems) {
            val isFirstPage = pagesItems.isEmpty()
            val maxY = MARGIN_BOTTOM

            if (currentY + mItem.rowHeight > maxY) {
                // Page full, finish current page
                pagesItems.add(currentPageItems)
                currentPageItems = mutableListOf()
                currentY = 65f + 22f // Next page table start
            }
            currentPageItems.add(mItem)
            currentY += mItem.rowHeight
        }
        pagesItems.add(currentPageItems)

        // Determine if summary fits on the last item page or needs an extra summary page
        val lastPageItemCount = pagesItems.last().size
        val needsSeparateSummaryPage = (currentY + summaryHeightNeeded > MARGIN_BOTTOM)
        val totalPages = if (needsSeparateSummaryPage) pagesItems.size + 1 else pagesItems.size

        // 3. Render PDF document
        val pdfDocument = PdfDocument()

        for (pageIndex in 0 until pagesItems.size) {
            val pageNum = pageIndex + 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = 0f

            if (pageNum == 1) {
                // Full Header + Client Card
                y = drawPageOneHeader(canvas, paint, quote, company, context)
            } else {
                // Continuation Mini Header
                y = drawContinuationHeader(canvas, paint, quote)
            }

            // Draw Table Header
            drawTableHeader(canvas, paint, y)
            y += 22f

            // Draw Table Rows for this page
            val itemsForThisPage = pagesItems[pageIndex]
            for (mItem in itemsForThisPage) {
                drawTableRow(canvas, paint, mItem, y)
                y += mItem.rowHeight
            }

            // If this is the last item page and summary fits here, draw summary
            if (pageNum == pagesItems.size && !needsSeparateSummaryPage) {
                drawSummaryAndFooterContent(canvas, paint, quote, company, y)
            }

            // Draw Page Number and Disclaimer Footer
            drawPageFooter(canvas, paint, pageNum, totalPages)

            pdfDocument.finishPage(page)
        }

        // If summary needed a dedicated final page
        if (needsSeparateSummaryPage) {
            val summaryPageNum = totalPages
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, summaryPageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val y = drawContinuationHeader(canvas, paint, quote)
            drawSummaryAndFooterContent(canvas, paint, quote, company, y + 10f)
            drawPageFooter(canvas, paint, summaryPageNum, totalPages)

            pdfDocument.finishPage(page)
        }

        // Save File
        val docsDir = File(context.cacheDir, "docs").apply { mkdirs() }
        val sanitizedNum = quote.quoteNumber.replace("#", "").replace("-", "_").replace(" ", "_")
        val file = File(docsDir, "orcamento_${sanitizedNum}.pdf")
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        return file
    }

    private fun drawPageOneHeader(
        canvas: Canvas,
        paint: Paint,
        quote: com.example.data.entity.QuoteEntity,
        company: CompanyProfileEntity,
        context: Context
    ): Float {
        // Top Header Accent Bar
        paint.color = primaryDark
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, paint)

        val currentY = 32f

        // Company Logo or Monogram
        val logoWidth = 56f
        val logoHeight = 56f
        var logoDrawn = false
        if (!company.logoPath.isNullOrBlank()) {
            try {
                val logoFile = File(company.logoPath)
                if (logoFile.exists()) {
                    val rawBitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (rawBitmap != null) {
                        val scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, logoWidth.toInt(), logoHeight.toInt(), true)
                        canvas.drawBitmap(scaledBitmap, MARGIN_LEFT, currentY, paint)
                        logoDrawn = true
                    }
                }
            } catch (_: Exception) {
                logoDrawn = false
            }
        }

        if (!logoDrawn) {
            paint.color = primaryDark
            val rectBadge = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + logoWidth, currentY + logoHeight)
            canvas.drawRoundRect(rectBadge, 8f, 8f, paint)
            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            val initial = if (company.tradeName.isNotBlank()) company.tradeName.trim().take(2).uppercase() else "OE"
            canvas.drawText(initial, MARGIN_LEFT + (logoWidth / 2f), currentY + 36f, paint)
        }

        // Company Text
        val compLeft = MARGIN_LEFT + logoWidth + 14f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        paint.color = textPrimary
        val displayName = if (company.tradeName.isNotBlank()) company.tradeName else "Orçamento Express"
        canvas.drawText(displayName, compLeft, currentY + 14f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        paint.color = textSecondary

        var subY = currentY + 26f
        if (company.document.isNotBlank()) {
            canvas.drawText("CNPJ/CPF: ${Formatters.formatDocument(company.document)}", compLeft, subY, paint)
            subY += 10f
        }
        val contacts = listOfNotNull(
            company.phone.takeIf { it.isNotBlank() }?.let { "Tel: ${Formatters.formatPhone(it)}" },
            company.email.takeIf { it.isNotBlank() }
        ).joinToString(" | ")
        if (contacts.isNotBlank()) {
            canvas.drawText(contacts, compLeft, subY, paint)
            subY += 10f
        }
        if (company.address.isNotBlank()) {
            val addr = if (company.address.length > 50) company.address.take(48) + "..." else company.address
            canvas.drawText(addr, compLeft, subY, paint)
        }

        // Header Right: Title, Number, Dates
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        paint.color = primaryEmerald
        canvas.drawText("ORÇAMENTO COMERCIAL", MARGIN_RIGHT, currentY + 14f, paint)

        paint.textSize = 12f
        paint.color = primaryDark
        canvas.drawText(quote.quoteNumber, MARGIN_RIGHT, currentY + 30f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        paint.color = textSecondary
        canvas.drawText("Emissão: ${Formatters.formatDate(quote.issueDate)}", MARGIN_RIGHT, currentY + 43f, paint)
        canvas.drawText("Válido até: ${Formatters.formatDate(quote.validUntil)}", MARGIN_RIGHT, currentY + 54f, paint)

        // Separator
        val sepY = 98f
        paint.color = borderLight
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN_LEFT, sepY, MARGIN_RIGHT, sepY, paint)

        // Client Details Box
        val clientBoxTop = sepY + 10f
        val clientBoxHeight = 65f
        paint.style = Paint.Style.FILL
        paint.color = softCardBg
        val clientCardRect = RectF(MARGIN_LEFT, clientBoxTop, MARGIN_RIGHT, clientBoxTop + clientBoxHeight)
        canvas.drawRoundRect(clientCardRect, 6f, 6f, paint)

        paint.color = borderLight
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(clientCardRect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        paint.color = primaryDark
        canvas.drawText("DADOS DO CLIENTE", MARGIN_LEFT + 12f, clientBoxTop + 16f, paint)

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = textPrimary

        val cName = if (quote.clientName.isNotBlank()) quote.clientName else "Cliente não informado"
        canvas.drawText("Nome: $cName", MARGIN_LEFT + 12f, clientBoxTop + 30f, paint)

        val docStr = if (quote.clientDocument.isNotBlank()) Formatters.formatDocument(quote.clientDocument) else "-"
        canvas.drawText("CPF/CNPJ: $docStr", MARGIN_LEFT + 12f, clientBoxTop + 43f, paint)

        val cPhone = if (quote.clientPhone.isNotBlank()) Formatters.formatPhone(quote.clientPhone) else "-"
        canvas.drawText("Telefone: $cPhone", 310f, clientBoxTop + 30f, paint)

        val cEmail = if (quote.clientEmail.isNotBlank()) quote.clientEmail else "-"
        canvas.drawText("Email: $cEmail", 310f, clientBoxTop + 43f, paint)

        val cAddr = if (quote.clientAddress.isNotBlank()) quote.clientAddress else "-"
        val cAddrTrunc = if (cAddr.length > 70) cAddr.take(68) + "..." else cAddr
        canvas.drawText("Endereço: $cAddrTrunc", MARGIN_LEFT + 12f, clientBoxTop + 56f, paint)

        return clientBoxTop + clientBoxHeight + 14f
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        paint: Paint,
        quote: com.example.data.entity.QuoteEntity
    ): Float {
        // Top accent
        paint.color = primaryDark
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        paint.color = primaryDark
        canvas.drawText("ORÇAMENTO COMERCIAL — ${quote.quoteNumber}", MARGIN_LEFT, 26f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        paint.color = textSecondary
        val cName = if (quote.clientName.isNotBlank()) "Cliente: ${quote.clientName}" else ""
        canvas.drawText(cName, MARGIN_RIGHT, 26f, paint)

        // Separator line
        paint.color = borderLight
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN_LEFT, 34f, MARGIN_RIGHT, 34f, paint)

        return 42f
    }

    private fun drawTableHeader(canvas: Canvas, paint: Paint, y: Float) {
        val tableHeight = 22f

        paint.style = Paint.Style.FILL
        paint.color = primaryDark
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + tableHeight, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("#", COL_ITEM, y + 14f, paint)
        canvas.drawText("DESCRIÇÃO / SERVIÇO", COL_DESC, y + 14f, paint)
        canvas.drawText("TIPO", COL_TYPE, y + 14f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("QTD", COL_QTD, y + 14f, paint)
        canvas.drawText("UN", COL_UNIT, y + 14f, paint)
        canvas.drawText("UNITÁRIO", COL_PRICE, y + 14f, paint)
        canvas.drawText("TOTAL", COL_TOTAL, y + 14f, paint)
    }

    private fun drawTableRow(
        canvas: Canvas,
        paint: Paint,
        mItem: MeasuredItem,
        y: Float
    ) {
        val item = mItem.item
        val isEven = mItem.index % 2 == 0

        paint.style = Paint.Style.FILL
        paint.color = if (isEven) Color.WHITE else zebraLight
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + mItem.rowHeight, paint)

        paint.color = borderLight
        paint.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN_LEFT, y + mItem.rowHeight, MARGIN_RIGHT, y + mItem.rowHeight, paint)

        paint.style = Paint.Style.FILL
        paint.color = textPrimary
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Item #
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${mItem.index + 1}", COL_ITEM, y + 14f, paint)

        // Multi-line Description
        var lineY = y + 14f
        for (line in mItem.lines) {
            canvas.drawText(line, COL_DESC, lineY, paint)
            lineY += 11f
        }

        // Type
        val typeStr = if (item.type == "SERVICE") "Serviço" else "Produto"
        canvas.drawText(typeStr, COL_TYPE, y + 14f, paint)

        // Qtd
        paint.textAlign = Paint.Align.RIGHT
        val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.forLanguageTag("pt-BR"), "%.2f", item.quantity)
        canvas.drawText(qtyStr, COL_QTD, y + 14f, paint)

        // Unit
        canvas.drawText(item.unit, COL_UNIT, y + 14f, paint)

        // Price
        canvas.drawText(Formatters.formatCurrency(item.unitPrice), COL_PRICE, y + 14f, paint)

        // Total
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(Formatters.formatCurrency(item.totalPrice), COL_TOTAL, y + 14f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun drawSummaryAndFooterContent(
        canvas: Canvas,
        paint: Paint,
        quote: com.example.data.entity.QuoteEntity,
        company: CompanyProfileEntity,
        startY: Float
    ) {
        var currentY = startY + 10f

        // TOTALS BOX (Right Aligned)
        val totalsLeft = 320f
        val totalsRight = MARGIN_RIGHT

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9f
        paint.color = textSecondary
        canvas.drawText("Subtotal dos Itens:", totalsLeft, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = textPrimary
        canvas.drawText(Formatters.formatCurrency(quote.subtotal), totalsRight, currentY + 12f, paint)

        currentY += 16f
        if (quote.discountCalculated > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.rgb(197, 48, 48)
            val descLabel = if (quote.discountType == "PERCENTAGE") {
                "Desconto (${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", quote.discountValue)}%):"
            } else {
                "Desconto:"
            }
            canvas.drawText(descLabel, totalsLeft, currentY + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("- " + Formatters.formatCurrency(quote.discountCalculated), totalsRight, currentY + 12f, paint)
            currentY += 16f
        }

        if (quote.shippingFee > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = textSecondary
            canvas.drawText("Frete / Deslocamento:", totalsLeft, currentY + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = textPrimary
            canvas.drawText("+ " + Formatters.formatCurrency(quote.shippingFee), totalsRight, currentY + 12f, paint)
            currentY += 16f
        }

        // Grand Total Highlight Box
        val totalBoxTop = currentY + 4f
        val totalBoxHeight = 26f
        paint.style = Paint.Style.FILL
        paint.color = primaryDark
        val totalBoxRect = RectF(totalsLeft - 10f, totalBoxTop, totalsRight, totalBoxTop + totalBoxHeight)
        canvas.drawRoundRect(totalBoxRect, 4f, 4f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10.5f
        canvas.drawText("TOTAL GERAL", totalsLeft, totalBoxTop + 17f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 12.5f
        canvas.drawText(Formatters.formatCurrency(quote.total), totalsRight - 8f, totalBoxTop + 18f, paint)

        currentY = totalBoxTop + totalBoxHeight + 16f

        // PAYMENT TERMS & PIX BOX
        paint.style = Paint.Style.FILL
        paint.color = softCardBg
        val payBoxRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + 58f)
        canvas.drawRoundRect(payBoxRect, 6f, 6f, paint)

        paint.color = borderLight
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(payBoxRect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        paint.color = primaryDark
        canvas.drawText("CONDIÇÕES DE PAGAMENTO", MARGIN_LEFT + 12f, currentY + 15f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        paint.color = textPrimary

        val payMethod = if (quote.paymentMethod.isNotBlank()) "Forma: ${quote.paymentMethod}" else "Forma: A combinar"
        val payTerms = if (quote.paymentTerms.isNotBlank()) "Condições: ${quote.paymentTerms}" else ""
        val payLine = if (payTerms.isNotBlank()) "$payMethod  |  $payTerms" else payMethod
        canvas.drawText(payLine, MARGIN_LEFT + 12f, currentY + 28f, paint)

        if (company.pixKey.isNotBlank()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = primaryEmerald
            canvas.drawText("Chave PIX (${company.pixKeyType}): ${company.pixKey}", MARGIN_LEFT + 12f, currentY + 40f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = textPrimary
        }

        if (quote.notes.isNotBlank()) {
            val noteDisplay = if (quote.notes.length > 95) quote.notes.take(90) + "..." else quote.notes
            canvas.drawText("Obs: $noteDisplay", MARGIN_LEFT + 12f, currentY + 51f, paint)
        }

        currentY += 70f

        // SIGNATURES
        val sigY = currentY + 30f
        paint.color = textSecondary
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE

        canvas.drawLine(50f, sigY, 230f, sigY, paint)
        canvas.drawLine(365f, sigY, 545f, sigY, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val repName = if (company.tradeName.isNotBlank()) company.tradeName else "Responsável"
        canvas.drawText(repName, 140f, sigY + 12f, paint)
        canvas.drawText("Responsável / Emissor", 140f, sigY + 21f, paint)

        val clientName = if (quote.clientName.isNotBlank()) quote.clientName else "Cliente"
        canvas.drawText(clientName, 455f, sigY + 12f, paint)
        canvas.drawText("Aceite do Cliente (Assinatura)", 455f, sigY + 21f, paint)
    }

    private fun drawPageFooter(canvas: Canvas, paint: Paint, pageNum: Int, totalPages: Int) {
        paint.color = textSecondary
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        paint.textAlign = Paint.Align.LEFT
        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date())
        canvas.drawText("Orçamento Express • Documento comercial não fiscal • Emitido em $nowStr", MARGIN_LEFT, 828f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Página $pageNum de $totalPages", MARGIN_RIGHT, 828f, paint)
    }

    private fun breakTextIntoLines(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Single long word
                    lines.add(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    fun getFileUri(context: Context, file: File): android.net.Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
