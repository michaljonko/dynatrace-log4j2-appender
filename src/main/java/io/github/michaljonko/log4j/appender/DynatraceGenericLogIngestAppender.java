package io.github.michaljonko.log4j.appender;

import static io.github.michaljonko.log4j.appender.DynatraceGenericLogIngestManager.ManagerConfig;
import static io.github.michaljonko.log4j.appender.DynatraceGenericLogIngestManager.getManager;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static org.apache.logging.log4j.util.Strings.dquote;

import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.util.JsonUtils;
import org.apache.logging.log4j.core.util.datetime.FixedDateFormat;

/**
 * Log4J2 appender to make Java applications logging on Dynatrace easy.
 */
@Plugin(name = "DynatraceGenericLogIngestAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class DynatraceGenericLogIngestAppender
		extends AbstractAppender {

	private static final FixedDateFormat DATE_FORMAT = FixedDateFormat.create(FixedDateFormat.FixedFormat.ISO8601_PERIOD);
	private static final String PACKAGE = DynatraceGenericLogIngestAppender.class.getPackage().getName();

	private final AbstractDynatraceGenericLogIngestManager manager;
	private final StrSubstitutor strSubstitutor;
	private final Set<DynatraceGenericLogIngestAttribute> attributes;

	DynatraceGenericLogIngestAppender(String name,
			Layout<? extends Serializable> layout,
			Filter filter,
			StrSubstitutor strSubstitutor,
			boolean ignoreExceptions,
			Property[] properties,
			AbstractDynatraceGenericLogIngestManager manager) {
		super(name, filter, requireNonNull(layout, "layout is null"), ignoreExceptions, properties);

		this.manager = requireNonNull(manager, "manager is null");
		this.strSubstitutor = requireNonNull(strSubstitutor, "strSubstitutor is null");

		if (nonNull(properties) && properties.length > 0) {
			var distinctPropertyNames = Arrays.stream(properties)
					.map(Property::getName)
					.distinct()
					.count();

			if (distinctPropertyNames != properties.length) {
				throw new IllegalArgumentException("property with the same name defined");
			}

			this.attributes = Arrays.stream(properties)
					.map(p -> new DynatraceGenericLogIngestAttribute(p.getName(), p.getValue(), p.isValueNeedsLookup()))
					.collect(collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
		} else {
			this.attributes = Set.of();
		}
	}

	@Override
	public void append(LogEvent event) {
		if (isNull(event)) {
			return;
		}

		var loggerName = event.getLoggerName();
		if (nonNull(loggerName) && loggerName.startsWith(PACKAGE)) {
			getStatusLogger().warn("Recursive logging from [{}] for appender [{}].", event.getLoggerName(), getName());
			return;
		}

		final var layout = getLayout();
		byte[] message;
		if (layout instanceof SerializedLayout) {
			var header = layout.getHeader();
			var formattedEvent = layout.toByteArray(event);
			message = new byte[header.length + formattedEvent.length];
			System.arraycopy(header, 0, message, 0, header.length);
			System.arraycopy(formattedEvent, 0, message, header.length, formattedEvent.length);
		} else {
			message = layout.toByteArray(event);
		}

		final var jsonBuilder = new StringBuilder()
				.append("{")
				.append(dquote("timestamp")).append(":").append(dquote(DATE_FORMAT.formatInstant(event.getInstant())))
				.append(",")
				.append(dquote("level")).append(":").append(dquote(event.getLevel().name())).append(",");

		for (var attribute : attributes) {
			var name = attribute.getName();
			var value =
					attribute.valueNeedsLookup() ? strSubstitutor.replace(event, attribute.getValue()) : attribute.getValue();

			jsonBuilder.append(dquote(name)).append(":\"");
			JsonUtils.quoteAsString(value, jsonBuilder);
			jsonBuilder.append("\",");
		}

		jsonBuilder.append(dquote("message")).append(":\"");
		JsonUtils.quoteAsString(new String(message), jsonBuilder);
		jsonBuilder.append("\"}");

		var jsonMessage = jsonBuilder.toString();
		if (manager.send(jsonMessage) != AbstractDynatraceGenericLogIngestManager.Status.SUCCESS) {
			getStatusLogger().warn("Cannot send log event {}", jsonMessage);
		}
	}

	@Override
	public boolean stop(long timeout,
			TimeUnit timeUnit) {
		return super.stop(timeout, timeUnit) && manager.stop(timeout, timeUnit);
	}

	/**
	 * Builder factory method.
	 *
	 * @param <B> type
	 * @return new instance of the Builder
	 */
	@PluginBuilderFactory
	public static <B extends Builder<B>> B createBuilder() {
		return new Builder<B>().asBuilder();
	}

	/**
	 * Builder for {@link io.github.michaljonko.log4j.appender.DynatraceGenericLogIngestAppender}.
	 *
	 * @param <B> DynatraceGenericLogIngestAppender
	 */
	public static final class Builder<B extends Builder<B>>
			extends org.apache.logging.log4j.core.appender.AbstractAppender.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<DynatraceGenericLogIngestAppender> {

		@PluginAttribute("activeGateUrl")
		@Required(message = "No URL provided for ActiveGate")
		private URL activeGateUrl;
		@PluginAttribute(value = "token", sensitive = true)
		private String token;
		@PluginAttribute(value = "sslValidation", defaultBoolean = true)
		private boolean sslValidation;

		/**
		 * Get Active Gate URL.
		 *
		 * @return URL
		 */
		public URL getActiveGateUrl() {
			return activeGateUrl;
		}

		/**
		 * Set Active Gate URL.
		 *
		 * @param activeGateUrl valid url
		 * @return this
		 */
		public B setActiveGateUrl(URL activeGateUrl) {
			this.activeGateUrl = activeGateUrl;
			return asBuilder();
		}

		/**
		 * Get Token used for authentication on Active Gate.
		 *
		 * @return token
		 */
		public String getToken() {
			return token;
		}

		/**
		 * Set Token for authentication on Active Gate. It has to be token with Log Ingest permission.
		 *
		 * @param token valid token
		 * @return this
		 */
		public B setToken(String token) {
			this.token = token;
			return asBuilder();
		}

		/**
		 * Should validate SSL connection.
		 *
		 * @return true if will valid
		 */
		public boolean isSslValidation() {
			return sslValidation;
		}

		/**
		 * Set SSL validation flag.
		 *
		 * @param sslValidation true - will valid, false - skip validation
		 * @return this
		 */
		public B setSslValidation(boolean sslValidation) {
			this.sslValidation = sslValidation;
			return asBuilder();
		}

		@Override
		public DynatraceGenericLogIngestAppender build() {
			final var managerConfig =
					new ManagerConfig(requireNonNull(getConfiguration(), "configuration is null").getLoggerContext(),
							getActiveGateUrl(),
							getToken(),
							isSslValidation());

			final var manager = getManager(getName(), managerConfig);

			return new DynatraceGenericLogIngestAppender(getName(),
					getOrCreateLayout(),
					getFilter(),
					getConfiguration().getStrSubstitutor(),
					isIgnoreExceptions(),
					getPropertyArray(),
					manager);
		}
	}
}
