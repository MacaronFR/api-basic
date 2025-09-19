package fr.imacaron.api_basic.search

import io.ktor.http.Parameters
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.char

fun Parameters.toSearchQuery(): SearchQuery = SearchQuery(
	search = get("search"),
	sort = getAll("sort[]")?.map {
		val args = it.split(".")
		Sort(
			args[0],
			SortOrder.entries.find { e -> e.value == args[1] } ?: throw IllegalArgumentException("Invalid sort order")
		)
	},
	filter = getAll("filter[]")?.map {
		val args = it.split(":")
		Filter(
			args.getOrElse(0) { throw IllegalArgumentException("Invalid filter field") },
			Operation.entries.find { e -> e.value == args[1] } ?: throw IllegalArgumentException("Invalid filter operation"),
			args.getOrNull(2)
		)
	},
	page = get("page")?.toInt(),
	pageSize = get("page_size")?.toInt()
)

val MariaDBDateFormat = LocalDate.Format {
	year(); char('-'); monthNumber(); char('-'); dayOfMonth()
}

fun String.toPascalCase(): String = this.replace("/_([A-Za-z0-9])/".toRegex()) { matchResult ->
	matchResult.groups[1]?.value?.uppercase() ?: ""
}