package io.github.michaljonko.log4j.appender;

import static java.util.Objects.requireNonNull;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractManager;

abstract class AbstractDynatraceGenericLogIngestManager
		extends AbstractManager {

	AbstractDynatraceGenericLogIngestManager(LoggerContext loggerContext,
			String name) {
		super(requireNonNull(loggerContext, "loggerContext is null"),
				requireNonNull(name, "name is null"));
	}

	protected abstract Status send(String message);

	enum Status {
		SUCCESS,
		FAILED,
		EXCEPTION,
		EMPTY_MESSAGE
	}
}
