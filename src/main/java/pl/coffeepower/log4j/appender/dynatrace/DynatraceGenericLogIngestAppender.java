package pl.coffeepower.log4j.appender.dynatrace;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.apache.logging.log4j.util.Strings.dquote;
import static pl.coffeepower.log4j.appender.dynatrace.DynatraceGenericLogIngestManager.ManagerConfig;
import static pl.coffeepower.log4j.appender.dynatrace.DynatraceGenericLogIngestManager.getManager;

import java.io.Serializable;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

@Plugin(name = "DynatraceGenericLogIngestAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class DynatraceGenericLogIngestAppender
		extends AbstractAppender {

	private static final FixedDateFormat DATE_FORMAT = FixedDateFormat.create(FixedDateFormat.FixedFormat.ISO8601_PERIOD);
	private static final String PACKAGE = DynatraceGenericLogIngestAppender.class.getPackage().getName();

	private final AbstractDynatraceGenericLogIngestManager manager;
	private final StrSubstitutor strSubstitutor;
	private final Map<String, Property> attributes;

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
			this.attributes = new HashMap<>(properties.length);
			for (Property property : properties) {
				this.attributes.put(property.getName(), property);
			}
		} else {
			this.attributes = Collections.emptyMap();
		}
	}

	@Override
	public void append(LogEvent event) {
		if (isNull(event)) {
			return;
		}

		String loggerName = event.getLoggerName();
		if (nonNull(loggerName) && loggerName.startsWith(PACKAGE)) {
			LOGGER.warn("Recursive logging from [{}] for appender [{}].", event.getLoggerName(), getName());
			return;
		}

		final Layout<? extends Serializable> layout = getLayout();
		byte[] message;
		if (layout instanceof SerializedLayout) {
			byte[] header = layout.getHeader();
			byte[] formattedEvent = layout.toByteArray(event);
			message = new byte[header.length + formattedEvent.length];
			System.arraycopy(header, 0, message, 0, header.length);
			System.arraycopy(formattedEvent, 0, message, header.length, formattedEvent.length);
		} else {
			message = layout.toByteArray(event);
		}

		StringBuilder jsonBuilder = new StringBuilder()
				.append("{")
				.append(dquote("timestamp")).append(":").append(dquote(DATE_FORMAT.formatInstant(event.getInstant())))
				.append(",")
				.append(dquote("level")).append(":").append(dquote(event.getLevel().name())).append(",");

		for (Property attribute : attributes.values()) {
			String attrKey = attribute.getName();
			String attrValue = attribute.isValueNeedsLookup()
					? strSubstitutor.replace(event, attribute.getValue())
					: attribute.getValue();
			jsonBuilder.append(dquote(attrKey)).append(":\"");
			JsonUtils.quoteAsString(attrValue, jsonBuilder);
			jsonBuilder.append("\",");
		}

		jsonBuilder.append(dquote("message")).append(":\"");
		JsonUtils.quoteAsString(new String(message), jsonBuilder);
		jsonBuilder.append("\"}");

		manager.send(jsonBuilder.toString());
	}

	@Override
	public boolean stop(long timeout,
			TimeUnit timeUnit) {
		return super.stop(timeout, timeUnit) && manager.stop(timeout, timeUnit);
	}

	@PluginBuilderFactory
	public static <B extends Builder<B>> B createBuilder() {
		return new Builder<B>().asBuilder();
	}

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

		public URL getActiveGateUrl() {
			return activeGateUrl;
		}

		public B setActiveGateUrl(URL activeGateUrl) {
			this.activeGateUrl = activeGateUrl;
			return asBuilder();
		}

		public String getToken() {
			return token;
		}

		public B setToken(String token) {
			this.token = token;
			return asBuilder();
		}

		public boolean isSslValidation() {
			return sslValidation;
		}

		public B setSslValidation(boolean sslValidation) {
			this.sslValidation = sslValidation;
			return asBuilder();
		}

		@Override
		public DynatraceGenericLogIngestAppender build() {
			final ManagerConfig managerConfig =
					new ManagerConfig(requireNonNull(getConfiguration(), "configuration is null").getLoggerContext(),
							getActiveGateUrl(),
							getToken(),
							isSslValidation());

			final DynatraceGenericLogIngestManager manager = getManager(getName(), managerConfig);

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
