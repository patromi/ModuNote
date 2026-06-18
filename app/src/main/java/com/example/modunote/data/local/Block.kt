package com.example.modunote.data.local

import java.util.UUID

enum class BlockType {
    PARAGRAPH, H1, H2, H3, BULLET, NUMBERED, TODO, QUOTE, DIVIDER, CODE, MAP, LINK
}

data class Block(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val checked: Boolean = false,
    val language: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val url: String = ""
)
