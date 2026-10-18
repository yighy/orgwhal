package io.github.yighy.orgwhal.core

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The guide notebook offered on first start: a template in the app's assets, whose examples get
 * dates around the day it is added ({{date:+2}}: in two days) and an ID of their own ({{id}}).
 */
object Guide {

    /** File name, without .org (made by the folder). */
    const val FILE_NAME = "orgwhal-guide"

    /** Its name up to 0.16.0, when the app was Narwhal. */
    const val OLD_FILE_NAME = "narwhal-guide"

    const val ASSET = "guide-en.org"

    private val DATE = Regex("""\{\{date:([+-]?\d+)\}\}""")

    /** [template] with its dates set from [today] (as Org writes them: 2026-10-12 Mon) and [id]. */
    fun fill(template: String, today: LocalDate, id: String): String =
        DATE.replace(template) { m ->
            val date = today.plusDays(m.groupValues[1].toLong())
            "$date ${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        }.replace("{{id}}", id)
}
