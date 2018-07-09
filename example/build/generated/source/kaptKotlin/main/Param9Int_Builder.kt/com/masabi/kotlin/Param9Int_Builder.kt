package com.masabi.kotlin

import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.reflect.KParameter

class Param9Int_Builder {
    private var param1: Int? = null

    private var param2: Int? = null

    private var param3: Int? = null

    private var param4: Int? = null

    private var param5: Int? = null

    private var param6: Int? = null

    private var param7: Int? = null

    private var param8: Int? = null

    private var param9: Int? = null

    val values: MutableMap<String, Any?> = mutableMapOf()

    fun withParam1(param1: Int?): Param9Int_Builder {
        this.param1 = param1
        this.values["param1"] = param1
        return this
    }

    fun withParam2(param2: Int?): Param9Int_Builder {
        this.param2 = param2
        this.values["param2"] = param2
        return this
    }

    fun withParam3(param3: Int?): Param9Int_Builder {
        this.param3 = param3
        this.values["param3"] = param3
        return this
    }

    fun withParam4(param4: Int?): Param9Int_Builder {
        this.param4 = param4
        this.values["param4"] = param4
        return this
    }

    fun withParam5(param5: Int?): Param9Int_Builder {
        this.param5 = param5
        this.values["param5"] = param5
        return this
    }

    fun withParam6(param6: Int?): Param9Int_Builder {
        this.param6 = param6
        this.values["param6"] = param6
        return this
    }

    fun withParam7(param7: Int?): Param9Int_Builder {
        this.param7 = param7
        this.values["param7"] = param7
        return this
    }

    fun withParam8(param8: Int?): Param9Int_Builder {
        this.param8 = param8
        this.values["param8"] = param8
        return this
    }

    fun withParam9(param9: Int?): Param9Int_Builder {
        this.param9 = param9
        this.values["param9"] = param9
        return this
    }

    fun build(): Param9Int {
        val constructor = ::Param9Int
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
