package com.masabi.kotlin

import kotlin.Any
import kotlin.String
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.reflect.KParameter

class NullableAndMandatoryMixed_Builder {
    private var nullableString: String? = null

    private var nonNullableString: String? = null

    val values: MutableMap<String, Any?> = mutableMapOf()

    fun withNullableString(nullableString: String?): NullableAndMandatoryMixed_Builder {
        this.nullableString = nullableString
        this.values["nullableString"] = nullableString
        return this
    }

    fun withNonNullableString(nonNullableString: String?): NullableAndMandatoryMixed_Builder {
        this.nonNullableString = nonNullableString
        this.values["nonNullableString"] = nonNullableString
        return this
    }

    fun build(): NullableAndMandatoryMixed {
        val constructor = ::NullableAndMandatoryMixed
        val parametersByName = constructor.parameters.groupBy { it.name }.mapValues { it.value.first() }
        fillInMissingNullables(parametersByName)
        verifyNonNullArgumentsArePresent(parametersByName)
        verifyMandatoryArgumentsArePresent(parametersByName)
        return constructor.callBy(mapOf(*values.map { parametersByName.getValue(it.key) to values.get(it.key) }.toTypedArray()))
    }

    private fun verifyNonNullArgumentsArePresent(parametersByName: Map<String?, KParameter>) {
        parametersByName
                    .filter { !it.value.type.isMarkedNullable }
                    .filter { !it.value.isOptional }
                    .forEach { if (values.get(it.key) == null) throw IllegalStateException("'${it.key}' cannot be null") }
    }

    private fun verifyMandatoryArgumentsArePresent(parametersByName: Map<String?, KParameter>) {
        parametersByName
                   .filter { !it.value.isOptional }
                   .forEach { if (!values.containsKey(it.key)) throw IllegalStateException("'${it.key}' has no default value, you must set one") }
    }

    private fun fillInMissingNullables(parametersByName: Map<String?, KParameter>) {
        parametersByName
                    .filter { it.value.type.isMarkedNullable }
                    .filter { !it.value.isOptional }
                    .forEach { values[it.key!!] = null }
    }
}
