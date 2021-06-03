package io.github.michaljonko.log4j;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.ContainsPattern;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.common.collect.Lists;

class DynatraceGenericLogIngestAppenderIntegrationTest {

	private static final URL CONFIG_RESOURCE =
			DynatraceGenericLogIngestAppenderIntegrationTest.class.getClassLoader().getResource("log4j2.xml");
	private static WireMockServer SERVER;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	static void beforeAll() throws IOException {
		SERVER = new WireMockServer(
				WireMockConfiguration.options()
						.dynamicPort());
		SERVER.stubFor(
				post("/ingest")
						.withHeader("Authorization", new EqualToPattern("Api-Token 123456"))
						.withHeader("User-Agent", new ContainsPattern("Dynatrace"))
						.withHeader("Content-Type", new EqualToPattern(APPLICATION_JSON.withCharset(UTF_8).toString()))
						.willReturn(aResponse().withFixedDelay(10).withStatus(HttpStatus.SC_OK))
		);
		SERVER.start();

		Path tempConfigFile = Files.createTempFile("log4j", "test");
		assertThat(
				Files.copy(
						CONFIG_RESOURCE.openStream(),
						tempConfigFile,
						StandardCopyOption.REPLACE_EXISTING))
				.isGreaterThan(0L);
		System.setProperty("activegateurl", SERVER.url("/ingest"));
		System.setProperty("activegatetoken", "123456");
		System.setProperty("log4j.configurationFile", tempConfigFile.toString());
	}

	@AfterAll
	static void afterAll() {
		if (nonNull(SERVER)) {
			SERVER.stop();
		}
	}

	@Test
	void integrationCheck() {
		try (SimpleLogGenerator app = new SimpleLogGenerator("Simple text")) {
			assertThat(app.getAtomicNumberValue())
					.isZero();
			app.setAtomicNumberValue(5);
			assertThat(app.getAtomicNumberValue())
					.isEqualTo(5);
		} catch (Throwable t) {
			fail(t);
		} finally {
			Awaitility.await()
					.pollDelay(Duration.ofSeconds(1L))
					.timeout(Duration.ofSeconds(5L))
					.until(() -> !SERVER.getServeEvents().getRequests().isEmpty());

			List<ServeEvent> requests = SERVER.getServeEvents().getRequests();

			assertThat(requests)
					.hasSize(5)
					.extracting(event -> event.getRequest().getBodyAsString())
					.extracting(objectMapper::readTree)
					.allSatisfy(jsonNode -> assertThat(jsonNode.get("dt.trace_id"))
							.isNotNull()
							.extracting(JsonNode::asText)
							.extracting(UUID::fromString)
							.isNotNull())
					.extracting(jsonNode -> Lists.newArrayList(jsonNode.fieldNames()))
					.allSatisfy(fields -> assertThat(fields)
							.containsOnly("timestamp", "level", "service.name", "dt.os.type", "message", "dt.trace_id"));
		}
	}
}