package id.global.asyncapi.spec.enums;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import id.global.common.annotations.amqp.ExchangeType;

public class ExchangeTypeTest {

    private static record ExchangeTypeTestDto(
            String typeUppercase,
            String typeLowercase,
            ExchangeType type) {
    }

    private static Stream<Arguments> exchangeTypes() {
        return Stream.of(
                arguments(new ExchangeTypeTestDto("DIRECT", "direct", ExchangeType.DIRECT)),
                arguments(new ExchangeTypeTestDto("TOPIC", "topic", ExchangeType.TOPIC)),
                arguments(new ExchangeTypeTestDto("FANOUT", "fanout", ExchangeType.FANOUT))
        );
    }

    @ParameterizedTest
    @MethodSource("exchangeTypes")
    public void exchangeTypeShouldConvertStringsToEnum(final ExchangeTypeTestDto testDto) {
        ExchangeType directUpperCase = ExchangeType.fromType(testDto.typeUppercase);
        ExchangeType directLowerCase = ExchangeType.fromType(testDto.typeLowercase);
        ExchangeType directFromType = ExchangeType.fromType(testDto.type.getType());

        assertThat(directUpperCase, is(testDto.type));
        assertThat(directLowerCase, is(testDto.type));
        assertThat(directFromType, is(testDto.type));
    }
}
