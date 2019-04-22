package com.masabi.kotlinbuilder

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

import com.masabi.kotlinbuilder.JvmBuilderAnnotationProcessor.BuilderField
import com.masabi.kotlinbuilder.annotations.JvmBuilder
import com.squareup.kotlinpoet.*
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.deserialization.NameResolver
import me.eugeniomarletti.kotlin.metadata.shadow.serialization.deserialization.getName
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.WARNING
import kotlin.reflect.KParameter

private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("com.masabi.kotlinbuilder.annotations.JvmBuilder")
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION_NAME)
class JvmBuilderAnnotationProcessor : AbstractProcessor() {

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(JvmBuilder::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
                ?: run {
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
        val typeMetadata = targetClass.kotlinMetadata as KotlinClassMetadata
        val nameResolver = typeMetadata.data.nameResolver
        val classProto = typeMetadata.data.classProto

        val constructors = ElementFilter.constructorsIn(targetClass.enclosedElements)

        if (constructors.size == 0) {
            processingEnv.messager.printMessage(ERROR, "No primary constructor found")
        }


        /******/
        val typeResolver = TypeResolver()
        val propertyList = classProto.propertyList
        val element = constructors.first()
        val constructor = classProto.constructorList.single { it.isPrimary }
        for (parameter in constructor.valueParameterList) {
            val name = nameResolver.getString(parameter.name)
            val index = constructor.valueParameterList.indexOf(parameter)
            val hasDefault = parameter?.declaresDefaultValue ?: true
            val hasDefault = parameter?. ?: true

            log("things =====> \t\tname = $name, paramter = $parameter, hasDefault => $hasDefault")
        }

//        for (property in propertyList) {
//            val name = nameResolver.getString(property.name)
//            val type = typeResolver.resolve(property.returnType.asTypeName(
//                nameResolver, classProto::getTypeParameter, false))
//
//            log("name = $name, type = $type, property = $property, thing = ${constructor.parameters[name]}")
//        }

        /*******/

        val executableBits = ElementFilter.methodsIn(targetClass.enclosedElements)
        val setterPrefix = targetClass.setterPrefix
        val primaryConstructor = constructors.sortedBy { it.parameters.size }.last()
        val constructorParams = primaryConstructor.parameters
            return constructorParams.map { it.asBuilderField(setterPrefix) }
            .plus(executableBits
                .map { it.asBuilderField("blah", targetClass.kotlinMetadata as KotlinClassMetadata) }
                .filterNotNull()
            )
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
            val name = ClassName("kotlin.collections", "MutableMap").parameterizedBy(String::class.asTypeName(), Any::class.asTypeName().asNullable())
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
                    .addParameter(ParameterSpec.builder("parametersByName", Map::class.asClassName().parameterizedBy(String::class.asTypeName().asNullable(), KParameter::class.asTypeName())).build())
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
                    .addParameter(ParameterSpec.builder("parametersByName", Map::class.asClassName().parameterizedBy(String::class.asTypeName().asNullable(), KParameter::class.asTypeName())).build())
                    .addStatement("""
                        parametersByName
                            .filter { !values.containsKey(it.value.name) }
                            .filter { it.value.type.isMarkedNullable }
                            .filter { !it.value.isOptional }
                            .forEach { values[it.key!!] = null }
                    """.trimIndent())
                    .build()
        }

        private fun mandatoryArgCheckerSpec(): FunSpec {
            return FunSpec.builder("verifyMandatoryArgumentsArePresent")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter(ParameterSpec.builder("parametersByName", Map::class.asClassName().parameterizedBy(String::class.asTypeName().asNullable(), KParameter::class.asTypeName())).build())
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

    private fun ExecutableElement.asBuilderField(setterPrefix: String, kotlinMetadata: KotlinClassMetadata): BuilderField? {
//        log("Looking at $simpleName ---- ${kotlinMetadata.data.classProto.companionObjectName}")
        if (simpleName.startsWith("get")) {
//            log("\tWorking with $simpleName")
            val property = kotlinMetadata.data.getPropertyOrNull(this)
            val function = kotlinMetadata.data.classProto.propertyOrBuilderList
//            log("\tProperty is $property")
//            log("\tFunction is $function")
            property?.let {
                property.returnType?.nullable
                return BuilderField(
                    name = kotlinMetadata.data.nameResolver.getName(property.name).asString(),
                    type = asType().asTypeName(), //.corrected(),
                    setterPrefix = "${setterPrefix}_metadata"
                )
            }
        }
        return null
    }


    data class BuilderField(val name: String, val type: TypeName, val setterPrefix: String) {
        fun asFunSpec(builderClass: String): FunSpec {
            return FunSpec.builder(generateSetterName())
                    .returns(ClassName.bestGuess(builderClass))
                    .addParameter(name, type.asNullable())
                    .addStatement("""this.values["$name"] = $name""")
                    .addStatement("return this")
                    .build()
        }

        fun generateSetterName(): String {
            return if (setterPrefix.isNotEmpty()) {
                "$setterPrefix${name.capitalize()}"
            } else {
                name
            }
        }
    }
}

private fun TypeName.asNullable(): TypeName = copy(nullable = true)

private fun VariableElement.asBuilderField(setterPrefix: String): BuilderField {
    return BuilderField(
        name = simpleName.toString(),
        type = asType().asTypeName().corrected(),
        setterPrefix = setterPrefix
    )
}


private val Element.setterPrefix: String
    get() = getAnnotation(JvmBuilder::class.java).setterPrefix

private fun TypeName.corrected(): TypeName {
    return if (this.toString() == "java.lang.String") ClassName("kotlin", "String") else this
}

internal fun ProtoBuf.Type.asTypeName(
    nameResolver: NameResolver,
    getTypeParameter: (index: Int) -> ProtoBuf.TypeParameter,
    useAbbreviatedType: Boolean = true
): TypeName {

    val argumentList = when {
        useAbbreviatedType && hasAbbreviatedType() -> abbreviatedType.argumentList
        else -> argumentList
    }

    if (hasFlexibleUpperBound()) {
        return WildcardTypeName.producerOf(
            flexibleUpperBound.asTypeName(nameResolver, getTypeParameter, useAbbreviatedType))
            .copy(nullable = nullable)
    } else if (hasOuterType()) {
        return WildcardTypeName.consumerOf(
            outerType.asTypeName(nameResolver, getTypeParameter, useAbbreviatedType))
            .copy(nullable = nullable)
    }

    val realType = when {
        hasTypeParameter() -> return getTypeParameter(typeParameter)
            .asTypeName(nameResolver, getTypeParameter, useAbbreviatedType)
            .copy(nullable = nullable)
        hasTypeParameterName() -> typeParameterName
        useAbbreviatedType && hasAbbreviatedType() -> abbreviatedType.typeAliasName
        else -> className
    }

    var typeName: TypeName =
        ClassName.bestGuess(nameResolver.getString(realType)
            .replace("/", "."))

    if (argumentList.isNotEmpty()) {
        val remappedArgs: Array<TypeName> = argumentList.map { argumentType ->
            val nullableProjection = if (argumentType.hasProjection()) {
                argumentType.projection
            } else null
            if (argumentType.hasType()) {
                argumentType.type.asTypeName(nameResolver, getTypeParameter, useAbbreviatedType)
                    .let { argumentTypeName ->
                        nullableProjection?.let { projection ->
                            when (projection) {
                                ProtoBuf.Type.Argument.Projection.IN -> WildcardTypeName.consumerOf(argumentTypeName)
                                ProtoBuf.Type.Argument.Projection.OUT -> WildcardTypeName.producerOf(argumentTypeName)
                                ProtoBuf.Type.Argument.Projection.STAR -> STAR
                                ProtoBuf.Type.Argument.Projection.INV -> TODO("INV projection is unsupported")
                            }
                        } ?: argumentTypeName
                    }
            } else {
                STAR
            }
        }.toTypedArray()
        typeName = (typeName as ClassName).parameterizedBy(*remappedArgs)
    }

    return typeName.copy(nullable = nullable)
}

internal fun ProtoBuf.TypeParameter.asTypeName(
    nameResolver: NameResolver,
    getTypeParameter: (index: Int) -> ProtoBuf.TypeParameter,
    resolveAliases: Boolean = false
): TypeVariableName {
    val possibleBounds = upperBoundList.map {
        it.asTypeName(nameResolver, getTypeParameter, resolveAliases)
    }
    return if (possibleBounds.isEmpty()) {
        TypeVariableName(
            name = nameResolver.getString(name),
            variance = variance.asKModifier())
    } else {
        TypeVariableName(
            name = nameResolver.getString(name),
            bounds = *possibleBounds.toTypedArray(),
            variance = variance.asKModifier())
    }
}

internal fun ProtoBuf.TypeParameter.Variance.asKModifier(): KModifier? {
    return when (this) {
        ProtoBuf.TypeParameter.Variance.IN -> KModifier.IN
        ProtoBuf.TypeParameter.Variance.OUT -> KModifier.OUT
        ProtoBuf.TypeParameter.Variance.INV -> null
    }
}

