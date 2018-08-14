import com.masabi.kotlin.*
import spock.lang.Specification

class BuildingSpec extends Specification {
    def "can build an object with 1 int argument"() {
        given:
            def direct = new Param1Int(99)
            def built = new Param1Int_Builder().param1(99).build()

        expect:
            direct == built
    }

    def "can build an object with 2 int arguments"() {
        given:
            def direct = new Param2Int(69, 42)
            def built = new Param2Int_Builder().param1(69).param2(42).build()

        expect:
            direct == built
    }

    def "can build an object with more int arguments"() {
        given:
            def direct = new Param9Int(1, 2, 3, 4, 5, 6, 7, 8, 9)
            def built = new Param9Int_Builder()
                .param1(1)
                .param2(2)
                .param3(3)
                .param4(4)
                .param5(5)
                .param6(6)
                .param7(7)
                .param8(8)
                .param9(9)
                .build()

        expect:
            direct == built
    }

    def "can build an object with a kotlin string"() {
        given:
            def direct = new Param1KotlinString("Hello Java World")
            def built = new Param1KotlinString_Builder()
                    .param1("Hello Java World")
                    .build()

        expect:
            direct == built
    }

    def "will error if trying to build and a value isn't provided for a non-null field"() {
        when:
            new Param1Int_Builder().param1(null).build()

        then:
            thrown(IllegalStateException)
    }

    def "will error if trying to build and single mandatory parameter hasn't been provided"() {
        when:
            new Param1Int_Builder().build()

        then:
            thrown(IllegalStateException)
    }

    def "will not error if nullable parameters aren't provided"() {
        expect:
            new NullableAndMandatoryMixed_Builder().nonNullableString("I'm here").build().nullableString == null
    }

    def "can mix nullable and non-nullable"() {
        expect:
            new NullableAndMandatoryMixed_Builder().nonNullableString("I'm here").build().nonNullableString == "I'm here"
    }

    def "uses defaults when provided"() {
        expect:
            new Param1Default_Builder().build().defaultString == "The D. Fault"
    }

    def "uses provided value instead of null when provided"() {
        expect:
            new Param1NullableString_Builder().nullableString("provided").build().nullableString == "provided"
    }

    def "can use a static builder on the data class"() {
        when:
            BuilderMethodProvided.builder().build()

        then:
            notThrown()
    }
}
