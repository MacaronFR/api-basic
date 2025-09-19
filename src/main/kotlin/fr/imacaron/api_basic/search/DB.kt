package fr.imacaron.api_basic.search

import fr.imacaron.api_basic.search.SortOrder as MySortOrder
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.AutoIncColumnType
import org.jetbrains.exposed.sql.ByteColumnType
import org.jetbrains.exposed.sql.CharColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnWithTransform
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.sql.GreaterEqOp
import org.jetbrains.exposed.sql.GreaterOp
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LessEqOp
import org.jetbrains.exposed.sql.LessOp
import org.jetbrains.exposed.sql.LikeEscapeOp
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.NeqOp
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ShortColumnType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinLocalDateColumnType
import org.jetbrains.exposed.sql.ops.SingleValueInListOp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.orWhere
import org.jetbrains.exposed.sql.stringParam

fun Query.search(searchQuery: SearchQuery, limit: Boolean = true): Query {
	val columns = this.targets.fold(listOf()) { acc: List<ExpressionWithColumnType<*>>, table: Table ->
		if(table is TableColumns) {
			acc + table.filterColumns
		} else {
			acc + table.columns
		}
	}
	val columnsMap = columns.associateBy {
		when(it) {
			is Column<*> -> if(it.table == targets.first()) it.name else "${it.table.tableName.toPascalCase()}.${it.name}"
			is ExpressionWithColumnTypeAlias<*> -> it.alias
			else -> ""
		}
	}
	if (columns.isEmpty()) {
		return this
	}
	var query = this
	searchQuery.search?.let { search ->
		var op = columns.first().like(search)
		columns.drop(1).forEach { column ->
			if (op == null) {
				op = column.like(search)
			} else {
				column.like(search)?.let { newOp -> op = op!! or newOp }
			}
		}
		op?.let { query = query.orWhere { it } }
	}
	searchQuery.filter?.let{ filters ->
		var filterOp: Op<Boolean>? = null
		filters.forEach { filter ->
			val op = columnsMap[filter.column]?.let { col ->
				val name = when(col) {
					is Column<*> -> col.name
					is ExpressionWithColumnTypeAlias<*> -> col.alias
					else -> ""
				}
				when(filter.operation) {
					Operation.EQ -> col.eq(filter.value ?: throw IllegalArgumentException("Value must be provided for EQ operation on filter column $name"))
					Operation.NEQ -> col.neq(filter.value ?: throw IllegalArgumentException("Value must be provided for NEQ operation on filter column $name"))
					Operation.LIKE -> col.like(filter.value ?: throw IllegalArgumentException("Value must be provided for LIKE operation on filter column $name"))
					Operation.LOWER -> col.lower(filter.value ?: throw IllegalArgumentException("Value must be provided for LOWER operation on filter column $name"))
					Operation.LOWER_EQ -> col.lowerEq(filter.value ?: throw IllegalArgumentException("Value must be provided for LOWER_EQ operation on filter column $name"))
					Operation.GREATER -> col.greater(filter.value ?: throw  IllegalArgumentException("Value must be provided for GREATER operation on filter column $name"))
					Operation.GREATER_EQ -> col.greaterEq(filter.value ?: throw IllegalArgumentException("Value must be provided for GREATER_EQ operation on filter column $name"))
					Operation.IS_NULL -> col.isNull()
					Operation.IS_NOT_NULL -> col.isNotNull()
					Operation.IN -> col.inList(filter.value ?: throw IllegalArgumentException("Value must be provided for IN operation on filter column $name"))
				}
			}
			if(filterOp == null) {
				filterOp = op
			} else {
				op?.let { filterOp = filterOp!! and it }
			}
		}
		filterOp?.let { query = query.andWhere { it } }
	}
	searchQuery.sort?.let { sorts ->
		sorts.forEach { sort ->
			columnsMap[sort.column]?.let {
				query = when(sort.order) {
					MySortOrder.ASC -> query.orderBy(it, SortOrder.ASC)
					MySortOrder.DESC -> query.orderBy(it, SortOrder.DESC)
				}
			}
		}
	}
	this.targets.first().columns.first { it.columnType is EntityIDColumnType<*> }.let { idCol ->
		query = query.orderBy(idCol, SortOrder.ASC)
	}
	if(limit) {
		searchQuery.pageSize?.let { pageSize ->
			query = query.limit(pageSize)
		}
		searchQuery.page?.let { page ->
			query = query.offset(page.toLong() * (searchQuery.pageSize ?: 10))
			query.limit ?: run {
				query = query.limit(10)
			}
		}
	}
	return query
}

