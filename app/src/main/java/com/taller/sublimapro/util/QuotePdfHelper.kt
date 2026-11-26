package com.taller.sublimapro.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.taller.sublimapro.domain.CostBreakdown
import com.taller.sublimapro.domain.CostInput
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object QuotePdfHelper {

    fun generateAndSharePdf(
        context: Context,
        workshopName: String,
        input: CostInput,
        breakdown: CostBreakdown
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1565C0")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1.5f
        }

        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val cur = breakdown.currencySymbol
        val productLabel = input.productName.ifBlank { "Producto personalizado (${input.technique.label})" }

        var y = 60f
        canvas.drawText(workshopName, 50f, y, titlePaint)
        y += 25f
        canvas.drawText("PRESUPUESTO / COTIZACIÓN", 50f, y, headerPaint)
        canvas.drawText("Fecha: $dateStr", 380f, y, bodyPaint)
        y += 15f
        canvas.drawLine(50f, y, 545f, y, linePaint)

        y += 35f
        canvas.drawText("Detalle del Trabajo", 50f, y, headerPaint)
        y += 22f
        canvas.drawText("Producto: $productLabel", 50f, y, bodyPaint)
        y += 18f
        canvas.drawText("Técnica: ${input.technique.label} | Medidas: ${fmt(input.widthCm)} x ${fmt(input.heightCm)} cm", 50f, y, bodyPaint)
        y += 18f
        canvas.drawText("Cantidad: ${input.quantity} unidad(es)", 50f, y, bodyPaint)

        y += 30f
        canvas.drawLine(50f, y, 545f, y, linePaint)
        y += 30f
        canvas.drawText("Precio Unitario: $${fmt(breakdown.suggestedUnitPriceUsd)} USD  ($cur ${fmt(breakdown.suggestedUnitPriceLocal)})", 50f, y, headerPaint)
        y += 28f
        canvas.drawText("TOTAL A PAGAR: $${fmt(breakdown.totalOrderPriceUsd)} USD", 50f, y, titlePaint)
        y += 24f
        canvas.drawText("Equivalente en moneda local: $cur ${fmt(breakdown.totalOrderPriceLocal)}", 50f, y, headerPaint)

        y += 35f
        canvas.drawLine(50f, y, 545f, y, linePaint)
        y += 25f
        val depositUsd = breakdown.totalOrderPriceUsd * 0.5
        canvas.drawText("Condiciones: 50% de abono ($${fmt(depositUsd)} USD) para iniciar producción.", 50f, y, bodyPaint)
        y += 18f
        canvas.drawText("Tasa referencial aplicada: 1 USD = ${fmt(breakdown.exchangeRate)} $cur (Sujeto a cambio).", 50f, y, bodyPaint)

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "cotizaciones").apply { mkdirs() }
        val file = File(dir, "Cotizacion_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Hola, te envío la cotización de $workshopName.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Enviar presupuesto PDF por..."))
    }

    fun shareTextSummary(
        context: Context,
        workshopName: String,
        input: CostInput,
        breakdown: CostBreakdown
    ) {
        val productLabel = input.productName.ifBlank { input.technique.label }
        val cur = breakdown.currencySymbol
        val text = """
            *$workshopName - Cotización*
            • Producto: $productLabel
            • Técnica: ${input.technique.label} (${fmt(input.widthCm)}x${fmt(input.heightCm)} cm)
            • Cantidad: ${input.quantity}
            • Precio Unitario: $${fmt(breakdown.suggestedUnitPriceUsd)} ($cur ${fmt(breakdown.suggestedUnitPriceLocal)})
            *TOTAL: $${fmt(breakdown.totalOrderPriceUsd)} USD* ($cur ${fmt(breakdown.totalOrderPriceLocal)})
            _(Abono 50% para iniciar producción)_
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar por WhatsApp / Mensaje"))
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
