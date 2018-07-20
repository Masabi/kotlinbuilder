import com.masabi.kotlin.*
import spock.lang.PendingFeature
import spock.lang.Specification

class BuildingSpec extends Specification {
    def "can build an object with 1 int argument"() {
        given:
            def direct = new Param1Int(99)
            def built = new Param1Int_Builder().withParam1(99).build()

        expect:
            direct == built
    }

    def "can build an object with 2 int arguments"() {
        given:
            def direct = new Param2Int(69, 42)
            def built = new Param2Int_Builder().withParam1(69).withParam2(42).build()

        expect:
            direct == built
    }

    def "can build an object with more int arguments"() {
        given:
            def direct = new Param9Int(1, 2, 3, 4, 5, 6, 7, 8, 9)
            def built = new Param9Int_Builder()
                .withParam1(1)
                .withParam2(2)
                .withParam3(3)
                .withParam4(4)
                .withParam5(5)
                .withParam6(6)
                .withParam7(7)
                .withParam8(8)
                .withParam9(9)
                .build()

        expect:
            direct == built
    }

    def "can build an object with a kotlin string"() {
        given:
            def direct = new Param1KotlinString("Hello Java World")
            def built = new Param1KotlinString_Builder()
                    .withParam1("Hello Java World")
                    .build()

        expect:
            direct == built
    }

    def "will error if trying to build and a value isn't provided for a non-null field"() {
        when:
            new Param1Int_Builder().withParam1(null).build()

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
            new NullableAndMandatoryMixed_Builder().withNonNullableString("I'm here").build().nullableString == null
    }

    def "can mix nullable and non-nullable"() {
        expect:
            new NullableAndMandatoryMixed_Builder().withNonNullableString("I'm here").build().nonNullableString == "I'm here"
    }

    def "uses defaults when provided"() {
        expect:
            new Param1Default_Builder().build().defaultString == "The D. Fault"
    }

    def "uses provided value instead of null when provided"() {
        expect:
            new Param1NullableString_Builder().withNullableString("provided").build().nullableString == "provided"
    }

    def "can use a static builder on the data class"() {
        when:
            BuilderMethodProvided.builder().build()

        then:
            notThrown()
    }
}
