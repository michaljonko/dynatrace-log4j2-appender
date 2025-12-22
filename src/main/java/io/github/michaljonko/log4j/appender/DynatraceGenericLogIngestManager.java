package io.github.michaljonko.log4j.appender;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.util.Strings;

final class DynatraceGenericLogIngestManager
		extends AbstractDynatraceGenericLogIngestManager {

	private static final ManagerFactory<DynatraceGenericLogIngestManager, ManagerConfig> MANAGER_FACTORY =
			new DynatraceGenericLogIngestManagerFactory();
	private static final String CONTENT_TYPE =
			"application/json; charset=UTF-8";
	private static final String USER_AGENT =
			"Dynatrace Generic Log Ingest Appender";

	private final URI activeGateUrl;
	private final String authorizationToken;
	private final HttpClient httpClient;

	DynatraceGenericLogIngestManager(String name,
			ManagerConfig managerConfig,
			Duration connectionTimeout) {
		super(requireNonNull(requireNonNull(managerConfig, "managerConfig is null").getLoggerContext(), "loggerContext is null"),
				requireNonNull(name, "name is null"));

		try {
			this.activeGateUrl =
					requireNonNull(managerConfig.getActiveGateUrl(), "activeGateUrl is null").toURI();
			this.authorizationToken =
					"Api-Token " + requireNonNull(managerConfig.getToken(), "token is null");

			final var httpClientBuilder = HttpClient.newBuilder()
					.followRedirects(HttpClient.Redirect.NORMAL)
					.connectTimeout(connectionTimeout)
					.executor(Executors.newSingleThreadExecutor());

			if (!managerConfig.isSslValidation()) {
				try {
					var trustManager = new X509ExtendedTrustManager() {

						@Override
						public X509Certificate[] getAcceptedIssuers() {
							return new X509Certificate[0];
						}

						@Override
						public void checkClientTrusted(X509Certificate[] chain, String authType) {
						}

						@Override
						public void checkServerTrusted(X509Certificate[] chain, String authType) {
						}

						@Override
						public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
						}

						@Override
						public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
						}

						@Override
						public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
						}

						@Override
						public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
						}
					};
					var sslContext = SSLContext.getInstance("TLS");
					sslContext.init(null, new TrustManager[] { trustManager }, new SecureRandom());
					httpClientBuilder.sslContext(sslContext);
				} catch (NoSuchAlgorithmException | KeyManagementException e) {
					logError("Error during appender initialization. SSL validation cannot be disabled.", e);
				}
			}

			this.httpClient = httpClientBuilder.build();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("ActiveGate URL cannot be converted to URI", e);
		}
	}

	DynatraceGenericLogIngestManager(String name,
			ManagerConfig managerConfig) {
		this(name,
				managerConfig,
				Duration.ofSeconds(30L));
	}

	@Override
	protected boolean releaseSub(long timeout,
			TimeUnit timeUnit) {
		super.releaseSub(timeout, timeUnit);

		return nonNull(httpClient);
	}

	@Override
	protected Status send(String message) {
		if (Strings.isBlank(message)) {
			return Status.EMPTY_MESSAGE;
		}

		final var request = HttpRequest.newBuilder()
				.uri(activeGateUrl)
				.POST(HttpRequest.BodyPublishers.ofString(message, UTF_8))
				.header("Authorization", authorizationToken)
				.header("Content-Type", CONTENT_TYPE)
				.header("User-Agent", USER_AGENT)
				.build();

		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			var statusCode = response.statusCode();
			var success = statusCode == 200 || statusCode == 204;
			if (!success) {
				logWarn("ActiveGate rejected request.",
						new RejectedRequestException(statusCode));
				return Status.FAILED;
			}

			return Status.SUCCESS;
		} catch (IOException | InterruptedException e) {
			logError("Cannot send log event", e);
			return Status.EXCEPTION;
		}
	}

	static DynatraceGenericLogIngestManager getManager(String name,
			ManagerConfig managerConfig) {
		return getManager(
				requireNonNull(name, "name is null"),
				MANAGER_FACTORY,
				requireNonNull(managerConfig, "managerConfig is null"));
	}

	static final class ManagerConfig {

		private final LoggerContext loggerContext;
		private final URL activeGateUrl;
		private final String token;
		private final boolean sslValidation;

		ManagerConfig(LoggerContext loggerContext,
				URL activeGateUrl,
				String token,
				boolean sslValidation) {
			this.loggerContext = requireNonNull(loggerContext, "loggerContext is null");
			this.activeGateUrl = requireNonNull(activeGateUrl, "activeGateUrl is null");
			this.token = requireNonNull(token, "token is null");
			this.sslValidation = sslValidation;
		}

		LoggerContext getLoggerContext() {
			return loggerContext;
		}

		URL getActiveGateUrl() {
			return activeGateUrl;
		}

		String getToken() {
			return token;
		}

		boolean isSslValidation() {
			return sslValidation;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			var data = (ManagerConfig) o;
			return sslValidation == data.sslValidation &&
					Objects.equals(loggerContext, data.loggerContext) &&
					Objects.equals(activeGateUrl, data.activeGateUrl) &&
					Objects.equals(token, data.token);
		}

		@Override
		public int hashCode() {
			return Objects.hash(loggerContext, activeGateUrl, token, sslValidation);
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", ManagerConfig.class.getSimpleName() + "[", "]")
					.add("loggerContext=" + loggerContext)
					.add("activeGateUrl='" + activeGateUrl + "'")
					.add("token='########'")
					.add("sslValidation=" + sslValidation)
					.toString();
		}
	}

	private static final class DynatraceGenericLogIngestManagerFactory implements
			ManagerFactory<DynatraceGenericLogIngestManager, ManagerConfig> {

		@Override
		public DynatraceGenericLogIngestManager createManager(String name,
				ManagerConfig config) {
			return new DynatraceGenericLogIngestManager(name, config);
		}
	}

	private static final class RejectedRequestException extends RuntimeException {

		private RejectedRequestException(int statusCode) {
			super("statusCode=" + statusCode, null, false, false);
		}

		private RejectedRequestException(int statusCode,
				String reason) {
			super("statusCode=" + statusCode + " reason=" + reason, null, false, false);
		}
	}
}
