package io.github.michaljonko.log4j.appender;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URL;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DynatraceGenericLogIngestAppenderBuilderTest {

	static class MockConfiguration extends AbstractConfiguration {

		private final boolean withoutStrSubstitutor;
		final LoggerContext strongReferenceToKeepLoggerContext;

		MockConfiguration(LoggerContext loggerContext, boolean withoutStrSubstitutor) {
			super(loggerContext, ConfigurationSource.NULL_SOURCE);
			this.withoutStrSubstitutor = withoutStrSubstitutor;
			this.strongReferenceToKeepLoggerContext = loggerContext;
		}

		static MockConfiguration withMockLoggerContextWithoutStrSubstitutor() {
			return new MockConfiguration(mock(LoggerContext.class), true);
		}

		@Override
		public StrSubstitutor getStrSubstitutor() {
			return withoutStrSubstitutor ? null : super.getStrSubstitutor();
		}
	}

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
						MockConfiguration.withMockLoggerContextWithoutStrSubstitutor(),
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

		assertThat(DynatraceGenericLogIngestAppender.createBuilder()
				.setConfiguration(new MockConfiguration(mock(LoggerContext.class), false))
				.setActiveGateUrl(new URL("http://localhost"))
				.setToken("token")
				.setName("name")
				.setSslValidation(true)
				.build())
				.isNotNull();
	}
}