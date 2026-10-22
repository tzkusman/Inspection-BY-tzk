package com.example.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.CategoryEntity
import com.example.data.model.InspectionEntity
import com.example.data.model.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    data class ExportOptions(
        val selectedCategoryIds: Set<Long>? = null, // null means all
        val includeComments: Boolean = true
    )

    suspend fun generateInspectionPdf(
        context: Context,
        inspection: InspectionEntity,
        categories: List<CategoryEntity>,
        photos: List<PhotoEntity>,
        options: ExportOptions = ExportOptions()
    ): File = withContext(Dispatchers.IO) {
        val filteredCategories = if (options.selectedCategoryIds != null) {
            categories.filter { it.id in options.selectedCategoryIds }
        } else {
            categories
        }

        val photosByCategory = photos.groupBy { it.categoryId }

        val doc = PdfDocument()
        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points
        val marginX = 28f
        val marginTop = 30f
        val marginBottom = 30f
        val contentWidth = pageWidth - (marginX * 2)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        // Paints
        val primaryPaint = Paint().apply {
            color = Color.rgb(26, 28, 30) // Dark Slate
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249) // Slate 100
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val categoryBgPaint = Paint().apply {
            color = Color.rgb(254, 243, 199) // Light Amber
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textBold = Paint().apply {
            color = Color.rgb(15, 23, 42)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            isAntiAlias = true
        }

        val textMedium = Paint().apply {
            color = Color.rgb(51, 65, 85)
            typeface = Typeface.DEFAULT
            textSize = 8.5f
            isAntiAlias = true
        }

        val textCaption = Paint().apply {
            color = Color.rgb(15, 23, 42)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8f
            isAntiAlias = true
        }

        val textComment = Paint().apply {
            color = Color.rgb(71, 85, 105)
            typeface = Typeface.DEFAULT
            textSize = 7f
            isAntiAlias = true
        }

        val textFooter = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 7.5f
            isAntiAlias = true
        }

        fun drawHeaderTable(c: Canvas, startY: Float): Float {
            var y = startY
            val tableHeight = 44f
            val tableRect = RectF(marginX, y, marginX + contentWidth, y + tableHeight)

            c.drawRect(tableRect, headerBgPaint)
            c.drawRect(tableRect, borderPaint)

            val col1Width = contentWidth * 0.35f
            val col2Width = contentWidth * 0.35f
            val col3Width = contentWidth * 0.30f

            // Dividing lines
            c.drawLine(marginX + col1Width, y, marginX + col1Width, y + tableHeight, borderPaint)
            c.drawLine(marginX + col1Width + col2Width, y, marginX + col1Width + col2Width, y + tableHeight, borderPaint)

            // Column 1: Company Name (bold) + Inspector
            c.drawText(inspection.companyName.ifEmpty { "Inspection Report" }, marginX + 8f, y + 16f, textBold)
            c.drawText("Inspector: ${inspection.inspectorName}", marginX + 8f, y + 32f, textMedium)

            // Column 2: Report Title + Buyer
            val titlePaint = Paint(textBold).apply { textSize = 9.5f }
            c.drawText(inspection.reportTitle, marginX + col1Width + 8f, y + 16f, titlePaint)
            val buyerText = if (inspection.buyer.isNotBlank()) "Buyer: ${inspection.buyer}" else ""
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateText = "Date: ${dateFormat.format(Date(inspection.createdAt))}"
            c.drawText("$buyerText  $dateText".trim(), marginX + col1Width + 8f, y + 32f, textMedium)

            // Column 3: Style No & Color
            c.drawText("STYLE: ${inspection.styleNo}", marginX + col1Width + col2Width + 8f, y + 16f, textBold)
            c.drawText("COLOR: ${inspection.color}", marginX + col1Width + col2Width + 8f, y + 32f, textBold)

            return y + tableHeight + 12f
        }

        fun drawFooter(c: Canvas, pNum: Int) {
            val footerY = pageHeight - marginBottom + 14f
            c.drawText("InspectCam Report • Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", marginX, footerY, textFooter)
            val pageStr = "Page $pNum"
            val pageStrWidth = textFooter.measureText(pageStr)
            c.drawText(pageStr, marginX + contentWidth - pageStrWidth, footerY, textFooter)
        }

        // Initialize Page 1
        var currentY = drawHeaderTable(canvas, marginTop)
        drawFooter(canvas, pageNumber)

        val colSpacing = 8f
        val numCols = 3
        val colWidth = (contentWidth - ((numCols - 1) * colSpacing)) / numCols
        val photoHeight = 125f
        val captionHeight = 13f
        val commentHeight = if (options.includeComments) 20f else 0f
        val cellTotalHeight = photoHeight + captionHeight + commentHeight + 8f

        for (category in filteredCategories) {
            val catPhotos = photosByCategory[category.id] ?: emptyList()
            if (catPhotos.isEmpty()) continue

            // Check if category header fits
            val categoryHeaderHeight = 22f
            if (currentY + categoryHeaderHeight + cellTotalHeight > pageHeight - marginBottom) {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                currentY = drawHeaderTable(canvas, marginTop)
                drawFooter(canvas, pageNumber)
            }

            // Draw Category Section Banner
            val bannerRect = RectF(marginX, currentY, marginX + contentWidth, currentY + categoryHeaderHeight)
            canvas.drawRoundRect(bannerRect, 4f, 4f, categoryBgPaint)
            val catTitle = "${category.name.uppercase(Locale.US)} (${catPhotos.size})"
            val catPaint = Paint(textBold).apply {
                color = Color.rgb(180, 83, 9) // Amber Dark
                textSize = 8.5f
            }
            canvas.drawText(catTitle, marginX + 8f, currentY + 14.5f, catPaint)
            currentY += categoryHeaderHeight + 8f

            // Draw photos in chunks of 3
            val photoRows = catPhotos.chunked(numCols)
            for (row in photoRows) {
                if (currentY + cellTotalHeight > pageHeight - marginBottom) {
                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawHeaderTable(canvas, marginTop)
                    drawFooter(canvas, pageNumber)

                    // Draw small category indicator on new page
                    canvas.drawText("${category.name} (continued)", marginX + 4f, currentY + 8f, textMedium)
                    currentY += 16f
                }

                // Render each cell in the row
                for ((colIndex, photo) in row.withIndex()) {
                    val cellX = marginX + (colIndex * (colWidth + colSpacing))
                    val cellRect = RectF(cellX, currentY, cellX + colWidth, currentY + photoHeight)

                    // Draw photo frame
                    canvas.drawRect(cellRect, borderPaint)

                    // Load & draw image
                    val imgFile = File(photo.thumbnailPath).takeIf { it.exists() }
                        ?: File(photo.imagePath).takeIf { it.exists() }

                    if (imgFile != null) {
                        try {
                            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                            if (bmp != null) {
                                val srcRect = Rect(0, 0, bmp.width, bmp.height)
                                canvas.drawBitmap(bmp, srcRect, cellRect, null)
                                bmp.recycle()
                            }
                        } catch (_: Exception) {}
                    }

                    // Caption (File Name)
                    val capY = currentY + photoHeight + 10f
                    val nameText = truncateText(photo.fileName, colWidth - 4f, textCaption)
                    canvas.drawText(nameText, cellX + 2f, capY, textCaption)

                    // Optional Comment & Barcode
                    if (options.includeComments) {
                        var extraText = photo.comment.orEmpty()
                        if (!photo.barcodeValue.isNullOrBlank()) {
                            extraText = if (extraText.isNotBlank()) "[$photo.barcodeValue] $extraText" else "[$photo.barcodeValue]"
                        }
                        if (extraText.isNotBlank()) {
                            val commentText = truncateText(extraText, colWidth - 4f, textComment)
                            canvas.drawText(commentText, cellX + 2f, capY + 11f, textComment)
                        }
                    }
                }

                currentY += cellTotalHeight
            }

            currentY += 10f // Space between categories
        }

        doc.finishPage(page)

        // Save PDF to cache dir
        val cleanStyle = inspection.styleNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val pdfFile = File(context.cacheDir, "Inspection_${cleanStyle}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(pdfFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        pdfFile
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }
}
