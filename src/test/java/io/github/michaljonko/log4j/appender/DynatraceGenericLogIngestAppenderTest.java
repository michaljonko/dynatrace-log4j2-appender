package io.github.michaljonko.log4j.appender;

import static org.apache.logging.log4j.core.config.Property.createProperty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.AdditionalAnswers;

import io.github.michaljonko.log4j.appender.AbstractDynatraceGenericLogIngestManager;
import io.github.michaljonko.log4j.appender.DynatraceGenericLogIngestAppender;

class DynatraceGenericLogIngestAppenderTest {

	private final java.time.Instant NOW = java.time.Instant.parse("2021-05-05T01:01:30.00Z");

	@ParameterizedTest
	@MethodSource("sourceForNullPointer")
	void throwExceptionForIncorrectParams(final String name,
			final Layout layout,
			final StrSubstitutor substitutor,
			final AbstractDynatraceGenericLogIngestManager manager,
			final Property[] properties,
			final Class<? extends Throwable> expectedExceptionType,
			final String expectedMessage) {
		final Filter filter = mock(Filter.class);

		assertThatExceptionOfType(expectedExceptionType)
				.isThrownBy(() ->
						new DynatraceGenericLogIngestAppender(name, layout, filter, substitutor, false, properties, manager))
				.withMessage(expectedMessage);
	}

	private static Stream<Arguments> sourceForNullPointer() {
		Layout layout = mock(Layout.class);
		StrSubstitutor substitutor = mock(StrSubstitutor.class);
		AbstractDynatraceGenericLogIngestManager manager = mock(AbstractDynatraceGenericLogIngestManager.class);

		return Stream.of(
				Arguments.of(null, layout, substitutor, manager, null, NullPointerException.class, "name"),
				Arguments.of("name", null, substitutor, manager, null, NullPointerException.class, "layout is null"),
				Arguments.of("name", layout, null, manager, null, NullPointerException.class, "strSubstitutor is null"),
				Arguments.of("name", layout, substitutor, null, null, NullPointerException.class, "manager is null"),
				Arguments.of("name", layout, substitutor, manager,
						new Property[] { createProperty("p1", "v1"), createProperty("p2", "v2"), createProperty("p1", "v") },
						IllegalArgumentException.class, "property with the same name defined")
		);
	}

	@Test
	void doNotCallManagerWhenMessageIsNull() {
		final Layout layout = mock(Layout.class);
		final StrSubstitutor substitutor = mock(StrSubstitutor.class);
		final Filter filter = mock(Filter.class);
		final AbstractDynatraceGenericLogIngestManager manager = mock(AbstractDynatraceGenericLogIngestManager.class);

		DynatraceGenericLogIngestAppender appender =
				new DynatraceGenericLogIngestAppender("name", layout, filter, substitutor, false, null, manager);

		appender.append(null);

		then(manager)
				.shouldHaveNoInteractions();
	}

