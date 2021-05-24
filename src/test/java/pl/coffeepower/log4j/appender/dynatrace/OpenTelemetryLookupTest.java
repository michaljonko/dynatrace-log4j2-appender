package pl.coffeepower.log4j.appender.dynatrace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryLookupTest {

	@Mock
	private ReadOnlyStringMap contextData;
	@Mock
	private LogEvent logEvent;

	@Test
	void readTraceId() {
		UUID traceId = UUID.randomUUID();

		given(contextData.getValue("trace_id"))
				.willReturn(traceId);
		given(logEvent.getContextData())
				.willReturn(contextData);

		OpenTelemetryLookup lookup = new OpenTelemetryLookup(logEvent -> contextData);

		assertThat(lookup.lookup(logEvent, "trace_id"))
				.isEqualTo(traceId.toString());
		assertThat(lookup.lookup("trace_id"))
				.isNull();
		assertThat(lookup.lookup("traceId"))
				.isNull();
	}
}