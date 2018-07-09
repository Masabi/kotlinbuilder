package com.masabi.kotlin

import kotlin.String
import kotlin.reflect.KParameter

class Param1NullableString_Builder_Desired {
    private val setValues: MutableMap<String, Any?> = mutableMapOf()

    fun withNullableString(nullableString: String?): Param1NullableString_Builder_Desired {
        this.setValues.set("nullableString", nullableString); return this}

//    fun withNonNullableString(nullableString: String?): Param1NullableString_Builder_Desired {
//        this.nullableString = nullableString; return this}

    fun build(): NullableAndMandatoryMixed {
        val constructor = ::NullableAndMandatoryMixed

        // the names in this class and the names in the constructor should be a direct match as this was generated
        val parametersByName = constructor.parameters.groupBy { it.name }.mapValues { it.value.first() }
        verifyNonNullArgumentsArePresent(parametersByName)
        // constructor.parameters.get(1).type.isMarkedNullable


        return constructor.callBy(mapOf(
                constructor.parameters.get(0) to nullableString,
                constructor.parameters.get(1) to nonNullableString)
        )
    }

    private fun verifyNonNullArgumentsArePresent(parametersByName: Map<String?, KParameter>) {
        val nonNullableParameters = parametersByName
            .filter { !it.value.type.isMarkedNullable }
        if (nonNullableString == null && nonNullableParameters.containsKey("nonNullableString")) throw IllegalStateException("'nonNullableString' cannot be null")
    }
}
