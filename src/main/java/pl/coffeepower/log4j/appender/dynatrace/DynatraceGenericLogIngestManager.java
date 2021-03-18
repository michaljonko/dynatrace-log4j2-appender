package pl.coffeepower.log4j.appender.dynatrace;

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

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
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
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.util.Strings;

final class DynatraceGenericLogIngestManager
		extends AbstractManager {

	private static final ManagerFactory<DynatraceGenericLogIngestManager, ManagerConfig> MANAGER_FACTORY =
			new DynatraceGenericLogIngestManagerFactory();
	private static final ContentType CONTENT_TYPE =
			ContentType.APPLICATION_JSON.withCharset(UTF_8);
	private static final BasicHeader USER_AGENT_HEADER =
			new BasicHeader(HttpHeaders.USER_AGENT, "Dynatrace Generic Log Ingest Appender");

	private final String activeGateUrl;
	private final BasicHeader authorizationTokenHeader;
	private final CloseableHttpClient httpClient;

	private DynatraceGenericLogIngestManager(String name, ManagerConfig config) {
		super(config.getLoggerContext(), requireNonNull(name, "name is null"));

		requireNonNull(config, "config is null");

		this.activeGateUrl = requireNonNull(config.getActiveGateUrl(), "activeGateUrl is null").toString();
		this.authorizationTokenHeader =
				new BasicHeader("Authorization", "Api-Token " + requireNonNull(config.getToken(), "token is null"));

		final HttpClientBuilder httpClientBuilder = HttpClients.custom()
				.disableAuthCaching()
				.disableCookieManagement()
				.setConnectionTimeToLive(10L, TimeUnit.SECONDS);

		if (!config.isSslValidation()) {
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

	@Override
	protected boolean releaseSub(long timeout, TimeUnit timeUnit) {
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

	void send(String json) {
		if (Strings.isBlank(json)) {
			return;
		}

		HttpPost request = new HttpPost(activeGateUrl);
		request.setHeader(authorizationTokenHeader);
		request.setHeader(USER_AGENT_HEADER);
		request.setEntity(new StringEntity(json, CONTENT_TYPE));

		try (CloseableHttpResponse response = httpClient.execute(request)) {
			StatusLine statusLine = response.getStatusLine();
			if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
				logWarn("ActiveGate rejected request.",
						new RejectedRequestException(statusLine.getStatusCode(), statusLine.getReasonPhrase()));
			}
		} catch (IOException e) {
			logError("Cannot send log event", e);
		}
	}

	static DynatraceGenericLogIngestManager getManager(String name, ManagerConfig managerConfig) {
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

		ManagerConfig(LoggerContext loggerContext, URL activeGateUrl, String token, boolean sslValidation) {
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
		public DynatraceGenericLogIngestManager createManager(String name, ManagerConfig config) {
			return new DynatraceGenericLogIngestManager(name, config);
		}
	}

	private static final class RejectedRequestException extends RuntimeException {

		private RejectedRequestException(int statusCode, String reason) {
			super("statusCode=" + statusCode + " reason=" + reason, null, false, false);
		}
	}
}
