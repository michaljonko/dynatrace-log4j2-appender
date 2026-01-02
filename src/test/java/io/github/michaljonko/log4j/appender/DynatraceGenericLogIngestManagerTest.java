package io.github.michaljonko.log4j.appender;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.github.michaljonko.log4j.appender.AbstractDynatraceGenericLogIngestManager.Status;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.ContainsPattern;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;

import io.github.michaljonko.log4j.appender.DynatraceGenericLogIngestManager.ManagerConfig;

@ExtendWith(MockitoExtension.class)
class DynatraceGenericLogIngestManagerTest {

	private static final String TOKEN = "123456";
	private static final int[] SUCCESS_STATUS_CODES = { 200, 204 };
	@Mock
	private LoggerContext loggerContext;
	private WireMockServer mockServer;

	@BeforeEach
	void setUp() {
		mockServer = new WireMockServer(
				WireMockConfiguration.options()
						.dynamicPort()
						.dynamicHttpsPort());
		mockServer.stubFor(
				post("/ingest")
						.withHeader("Authorization", new EqualToPattern("Api-Token " + TOKEN))
						.withHeader("User-Agent", new ContainsPattern("Dynatrace"))
						.withHeader("Content-Type", new EqualToPattern("application/json; charset=UTF-8"))
						.willReturn(aResponse()
								.withFixedDelay(1)
								.withStatus(
										SUCCESS_STATUS_CODES[(int) (System.currentTimeMillis() % SUCCESS_STATUS_CODES.length)]))
		);
		mockServer.stubFor(
				post("/ingest-timeout")
						.willReturn(aResponse()
								.withFixedDelay((int) TimeUnit.SECONDS.toMillis(1L))
								.withStatus(200))
		);

		mockServer.start();
	}

	@AfterEach
	void tearDown() {
		if (nonNull(mockServer)) {
			mockServer.stop();
		}
	}

	@Test
	void returnExceptionWhenTimeoutOccurs() throws Exception {
		final var activeGateUrl = new URL(mockServer.url("/ingest-timeout"));
		final var config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				TOKEN,
				false
		);

		var manager = new DynatraceGenericLogIngestManager("manager", config, Duration.ofMillis(100L));

		assertThat(manager.send("message"))
				.isEqualTo(Status.EXCEPTION);
	}

	@Test
	void returnExceptionWhenCannotValidCert() throws Exception {
		final var activeGateUrl =
				new URL("https://" + mockServer.getOptions().bindAddress() + ":" + mockServer.httpsPort() + "/ingest");
		final var config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				TOKEN,
				true
		);

		var manager = new DynatraceGenericLogIngestManager("manager", config);

		assertThat(manager.send("message"))
				.isEqualTo(Status.EXCEPTION);
	}

	@Test
	void createAndReleaseManager() throws Exception {
		final var activeGateUrl = new URL(mockServer.baseUrl());
		final var config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				TOKEN,
				false
		);

		var manager = new DynatraceGenericLogIngestManager("manager", config);

		assertThat(manager.releaseSub(1L, TimeUnit.SECONDS))
				.isTrue();
	}

	@ParameterizedTest
	@MethodSource("sourceForSendMessage")
	void sendMessage(final String path,
			final String token,
			final String logMessage,
			final Status expectedStatus) throws Exception {
		final var activeGateUrl = new URL(mockServer.url(path));
		final var config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				token,
				false
		);

		var manager = new DynatraceGenericLogIngestManager("manager", config);

		assertThat(manager.send(logMessage))
				.isEqualTo(expectedStatus);
	}

	private static Stream<Arguments> sourceForSendMessage() {
		return Stream.of(
				Arguments.of("/ingest", TOKEN, "Simple log message", Status.SUCCESS),
				Arguments.of("/ingest", TOKEN, null, Status.EMPTY_MESSAGE),
				Arguments.of("/ingest", TOKEN, "", Status.EMPTY_MESSAGE),
				Arguments.of("/ingest", TOKEN, "   ", Status.EMPTY_MESSAGE),
				Arguments.of("/ingest", "x", "Simple log message", Status.FAILED),
				Arguments.of("/ingest", "x", null, Status.EMPTY_MESSAGE),
				Arguments.of("/ingest", "x", "", Status.EMPTY_MESSAGE),
				Arguments.of("/ingest", "x", "  ", Status.EMPTY_MESSAGE)
		);
	}
}