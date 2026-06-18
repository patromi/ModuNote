package com.example.modunote.utils

import android.content.Context
import com.example.modunote.data.local.Block
import com.example.modunote.data.local.BlockType


fun shareNote(context: Context, noteTitle: String, blocks: List<Block>) {
    val text = StringBuilder()
    text.append("# ").append(noteTitle).append("\n\n")
    var bulletIdx = 1
    blocks.forEachIndexed { idx, block ->
        when (block.type) {
            BlockType.H1 -> text.append("# ").append(block.text).append("\n")
            BlockType.H2 -> text.append("## ").append(block.text).append("\n")
            BlockType.H3 -> text.append("### ").append(block.text).append("\n")
            BlockType.BULLET -> text.append("- ").append(block.text).append("\n")
            BlockType.NUMBERED -> {
                text.append("${bulletIdx}. ").append(block.text).append("\n")
                val nextIsNumbered = blocks.getOrNull(idx + 1)?.type == BlockType.NUMBERED
                if (nextIsNumbered) {
                    bulletIdx++
                } else {
                    bulletIdx = 1
                }
            }
            BlockType.TODO -> {
                val check = if (block.checked) "[x]" else "[ ]"
                text.append(check).append(" ").append(block.text).append("\n")
            }
            BlockType.QUOTE -> text.append("> ").append(block.text).append("\n")
            BlockType.CODE -> text.append("```\n").append(block.text).append("\n```\n")
            BlockType.DIVIDER -> text.append("---\n")
            BlockType.MAP -> {
                val addr = block.address ?: "${block.latitude}, ${block.longitude}"
                text.append("Mapa: ").append(addr).append("\n")
            }
            BlockType.LINK -> {
                val label = block.text.ifBlank { block.url }
                text.append(" ").append(label).append(" (").append(block.url).append(")\n")
            }
            else -> text.append(block.text).append("\n")
        }
    }

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, noteTitle)
        putExtra(android.content.Intent.EXTRA_TEXT, text.toString())
    }
    val chooser = android.content.Intent.createChooser(intent, "Udostępnij notatkę za pomocą…")
    context.startActivity(chooser)
}




