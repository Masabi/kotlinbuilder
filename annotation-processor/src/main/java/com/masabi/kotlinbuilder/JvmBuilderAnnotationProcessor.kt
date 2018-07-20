package com.masabi.kotlinbuilder

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.WARNING
import kotlin.reflect.KParameter

@Target(AnnotationTarget.CLASS)
annotation class JvmBuilder

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("com.masabi.kotlinbuilder.JvmBuilder")
@SupportedOptions(JvmBuilderAnnotationProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class JvmBuilderAnnotationProcessor : AbstractProcessor() {
    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(JvmBuilder::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
            processingEnv.messager.printMessage(ERROR, "Can't find the target directory for generated Kotlin files.")
            return false
        }

        annotatedElements.forEach {
            generateBuilder(kaptKotlinGeneratedDir, it)
        }

        return true
    }

    private fun generateBuilder(generationDir: String, annotatedElement: Element) {
        val className = annotatedElement.simpleName.toString()
        val pack = processingEnv.elementUtils.getPackageOf(annotatedElement).toString()
        val builderClassName = "${className}_Builder"

        FileSpec.builder(pack, builderClassName)
            .addType(builderSpec(builderClassName, annotatedElement))
            .build()
            .writeTo(File(generationDir, "$builderClassName.kt"))
    }

    private fun builderSpec(builderClassName: String, targetClass: Element): TypeSpec {
        val builderClass = BuilderClass(
                builderClass = builderClassName,
                targetClass = targetClass.asType(),
                properties = propertiesFrom(targetClass)
        )

        return TypeSpec.classBuilder(builderClassName)
            .addProperties(builderClass.propertySpecs())
            .addFunctions(builderClass.funSpecs())
            .build()
    }

    private fun propertiesFrom(targetClass: Element): List<BuilderField> {
        val constructors = ElementFilter.constructorsIn(targetClass.enclosedElements)

        if (constructors.size == 0) {
            processingEnv.messager.printMessage(ERROR, "No primary constructor found")
        }

        val primaryConstructor = constructors.sortedBy { it.parameters.size }.last()
        val constructorParams = primaryConstructor.parameters
        return constructorParams.map { BuilderField(it.simpleName.toString(), it.asType().asTypeName().corrected()) }
    }

    private fun log(any: Any) {
        processingEnv.messager.printMessage(WARNING, any.toString())
    }

    data class BuilderClass(
            val properties: List<BuilderField>,
            val builderClass: String,
            val targetClass: TypeMirror
    ) {
        fun propertySpecs(): Iterable<PropertySpec> {
            return listOf(mapBasedProperties())
        }

        private fun mapBasedProperties(): PropertySpec {
            val name = ParameterizedTypeName.get(ClassName("kotlin.collections", "MutableMap"), String::class.asTypeName(), Any::class.asTypeName().asNullable())
            return PropertySpec.builder("values", name)
                .addModifiers(KModifier.PRIVATE)
                .initializer("mutableMapOf()")
                .build()
        }

        fun funSpecs(): Iterable<FunSpec> {
            return properties.map { it.asFunSpec(builderClass) } + builderSpecs()
        }

        private fun builderSpecs(): Iterable<FunSpec> {
            return listOf(
                    builderSpec(),
                    nonNullArgCheckerSpec(),
                    mandatoryArgCheckerSpec(),
                    fillInMissingNullables()
            )
        }

        private fun nonNullArgCheckerSpec(): FunSpec {
            return FunSpec.builder("verifyNonNullArgumentsArePresent")
                .addModifiers(KModifier.PRIVATE)
                .addParameter(ParameterSpec.builder("parametersByName", ParameterizedTypeName.get(Map::class.asClassName(), String::class.asTypeName().asNullable(), KParameter::class.asTypeName())).build())
                .addStatement("""
                    parametersByName
                        .filter { !it.value.type.isMarkedNullable }
                        .filter { !it.value.isOptional }
                        .forEach { if (values.get(it.key) == null) throw IllegalStateException("'${'$'}{it.key}' cannot be null") }
                    """.trimIndent())
                .build()
        }

        private fun fillInMissingNullables(): FunSpec {
            return FunSpec.builder("fillInMissingNullables")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter(ParameterSpec.builder("parametersByName", ParameterizedTypeName.get(Map::class.asClassName(), String::class.asTypeName().asNullable(), KParameter::class.asTypeName())).build())
                    .addStatement("""
                        parametersByName
                            .filter { it.value.type.isMarkedNullable }
                            .filter { !it.value.isOptional }
                            .forEach { values[it.key!!] = null }
                    """.trimIndent())
                    .build()
        }

        private fun mandatoryArgCheckerSpec(): FunSpec {
            return FunSpec.builder("verifyMandatoryArgumentsArePresent")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter(ParameterSpec.builder("parametersByName", ParameterizedTypeName.get(Map::class.asClassName(), String::class.asTypeName().asNullable(), KParameter::class.asTypeName())).build())
                    .addStatement("""
                    parametersByName
                       .filter { !it.value.isOptional }
                       .forEach { if (!values.containsKey(it.key)) throw IllegalStateException("'${'$'}{it.key}' has no default value, you must set one") }
                    """.trimIndent())
                    .build()
        }


        private fun builderSpec(): FunSpec {
            return FunSpec.builder("build")
                    .returns(targetClass.asTypeName())
                    .addStatement("val constructor = ::%T", targetClass)
                    .addStatement("val parametersByName = constructor.parameters.groupBy { it.name }.mapValues { it.value.first() }")
                    .addStatement("fillInMissingNullables(parametersByName)")
                    .addStatement("verifyNonNullArgumentsArePresent(parametersByName)")
                    .addStatement("verifyMandatoryArgumentsArePresent(parametersByName)")
                    .addStatement("return constructor.callBy(mapOf(*values.map { parametersByName.getValue(it.key) to values.get(it.key) }.toTypedArray()))")
                    .build()
        }
    }

    data class BuilderField(val name: String, val type: TypeName) {
        fun asFunSpec(builderClass: String): FunSpec {
            return FunSpec.builder("with${name.capitalize()}")
                    .returns(ClassName.bestGuess(builderClass))
                    .addParameter(name, type.asNullable())
                    .addStatement("""this.values["$name"] = $name""")
                    .addStatement("return this")
                    .build()
        }
    }
}

private fun TypeName.corrected(): TypeName {
    return if (this.toString() == "java.lang.String") ClassName("kotlin", "String") else this
}
