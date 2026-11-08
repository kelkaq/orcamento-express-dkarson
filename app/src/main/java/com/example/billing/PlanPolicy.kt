package com.example.billing

import java.util.Calendar

/** O plano Gratuito permite cinco NOVOS orçamentos por mês civil local. */
object PlanPolicy {
    const val FREE_QUOTES_PER_MONTH = 5
    const val PRODUCT_ID = "orcamento_express_pro_mensal"
    const val OFFLINE_PRODUCT_ID = "orcamento_express_pro_offline"
    const val BASE_PLAN_ID = "mensal"

    fun currentMonthBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        return start to calendar.timeInMillis
    }

    fun remainingNewQuotes(existingDates: List<Long>, now: Long = System.currentTimeMillis()): Int {
        val (start, end) = currentMonthBounds(now)
        return (FREE_QUOTES_PER_MONTH - existingDates.count { it >= start && it < end })
            .coerceAtLeast(0)
    }
}

class FreePlanLimitException : IllegalStateException(
    "Você já criou 5 orçamentos neste mês no plano Gratuito. " +
        "Seus orçamentos continuam disponíveis. Assine o Pro para criar sem limite."
)

class ProRequiredException(feature: String) :
    IllegalStateException("$feature é um recurso Pro. Conheça o plano para continuar.")
