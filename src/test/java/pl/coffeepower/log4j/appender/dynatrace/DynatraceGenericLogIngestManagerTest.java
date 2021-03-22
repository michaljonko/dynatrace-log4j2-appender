package pl.coffeepower.log4j.appender.dynatrace;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.coffeepower.log4j.appender.dynatrace.AbstractDynatraceGenericLogIngestManager.Status;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
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

import pl.coffeepower.log4j.appender.dynatrace.DynatraceGenericLogIngestManager.ManagerConfig;

@ExtendWith(MockitoExtension.class)
class DynatraceGenericLogIngestManagerTest {

	private static final String TOKEN = "123456";
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
						.withHeader("Content-Type", new EqualToPattern(APPLICATION_JSON.withCharset(UTF_8).toString()))
						.willReturn(aResponse()
								.withFixedDelay(1)
								.withStatus(HttpStatus.SC_OK))
		);
		mockServer.stubFor(
				post("/ingest-timeout")
						.willReturn(aResponse()
								.withFixedDelay((int) TimeUnit.SECONDS.toMillis(1L))
								.withStatus(HttpStatus.SC_OK))
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
		final URL activeGateUrl = new URL(mockServer.url("/ingest-timeout"));
		final ManagerConfig config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				TOKEN,
				false
		);
		final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10).build();

		DynatraceGenericLogIngestManager manager = new DynatraceGenericLogIngestManager("manager", config, requestConfig);

		assertThat(manager.send("message"))
				.isEqualTo(Status.EXCEPTION);
	}

	@Test
	void returnExceptionWhenCannotValidCert() throws Exception {
		final URL activeGateUrl =
				new URL("https://" + mockServer.getOptions().bindAddress() + ":" + mockServer.httpsPort() + "/ingest");
		final ManagerConfig config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				TOKEN,
				true
		);

		DynatraceGenericLogIngestManager manager = new DynatraceGenericLogIngestManager("manager", config);

		assertThat(manager.send("message"))
				.isEqualTo(Status.EXCEPTION);
	}

	@Test
	void createAndReleaseManager() throws Exception {
		final URL activeGateUrl = new URL(mockServer.baseUrl());
		final ManagerConfig config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				TOKEN,
				false
		);

		DynatraceGenericLogIngestManager manager = new DynatraceGenericLogIngestManager("manager", config);

		assertThat(manager.releaseSub(1L, TimeUnit.SECONDS))
				.isTrue();
	}

	@ParameterizedTest
	@MethodSource("sourceForSendMessage")
	void sendMessage(final String path,
			final String token,
			final String logMessage,
			final Status expectedStatus) throws Exception {
		final URL activeGateUrl = new URL(mockServer.url(path));
		final ManagerConfig config = new ManagerConfig(
				loggerContext,
				activeGateUrl,
				token,
				false
		);

		DynatraceGenericLogIngestManager manager = new DynatraceGenericLogIngestManager("manager", config);

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