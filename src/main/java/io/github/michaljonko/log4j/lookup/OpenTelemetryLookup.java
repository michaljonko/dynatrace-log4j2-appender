package io.github.michaljonko.log4j.lookup;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.AbstractLookup;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

/**
 * OpenTelemetry Lookup to get extra data from OpenTelemetry Instrumentation for Java Agent.
 */
@Plugin(name = "otel", category = StrLookup.CATEGORY)
public final class OpenTelemetryLookup extends AbstractLookup {

	private static final Set<String> ALLOWED_KEYS = new HashSet<>(Arrays.asList(
			"trace_id",
			"span_id",
			"trace_flags"
	));

	private final Function<LogEvent, ReadOnlyStringMap> contextDataExtractor;

	/**
	 * Default constructor.
	 */
	public OpenTelemetryLookup() {
		this(LogEvent::getContextData);
	}

	OpenTelemetryLookup(Function<LogEvent, ReadOnlyStringMap> contextDataExtractor) {
		this.contextDataExtractor = requireNonNull(contextDataExtractor, "contextDataExtractor is null");
	}

	@Override
	public String lookup(LogEvent event, String key) {
		if (nonNull(event) && ALLOWED_KEYS.contains(key) && nonNull(event.getContextData())) {
			return contextDataExtractor
					.andThen(map -> map.getValue(key))
					.andThen(o -> nonNull(o) ? o.toString() : null)
					.apply(event);
		}

		return null;
	}
}
