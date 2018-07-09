package org.kotlin.annotationProcessor

import com.squareup.kotlinpoet.*
import org.yanex.takenoko.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic.Kind.*
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.system.exitProcess

@Target(AnnotationTarget.CLASS)
annotation class JvmBuilder

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.kotlin.annotationProcessor.JvmBuilder")
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
        println("Processing: $className")
        val pack = processingEnv.elementUtils.getPackageOf(annotatedElement).toString()
        val builderClassName = "${className}_Builder"

        val file = FileSpec.builder(pack, builderClassName)
                    .addType(builderSpec(builderClassName, annotatedElement))
                    .build()
//
            val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
            file.writeTo(File(kaptKotlinGeneratedDir, "$builderClassName.kt"))

        processingEnv.messager.printMessage(WARNING, "Going to generate $pack -> $builderClassName")
    }

    private fun builderSpec(builderClassName: String, targetClass: Element): TypeSpec {
        val builderClass = BuilderClass(
                builderClass = builderClassName,
                targetClass = targetClass.asType(),
                properties = propertiesFrom(targetClass)
        )
        // generate an internal representation of what we want
        // add properties and functions here
        if (targetClass.simpleName.contains("Null")) {
            propertiesFrom(targetClass).forEach {
                log("**** ${it.name} is nullable ${it.type.nullable}")
            }
        }

        return TypeSpec.classBuilder(builderClassName)
            .addProperties(builderClass.propertySpecs())
            .addFunctions(builderClass.funSpecs())
            .build()
    }

    private fun propertiesFrom(targetClass: Element): List<BuilderField> {
        val constructors = ElementFilter.constructorsIn(targetClass.enclosedElements)

        // TODO find the primary constructor if we have overloads, ignore this for now
        if (constructors.size == 0) {
            processingEnv.messager.printMessage(ERROR, "No primary constructor found")
        }

        // TODO can we instantiate the shortest constructor and get the defaults from that version?
        // Maybe the real code could just do that and then use copyWith to fill in the values we know about?

        log("Found ${constructors.size} constructors")
        constructors.forEach {
            log("Constructor: ${it.parameters}")
        }
        val primaryConstructor = constructors.sortedBy { it.parameters.size }.last()
        val constructorParams = primaryConstructor.parameters
//        val constructorParams = primaryConstructor.enclosedElements.filter { it.kind == ElementKind.PARAMETER }
        log("CP ----- ${primaryConstructor.enclosedElements.map { it.toString() }}")
        log("CPx ----- ${primaryConstructor.parameters}")
        log("CPx ----- ${primaryConstructor.parameters.get(0).enclosedElements}")

        constructorParams.forEach {
            log("param name is ${it.simpleName}")
            log("param javaClass is ${it.javaClass}")
            log("param type is ${it.asType().asTypeName()}")
        }
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
            return properties.map { it.asPropertySpec() } + mapBasedProperties()
        }

        private fun mapBasedProperties(): PropertySpec {
            /**
             *     private val setValues: MutableMap<String, Any?> = mutableMapOf()

             */

//            return PropertySpec.builder("values", ParameterizedTypeName.get(MutableMap::class::asClassName, String::class::asTypeName, Any::class.asTypeName().asNullable()))
            val name = ParameterizedTypeName.get(Map::class.asClassName(), String::class.asTypeName(), Any::class.asTypeName().asNullable())
            return PropertySpec.builder("values", name)
                .initializer("mutableMapOf()")
                .build()
        }

        fun funSpecs(): Iterable<FunSpec> {
            return properties.map { it.asFunSpec(builderClass) } + builderSpecs()
        }

        private fun builderSpecs(): Iterable<FunSpec> {
            return listOf(builderSpec(), nonNullArgCheckerSpec())
        }

        private fun nonNullArgCheckerSpec(): FunSpec {
            val builder = FunSpec.builder("verifyNonNullArgumentsArePresent")
                    .addParameter(ParameterSpec.builder("parametersByName", ParameterizedTypeName.get(Map::class, String::class, KParameter::class)).build())
                    .addCode("""
                        val nonNullableParameters = parametersByName
                            .filter { !it.value.type.isMarkedNullable }
                            .forEach { if (values.get(it.key) == null) throw IllegalStateException("'${'$'}it.key' cannot be null") }
                        """)
//            properties.forEach {
//                val block = CodeBlock.builder().addStatement("""
//                    if (${it.name} == null && nonNullableParameters.containsKey("${it.name}")) throw IllegalStateException("'${it.name}' cannot be null")
//                    """)
//                        .build()
//                builder.addCode(block)
//            }
            return builder.build()
        }

        private fun builderSpec(): FunSpec {
            val propertyAssignments = properties.map { "${it.name} = this.${it.name}!!" }.joinToString(", ")
            return FunSpec.builder("build")
                    .returns(targetClass.asTypeName())
                    .addStatement("return %T($propertyAssignments)", targetClass)
                    .build()
        }
    }

    data class BuilderField(val name: String, val type: TypeName) {
        fun asPropertySpec() =
            PropertySpec.builder(name, type.asNullable())
                .mutable(true)
                .addModifiers(KModifier.PRIVATE)
                .initializer("null")
                .build()

        fun asFunSpec(builderClass: String): FunSpec {
            return FunSpec.builder("with${name.capitalize()}")
                    .returns(ClassName.bestGuess(builderClass))
                    .addParameter(name, type.asNullable())
                    .addCode("this.${name} = ${name}; return this")
                    .build()
        }
    }

    private fun generateBuilder2(generatedDir: String, annotatedElement: Element) {
        val typeElement = annotatedElement.toTypeElementOrNull() ?: return
        val element = processingEnv.typeUtils.asElement(typeElement.asType())
        val packageName = processingEnv.elementUtils.getPackageOf(element)

        val generatedKtFile = kotlinFile(packageName = packageName.qualifiedName.toString()) {
            property("simpleClassName") {
                receiverType(typeElement.qualifiedName.toString())
                getterExpression("this::class.java.simpleName")
            }
        }

        // TODO JVM overloads means we end up with multiple constructors - I think we can just get
        // away with the longest one for a data class

        // TODO is "data" a modifier somewhere?
        val constructors = ElementFilter.constructorsIn(annotatedElement.enclosedElements)

        processingEnv.messager.printMessage(WARNING, "type = ${typeElement}")
        processingEnv.messager.printMessage(WARNING, "element = ${element}")
        processingEnv.messager.printMessage(WARNING, "package = ${packageName}")
        processingEnv.messager.printMessage(WARNING, "constructors = ${constructors}")

        // TODO figure out how to write the package name here
        File(generatedDir, "blahblah.kt").apply {
            parentFile.mkdirs()
            writeText(generatedKtFile.accept(PrettyPrinter(PrettyPrinterConfiguration())))
        }
    }

    fun Element.toTypeElementOrNull(): TypeElement? {
        if (this !is TypeElement) {
            processingEnv.messager.printMessage(ERROR, "Invalid element type, class expected", this)
            return null
        }

        return this
    }
}

private fun TypeName.corrected(): TypeName {
    return if (this.toString() == "java.lang.String") ClassName("kotlin", "String") else this
}
