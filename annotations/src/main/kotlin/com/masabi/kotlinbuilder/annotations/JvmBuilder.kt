package com.masabi.kotlinbuilder.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class JvmBuilder(
    val setterPrefix: String = ""
)
