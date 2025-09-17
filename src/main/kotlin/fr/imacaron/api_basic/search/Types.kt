package fr.imacaron.api_basic.search

data class SearchQuery(
	val search: String? = null,
	val sort: List<Sort>? = null,
	val filter: List<Filter>? = null,
	val page: Int? = null,
	val pageSize: Int? = null
)

data class Sort(
	val column: String,
	val order: SortOrder
)

enum class SortOrder(val value: String) {
	ASC("asc"), DESC("desc")
}

data class Filter(
	val column: String,
	val operation: Operation,
	val value: String? = null
)

enum class Operation(val value: String) {
	EQ("eq"),
	NEQ("neq"),
	LIKE("like"),
	GREATER("gt"),
	GREATER_EQ("gte"),
	LOWER("lw"),
	LOWER_EQ("lwe"),
	IS_NULL("isn"),
	IS_NOT_NULL("isnn"),
	IN("in")
}