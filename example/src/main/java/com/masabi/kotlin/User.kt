package com.masabi.kotlin

import org.kotlin.annotationProcessor.JvmBuilder

//@JvmBuilder

data class User constructor(
    val name: String = "Bobby De Fault",
    val age: Int
)