	@Test
	void doNotCallManagerWhenEventIsForLogger() {
		final Layout layout = mock(Layout.class);
		final StrSubstitutor substitutor = mock(StrSubstitutor.class);
		final Filter filter = mock(Filter.class);
		final AbstractDynatraceGenericLogIngestManager manager = mock(AbstractDynatraceGenericLogIngestManager.class);
		final LogEvent logEvent = mock(LogEvent.class);

		given(logEvent.getLoggerName())
				.willReturn(this.getClass().getPackage().getName());

		DynatraceGenericLogIngestAppender appender =
				new DynatraceGenericLogIngestAppender("name", layout, filter, substitutor, false, null, manager);

		appender.append(logEvent);

		then(manager)
				.shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@MethodSource("sourceForSendMessage")
	void sendMessage(final String message,
			final Property[] properties,
			final String expectedJson) {
		final Layout layout = mock(Layout.class);
		final StrSubstitutor substitutor = mock(StrSubstitutor.class);
		final Filter filter = mock(Filter.class);
		final AbstractDynatraceGenericLogIngestManager manager = mock(AbstractDynatraceGenericLogIngestManager.class);
		final LogEvent logEvent = mock(LogEvent.class);
		final Instant instant = mock(Instant.class);

		given(instant.getEpochMillisecond())
				.willReturn(NOW.toEpochMilli());
		given(instant.getNanoOfMillisecond())
				.willReturn(NOW.getNano());
		given(logEvent.getInstant())
				.willReturn(instant);
		given(logEvent.getLevel())
				.willReturn(Level.DEBUG);
		given(layout.toByteArray(logEvent))
				.willReturn(message.getBytes(StandardCharsets.UTF_8));
		given(substitutor.replace(eq(logEvent), anyString()))
				.willAnswer(AdditionalAnswers.<String, LogEvent, String> answer(
						(event, value) -> value.replace("${", "").replace("}", "")));

		DynatraceGenericLogIngestAppender appender =
				new DynatraceGenericLogIngestAppender("name", layout, filter, substitutor, false, properties, manager);

		appender.append(logEvent);

		then(manager)
				.should()
				.send(expectedJson);
	}

	private static Stream<Arguments> sourceForSendMessage() {
		return Stream.of(
				Arguments.of(
						"simple message",
						null,
						"{\"timestamp\":\"2021-05-05T03:01:30.000\",\"level\":\"DEBUG\",\"message\":\"simple message\"}"
				),
				Arguments.of(
						"simple message",
						new Property[] { createProperty("prop", "value") },
						"{\"timestamp\":\"2021-05-05T03:01:30.000\",\"level\":\"DEBUG\",\"prop\":\"value\",\"message\":\"simple message\"}"
				),
				Arguments.of(
						"simple message",
						new Property[] { createProperty("p1", "v1"), createProperty("p2", "v2") },
						"{\"timestamp\":\"2021-05-05T03:01:30.000\",\"level\":\"DEBUG\",\"p1\":\"v1\",\"p2\":\"v2\",\"message\":\"simple message\"}"
				),
				Arguments.of(
						"simple message",
						new Property[] { createProperty("p1", "v1"), createProperty("p2", "v2"), createProperty("p3", "${eval}")
						},
						"{\"timestamp\":\"2021-05-05T03:01:30.000\",\"level\":\"DEBUG\",\"p1\":\"v1\",\"p2\":\"v2\",\"p3\":\"eval\",\"message\":\"simple message\"}"
				)
		);
	}

	@Test
	void sendMessageFormattedBySerializedLayout() {
		final SerializedLayout layout = SerializedLayout.createLayout();
		final StrSubstitutor substitutor = mock(StrSubstitutor.class);
		final Filter filter = mock(Filter.class);
		final AbstractDynatraceGenericLogIngestManager manager = mock(AbstractDynatraceGenericLogIngestManager.class);
		final LogEvent logEvent = mock(LogEvent.class);
		final Instant instant = mock(Instant.class);

		given(instant.getEpochMillisecond())
				.willReturn(NOW.toEpochMilli());
		given(instant.getNanoOfMillisecond())
				.willReturn(NOW.getNano());
		given(logEvent.getInstant())
				.willReturn(instant);
		given(logEvent.getLevel())
				.willReturn(Level.DEBUG);

		DynatraceGenericLogIngestAppender appender =
				new DynatraceGenericLogIngestAppender("name", layout, filter, substitutor, false, null, manager);

		appender.append(logEvent);

		then(manager)
				.should()
				.send(anyString());
	}

	@Test
	void stopAppender() {
		final Layout layout = mock(Layout.class);
		final StrSubstitutor substitutor = mock(StrSubstitutor.class);
		final Filter filter = mock(Filter.class);
		final AbstractDynatraceGenericLogIngestManager manager = mock(AbstractDynatraceGenericLogIngestManager.class);

		given(manager.stop(1L, TimeUnit.SECONDS))
				.willReturn(true);

		DynatraceGenericLogIngestAppender appender =
				new DynatraceGenericLogIngestAppender("name", layout, filter, substitutor, false, null, manager);

		assertThat(appender.stop(1L, TimeUnit.SECONDS))
				.isTrue();

		then(manager)
				.should()
				.stop(1L, TimeUnit.SECONDS);
	}
}
