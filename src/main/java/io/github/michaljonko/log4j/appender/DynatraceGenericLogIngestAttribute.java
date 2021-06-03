package io.github.michaljonko.log4j.appender;

import java.util.Objects;

final class DynatraceGenericLogIngestAttribute {

	private final String name;
	private final String value;
	private final boolean valueNeedsLookup;
	private final int hash;

	DynatraceGenericLogIngestAttribute(String name,
			String value,
			boolean valueNeedsLookup) {
		this.name = name;
		this.value = value;
		this.valueNeedsLookup = valueNeedsLookup;
		this.hash = Objects.hash(name, value, valueNeedsLookup);
	}

	String getName() {
		return name;
	}

	String getValue() {
		return value;
	}

	boolean valueNeedsLookup() {
		return valueNeedsLookup;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DynatraceGenericLogIngestAttribute that = (DynatraceGenericLogIngestAttribute) o;
		return valueNeedsLookup == that.valueNeedsLookup &&
				Objects.equals(name, that.name) &&
				Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return hash;
	}
}
