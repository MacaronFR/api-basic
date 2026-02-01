package fr.imacaron.api_basic.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.LazySizedCollection
import kotlin.collections.get
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

interface CustomMapper<T> {
	val custom: Map<KProperty<Any?>, KProperty<Any?>>
}

class EntityToTypeDelegate<E: Entity<*>, T: Any>(private val entity: E, private val type: KClass<T>) {
	operator fun getValue(thisRef: Nothing?, property: KProperty<*>): T {
		if(!type.isData) {
			throw IllegalArgumentException("Only data classes can be mapped from entities")
		}
		return entityToType(entity, type)
	}
}

inline fun <E: Entity<*>, reified T: Any>mapEntity(entity: E): EntityToTypeDelegate<E, T> {
	return EntityToTypeDelegate(entity, T::class)
}

class EntitiesToTypeDelegate<E: Entity<*>, T: Any>(private val entities: List<E>, private val type: KClass<T>) {
	operator fun getValue(thisRef: Nothing?, property: KProperty<*>): List<T> {
		if(!type.isData) {
			throw IllegalArgumentException("Only data classes can be mapped from entities")
		}
		return entities.map { entity -> entityToType(entity, type) }
	}
}

inline fun <E: Entity<*>, reified T: Any>mapEntities(entities: List<E>): EntitiesToTypeDelegate<E, T> {
	return EntitiesToTypeDelegate(entities, T::class)
}

private fun <E: Entity<*>, T: Any>entityToType(entity: E, type: KClass<T>): T {
	if (type.primaryConstructor == null) {
		throw IllegalArgumentException("Primary constructor is missing from entity ${entity::class.simpleName}")
	}
	val args = MutableList<Any?>(type.primaryConstructor!!.parameters.size) { null }
	type.primaryConstructor?.parameters?.forEach {
		val prop = entity::class.declaredMemberProperties.find { m ->
			m.name == it.name
		}
		var value = prop?.call(entity) ?: run {
			if(type.companionObjectInstance is CustomMapper<*>) {
				val p = type.declaredMemberProperties.find { p -> p.name == it.name }!! as KProperty<*>
				val eP = (type.companionObjectInstance as CustomMapper<*>).custom[p]
				return@run eP?.call(entity)
			} else {
				null
			}
		}
		if (value == null && !it.isOptional) {
			throw IllegalArgumentException("Property ${it.name} is null in entity ${entity::class.simpleName}")
		}
		if (value != null && value::class != it.type) {
			if (value is Entity<*>) {
				val mappedEntity by EntityToTypeDelegate(value, it.type.classifier as KClass<*>)
				value = mappedEntity
			} else if(value is EntityID<*>) {
				value = value.value
			} else if(value is LazySizedCollection<*>) {
				value = value.toList()
				if(value.firstOrNull() is Entity<*>) {
					val mappedEntities by EntitiesToTypeDelegate(value as List<Entity<*>>, it.type.arguments[0].type!!.classifier as KClass<*>)
					value = mappedEntities
				}
			}
		}
		args[it.index] = value
	}
	return type.primaryConstructor!!.call(*args.toTypedArray())
}