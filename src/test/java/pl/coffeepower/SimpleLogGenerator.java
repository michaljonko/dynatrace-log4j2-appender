package pl.coffeepower;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.ThreadContext;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
@AllArgsConstructor(access = AccessLevel.MODULE)
@ToString
final class SimpleLogGenerator implements AutoCloseable {

	@Getter(AccessLevel.MODULE)
	private final String text;
	private final AtomicInteger atomicNumber;

	SimpleLogGenerator(@NonNull String text) {
		ThreadContext.put("trace_id", UUID.randomUUID().toString());
		log.info("Creating SimpleApp");
		this.text = text;
		this.atomicNumber = new AtomicInteger();
	}

	int getAtomicNumberValue() {
		int value = atomicNumber.intValue();
		log.debug("Getting 'atomicNumber' value: {}", value);
		return value;
	}

	void setAtomicNumberValue(int number) {
		log.debug("Setting 'atomicNumber' value: {}", number);
		this.atomicNumber.set(number);
	}

	@Override
	public void close() {
		log.warn("Closing SimpleApp");
		ThreadContext.clearMap();
	}
}
