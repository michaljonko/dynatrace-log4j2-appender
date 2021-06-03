package io.github.michaljonko.log4j.appender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.URL;
import java.util.stream.Stream;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.michaljonko.log4j.appender.DynatraceGenericLogIngestAppender;

class DynatraceGenericLogIngestAppenderBuilderTest {

	@ParameterizedTest
	@MethodSource("sourceForNullPointer")
	void throwNullPointerExceptionForNullParams(final Configuration configuration,
			final URL activeGateUrl,
			final String token,
			final String name,
			final String expectedMessage) {
		assertThatNullPointerException()
				.isThrownBy(() -> DynatraceGenericLogIngestAppender.createBuilder()
						.setConfiguration(configuration)
						.setActiveGateUrl(activeGateUrl)
						.setToken(token)
						.setName(name)
						.build())
				.withMessage(expectedMessage);
	}

	private static Stream<Arguments> sourceForNullPointer() throws Exception {
		return Stream.of(
				Arguments.of(
						given(mock(Configuration.class).getLoggerContext()).willReturn(mock(LoggerContext.class)).getMock(),
						new URL("http://localhost"),
						"token",
						"name",
						"strSubstitutor is null"
				),
				Arguments.of(
						given(mock(Configuration.class).getLoggerContext()).willReturn(mock(LoggerContext.class)).getMock(),
						new URL("http://localhost"),
						"token",
						null,
						"name is null"
				),
				Arguments.of(
						given(mock(Configuration.class).getLoggerContext()).willReturn(mock(LoggerContext.class)).getMock(),
						new URL("http://localhost"),
						null,
						null,
						"token is null"
				),
				Arguments.of(
						given(mock(Configuration.class).getLoggerContext()).willReturn(mock(LoggerContext.class)).getMock(),
						null,
						null,
						null,
						"activeGateUrl is null"
				),
				Arguments.of(
						mock(Configuration.class),
						null,
						null,
						null,
						"loggerContext is null"
				),
				Arguments.of(
						null,
						null,
						null,
						null,
						"configuration is null"
				)
		);
	}

	@Test
	void createAppender() throws Exception {
		final Configuration configuration = mock(Configuration.class);
		final LoggerContext loggerContext = mock(LoggerContext.class);
		final StrSubstitutor strSubstitutor = mock(StrSubstitutor.class);

		given(configuration.getLoggerContext())
				.willReturn(loggerContext);
		given(configuration.getStrSubstitutor())
				.willReturn(strSubstitutor);

		assertThat(DynatraceGenericLogIngestAppender.createBuilder()
				.setConfiguration(configuration)
				.setActiveGateUrl(new URL("http://localhost"))
				.setToken("token")
				.setName("name")
				.setSslValidation(true)
				.build())
				.isNotNull();
	}
}