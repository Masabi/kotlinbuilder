![Build Status](https://codebuild.eu-west-1.amazonaws.com/badges?uuid=eyJlbmNyeXB0ZWREYXRhIjoic29QQnlzQktXdE9ORHArNEd6TlNHNXNNaFh4eGU4VzRmRU5INU9BWWFZNmFHZWZURGdmZDJ3MVVEVXlHeC9heDlmdml3RjJsTVozV25uNnZLVms4VUc4PSIsIml2UGFyYW1ldGVyU3BlYyI6InFDTG5iWjhUa21VMEF2OEkiLCJtYXRlcmlhbFNldFNlcmlhbCI6MX0%3D&branch=master)

# Kotlin Builder
JVM Builder for Kotlin Data Classes

## Introduction
This is an annotation processor for Kotlin data classes to generate a builder for use from other JVM languages.  This provides an alternative to an all-args constructor or generating telescopic constructors.

Usage is as simple as adding the `@JvmBuilder` annotation to the class of your choice, for example:
```
@JvmBuilder
data class Person(val name: String, val age: Int)
```

After applying kapt, a `Person_Builder` class will be generated allowing you to construct an object from other language, e.g. in Java this would be:
```
new Person_Builder()
    .withName("Henry")
    .withAge(15)
    .build()
```

### Nullability
The annotation processor will generate a builder that accepts nullable fields.  As you're calling this from another JVM language, this is ok as they typically don't have the notion of nullability.  During the build process, nullability will be checked and an `IllegalStateException` will be raised informing you of the mistake as illustrated n the following, taken from the examples:

Kotlin:
```
@JvmBuilder
data class Param1Int(val param1: Int)
```

Groovy Test:
```
    def "will error if trying to build and a value isn't provided for a non-null field"() {
        when:
            new Param1Int_Builder().withParam1(null).build()

        then:
            thrown(IllegalStateException)
    }
```

### Default Values
If defaults have been provided in the data class, these can be omitted from the builder chain and the original value will be provided, as shown in this example:

Kotlin:
```
@JvmBuilder
data class Param1Default(val defaultString: String = "The D. Fault")
```

Groovy Test:
```
    def "uses defaults when provided"() {
        expect:
            new Param1Default_Builder().build().defaultString == "The D. Fault"
    }
```

If a parameter does not have a default value and is not nullable, an `IllegalStateException` will be raised on build.

### Static builder() method
It is normal for the class the builder is for to generate a static `builder()` method to provide easy access to creating a builder instance.  As there is no officially supported way during annotation processing to modify existing classes this is not provided, but can be achieved with the following:
```
@JvmBuilder
data class BuilderMethodProvided(val param1: Int = 1, val param2: String = "Default") {
    companion object {
        @JvmStatic fun builder() = BuilderMethodProvided_Builder()
    }
}
```

Obviously this won't compile until you have run kapt to generate the builder.

## Getting Started
Kotlin Builder is available from maven central and must be added as a `kapt` and `compile` dependency to your project.  The `example/` directory has a fully working example to get you started.

The builder uses runtime reflection for nullability and default paremeters, so this must be provided on your runtime classpath, shown below:
```
apply plugin: 'kotlin'
apply plugin: 'kotlin-kapt'

def kotlinbuilderVersion = "1.0.0"

dependencies {
	compile(
        "com.masabi.kotlinbuilder:kotlinbuilder:$kotlinbuilderVersion",
        "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version",
        "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    )

    kapt(
        "com.masabi.kotlinbuilder:kotlinbuilder:$kotlinbuilderVersion"
    )
}
```

# Change Log
**Version 1.0.1 (23-07-2018)**

Fixed issue where nullable values weren't being overriden

**Version 1.0.0 (20-07-2018)**

Initial release
