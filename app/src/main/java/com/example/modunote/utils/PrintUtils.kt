package com.example.modunote.utils

import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.modunote.data.local.Block
import com.example.modunote.data.local.BlockType


fun buildHtmlForPrint(noteTitle: String, blocks: List<Block>): String {
    val body = StringBuilder()
    for (block in blocks) {
        when (block.type) {
            BlockType.H1       -> body.append("<h1>${block.text.escapeHtml()}</h1>\n")
            BlockType.H2       -> body.append("<h2>${block.text.escapeHtml()}</h2>\n")
            BlockType.H3       -> body.append("<h3>${block.text.escapeHtml()}</h3>\n")
            BlockType.BULLET   -> body.append("<ul><li>${block.text.escapeHtml()}</li></ul>\n")
            BlockType.NUMBERED -> body.append("<ol><li>${block.text.escapeHtml()}</li></ol>\n")
            BlockType.TODO     -> {
                val check = if (block.checked) "&#9746;" else "&#9744;"
                body.append("<p>$check&nbsp;${block.text.escapeHtml()}</p>\n")
            }
            BlockType.QUOTE    -> body.append("<blockquote>${block.text.escapeHtml()}</blockquote>\n")
            BlockType.CODE     -> body.append("<pre><code>${block.text.escapeHtml()}</code></pre>\n")
            BlockType.DIVIDER  -> body.append("<hr/>\n")
            BlockType.MAP      -> body.append("<p><em>[Mapa: ${block.address ?: "${block.latitude}, ${block.longitude}"}]</em></p>\n")
            BlockType.LINK     -> body.append("<p> - <a href=\"${block.url.escapeHtml()}\">${block.text.ifBlank { block.url }.escapeHtml()}</a></p>\n")
            else               -> body.append("<p>${block.text.escapeHtml()}</p>\n")
        }
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <style>
                body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; margin: 32px; color: #191c20; line-height: 1.6; }
                h1 { font-size: 26px; margin-bottom: 8px; }
                h2 { font-size: 20px; margin-bottom: 6px; }
                h3 { font-size: 16px; margin-bottom: 4px; }
                blockquote { border-left: 4px solid #00639A; margin: 0; padding-left: 16px; color: #43474e; font-style: italic; }
                pre { background: #1a1a2e; color: #80cbc4; padding: 12px; border-radius: 6px; overflow-x: auto; }
                hr { border: none; border-top: 1px solid #e0e2ec; margin: 16px 0; }
                ul, ol { padding-left: 24px; }
            </style>
        </head>
        <body>
            <h1>${noteTitle.escapeHtml()}</h1>
            <hr/>
            $body
        </body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun printNote(context: Context, noteTitle: String, blocks: List<Block>) {
    val html = buildHtmlForPrint(noteTitle, blocks)
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "ModuNote â€“ $noteTitle"
            val adapter = view.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, adapter, null)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}






