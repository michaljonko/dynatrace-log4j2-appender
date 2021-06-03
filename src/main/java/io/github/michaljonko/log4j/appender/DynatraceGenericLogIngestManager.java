package io.github.michaljonko.log4j.appender;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.util.Strings;

final class DynatraceGenericLogIngestManager
		extends AbstractDynatraceGenericLogIngestManager {

	private static final ManagerFactory<DynatraceGenericLogIngestManager, ManagerConfig> MANAGER_FACTORY =
			new DynatraceGenericLogIngestManagerFactory();
	private static final ContentType CONTENT_TYPE =
			ContentType.APPLICATION_JSON.withCharset(UTF_8);
	private static final String USER_AGENT =
			"Dynatrace Generic Log Ingest Appender";

	private final String activeGateUrl;
	private final BasicHeader authorizationTokenHeader;
	private final CloseableHttpClient httpClient;

	DynatraceGenericLogIngestManager(String name,
			ManagerConfig managerConfig,
			RequestConfig requestConfig) {
		super(requireNonNull(requireNonNull(managerConfig, "managerConfig is null").getLoggerContext(), "loggerContext is null"),
				requireNonNull(name, "name is null"));

		this.activeGateUrl =
				requireNonNull(managerConfig.getActiveGateUrl(), "activeGateUrl is null").toString();
		this.authorizationTokenHeader =
				new BasicHeader("Authorization", "Api-Token " + requireNonNull(managerConfig.getToken(), "token is null"));

		final HttpClientBuilder httpClientBuilder = HttpClients.custom()
				.disableAuthCaching()
				.disableCookieManagement()
				.setUserAgent(USER_AGENT)
				.setDefaultRequestConfig(requestConfig);

		if (!managerConfig.isSslValidation()) {
			try {
				SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustSelfSignedStrategy()).build();
				HostnameVerifier verifier = new NoopHostnameVerifier();
				SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, verifier);
				httpClientBuilder.setSSLSocketFactory(sslSocketFactory);
			} catch (Exception t) {
				logError("Error during appender initialization. SSL validation cannot be disabled. "
						+ "Default SSLConnectionSocketFactory with SSLContext validation will be used.", t);
			}
		}

		this.httpClient = httpClientBuilder.build();
	}

	DynatraceGenericLogIngestManager(String name,
			ManagerConfig managerConfig) {
		this(name,
				managerConfig,
				RequestConfig.custom()
						.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(30))
						.setSocketTimeout((int) TimeUnit.SECONDS.toMillis(30))
						.setConnectionRequestTimeout((int) TimeUnit.SECONDS.toMillis(30))
						.build());
	}

	@Override
	protected boolean releaseSub(long timeout,
			TimeUnit timeUnit) {
		super.releaseSub(timeout, timeUnit);

		if (nonNull(httpClient)) {
			try {
				httpClient.close();
				return true;
			} catch (IOException e) {
				logError("Cannot close HttpClient", e);
			}
		}
		return false;
	}

	@Override
	protected Status send(String message) {
		if (Strings.isBlank(message)) {
			return Status.EMPTY_MESSAGE;
		}

		HttpPost request = new HttpPost(activeGateUrl);
		request.setHeader(authorizationTokenHeader);
		request.setEntity(new StringEntity(message, CONTENT_TYPE));

		StatusLine statusLine;
		try (CloseableHttpResponse response = httpClient.execute(request)) {
			statusLine = response.getStatusLine();
		} catch (IOException e) {
			logError("Cannot send log event", e);
			return Status.EXCEPTION;
		}

		int statusCode = statusLine.getStatusCode();
		boolean success = (statusCode == HttpStatus.SC_OK) || (statusCode == HttpStatus.SC_NO_CONTENT);

		if (!success) {
			logWarn("ActiveGate rejected request.",
					new RejectedRequestException(statusCode, statusLine.getReasonPhrase()));
			return Status.FAILED;
		}

		return Status.SUCCESS;
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
			ManagerConfig data = (ManagerConfig) o;
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

		private RejectedRequestException(int statusCode,
				String reason) {
			super("statusCode=" + statusCode + " reason=" + reason, null, false, false);
		}
	}
}
