package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Quotes : Screen("quotes")
    object Clients : Screen("clients")
    object Catalog : Screen("catalog")
    object Company : Screen("company")
    object QuoteEditor : Screen("quote_editor?quoteId={quoteId}") {
        fun createRoute(quoteId: Long? = null): String {
            return if (quoteId != null && quoteId > 0) "quote_editor?quoteId=$quoteId" else "quote_editor"
        }
    }
    object QuoteDetail : Screen("quote_detail/{quoteId}") {
        fun createRoute(quoteId: Long): String = "quote_detail/$quoteId"
    }
}
