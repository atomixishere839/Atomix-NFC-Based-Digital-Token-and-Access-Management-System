package com.atomix.app

enum class CardType(val displayName: String) {
    MIFARE_CLASSIC_1K("MIFARE Classic 1K"),
    MIFARE_CLASSIC_4K("MIFARE Classic 4K"), 
    MIFARE_ULTRALIGHT("MIFARE Ultralight"),
    UNSUPPORTED("Unsupported Card")
}