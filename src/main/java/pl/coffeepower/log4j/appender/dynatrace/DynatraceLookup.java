package pl.coffeepower.log4j.appender.dynatrace;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.AbstractLookup;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

@Plugin(name = "dynatrace", category = StrLookup.CATEGORY)
public final class DynatraceLookup extends AbstractLookup {

	private static final Logger LOGGER = StatusLogger.getLogger();
	private static final Path MAGIC_FILE_PATH = Paths.get("dt_metadata_e617c525669e072eebe3d0f08212e8f2.properties");
	private final Map<String, String> metadata;

	public DynatraceLookup() {
		this(MAGIC_FILE_PATH);
	}

	DynatraceLookup(Path magicFilePath) {
		Map<String, String> tempMetadata = Collections.emptyMap();
		try (Stream<String> linesWithPath = Files.lines(requireNonNull(magicFilePath, "magicFilePath is null"))) {
			tempMetadata = linesWithPath.findFirst()
					.map(Paths::get)
					.map(DynatraceLookup::readMetadataFile)
					.orElse(Collections.emptyMap());
		} catch (IOException e) {
			LOGGER.error("DynatraceLookup cannot read metadata (magic file {})", magicFilePath);
		}
		this.metadata = tempMetadata;
	}

	private static Map<String, String> readMetadataFile(Path path) {
		try (Stream<String> linesWithProperties = Files.lines(path)) {
			return linesWithProperties
					.filter(Strings::isNotBlank)
					.filter(line -> !line.startsWith("=") && line.contains("="))
					.map(line -> line.split("=", 2))
					.filter(array -> array.length == 1 || array.length == 2)
					.collect(
							collectingAndThen(
									toMap(array -> array[0], array -> array.length == 2 ? array[1] : ""),
									Collections::unmodifiableMap));

		} catch (IOException e) {
			LOGGER.error("DynatraceLookup cannot read metadata (metadata file {})", path);
			return Collections.emptyMap();
		}
	}

	@Override
	public String lookup(LogEvent event, String key) {
		return metadata.getOrDefault(key, "");
	}
}