private fun ExpressionWithColumnType<*>.like(t: String): Op<Boolean>? =
	when(actualColumnType) {
		is VarCharColumnType, is CharColumnType -> {
			val pattern = LikePattern("%${t.replace(" ", "%")}%")
			val col = when(this@like) {
				is Column<*> -> this@like
				is ExpressionWithColumnTypeAlias<*> -> this@like.delegate
				else -> throw IllegalArgumentException("Unsupported column type ${this.actualColumnType::class.simpleName}")
			}
			LikeEscapeOp(
				col,
				stringParam(pattern.pattern),
				true,
				pattern.escapeChar
			)
		}
		else -> this@like.eq(t )
	}

private fun ExpressionWithColumnType<*>.inList(t: String): Op<Boolean>? =
	t.split(",").mapNotNull { this.getValueTypedByColumn(it) }.let { value ->
		val col = when(this) {
			is Column<*> -> this
			is ExpressionWithColumnTypeAlias<*> -> this.delegate
			else -> throw IllegalArgumentException("Unsupported column type ${this.actualColumnType::class.simpleName}")
		}
		SingleValueInListOp(col, value)
	}

private fun ExpressionWithColumnType<*>.eq(t: String): Op<Boolean>? =
	this.getValueTypedByColumn(t)?.let { value ->
		val col = when(this) {
			is Column<*> -> this
			is ExpressionWithColumnTypeAlias<*> -> this.delegate
			else -> throw IllegalArgumentException("Unsupported column type ${this.actualColumnType::class.simpleName}")
		}
		EqOp(col, wrap(value))
	}

private fun ExpressionWithColumnType<*>.neq(t: String): Op<Boolean>? =
	this.getValueTypedByColumn(t)?.let { value ->
		val col = when(this) {
			is Column<*> -> this
			is ExpressionWithColumnTypeAlias<*> -> this.delegate
			else -> throw IllegalArgumentException("Unsupported column type ${this.actualColumnType::class.simpleName}")
		}
		NeqOp(col, wrap(value))
	}

private fun ExpressionWithColumnType<*>.numberDateOperation(t: String, op: (Expression<*>, Expression<*>) -> Op<Boolean>) =
	this.getValueTypedByColumn(t)?.let { value ->
		when(this.actualColumnType) {
			is ByteColumnType,
			is ShortColumnType,
			is IntegerColumnType,
			is LongColumnType,
			is DoubleColumnType,
			is KotlinLocalDateColumnType
				-> {
				val col = when (this) {
					is Column<*> -> this
					is ExpressionWithColumnTypeAlias<*> -> this.delegate
					else -> throw IllegalArgumentException("Unsupported column type ${this.actualColumnType::class.simpleName}")
				}
				op(col, wrap(value))
			}
			else -> null
		}
	}

private fun ExpressionWithColumnType<*>.lower(t: String): Op<Boolean>? = numberDateOperation(t, ::LessOp)

private fun ExpressionWithColumnType<*>.lowerEq(t: String): Op<Boolean>? = numberDateOperation(t, ::LessEqOp)

private fun ExpressionWithColumnType<*>.greater(t: String): Op<Boolean>? = numberDateOperation(t, ::GreaterOp)

private fun ExpressionWithColumnType<*>.greaterEq(t: String): Op<Boolean>? = numberDateOperation(t, ::GreaterEqOp)

@Suppress("UNCHECKED_CAST")
private fun ExpressionWithColumnType<*>.getValueTypedByColumn(value: String): Any? = when (this.actualColumnType) {
	is VarCharColumnType, is CharColumnType -> value
	is ByteColumnType -> value.toByteOrNull()
	is ShortColumnType -> value.toShortOrNull()
	is IntegerColumnType -> value.toIntOrNull()
	is LongColumnType -> value.toLongOrNull()
	is DoubleColumnType -> value.toDoubleOrNull()
	is KotlinLocalDateColumnType -> MariaDBDateFormat.parseOrNull(value)
	is ColumnWithTransform<*, *> -> {
		val c = (this as Column<Any>).defaultValueFun?.invoke()
		val columnWithTransform = this.columnType as ColumnWithTransform<Any, Any>
		if(c is Enum<*>) {
			c::class.java.enumConstants.find { it.name == value }
		} else {
			columnWithTransform.transformer.unwrap(value)
		}
	}
	else -> null
}?.let {
	if(this.columnType is EntityIDColumnType<*>) {
		when(this) {
			is Column<*> -> EntityID(it, table as IdTable<Any>)
			else -> it
		}
	} else {
		it
	}
}

private val ExpressionWithColumnType<*>.actualColumnType: IColumnType<out Any> get()  {
	var c: IColumnType<out Any> = columnType
	if (c is EntityIDColumnType<*>) {
		c = c.idColumn.columnType.let { type ->
			if (type is AutoIncColumnType) {
				type.delegate
			} else {
				type
			}
		}
	}
	return c
}

interface TableColumns {
	val filterColumns: List<ExpressionWithColumnType<*>>
}