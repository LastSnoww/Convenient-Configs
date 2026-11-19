package io.github.lastsnow.settingspresets.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores presets as one JSON file per preset in a directory. Pure Java (Gson + SLF4J only), so it
 * can be unit-tested with a temporary directory. Not thread-safe; use it from the render thread.
 */
public final class PresetStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("SettingsPresets");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String EXTENSION = ".json";
	private static final int MAX_SLUG_LENGTH = 64;
	private static final int MAX_BACKUPS = 5;
	private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9_-]+");
	private static final Pattern DASHES = Pattern.compile("-{2,}");
	private static final Set<String> WINDOWS_RESERVED = Set.of(
		"con", "prn", "aux", "nul",
		"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
		"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
	);
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

	private final Path directory;
	private final Supplier<Instant> clock;
	private List<Preset> cache;
	/** Files already reported as unreadable, so a broken file is logged once, not on every screen open. */
	private final Set<String> reportedBroken = new HashSet<>();

	public PresetStore(Path directory) {
		this(directory, Instant::now);
	}

	public PresetStore(Path directory, Supplier<Instant> clock) {
		this.directory = directory;
		this.clock = clock;
	}

	public Path directory() {
		return directory;
	}

	public Path backupDirectory() {
		return directory.resolve(".backup");
	}

	// ---------------------------------------------------------------- listing

	/** Cached list, newest first. Call {@link #refresh()} to rescan the folder. */
	public List<Preset> list() {
		if (cache == null) {
			refresh();
		}
		return cache;
	}

	/** Rescans the folder. Unreadable or corrupt files are logged and skipped. */
	public List<Preset> refresh() {
		List<Preset> presets = new ArrayList<>();
		if (Files.isDirectory(directory)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
				for (Path file : files) {
					if (!Files.isRegularFile(file)) {
						continue;
					}
					String key = file.getFileName() + "@" + lastModified(file);
					try {
						presets.add(read(file));
						reportedBroken.remove(key);
					} catch (Exception e) {
						if (reportedBroken.add(key)) {
							LOGGER.warn("Skipping unreadable preset file {}: {}", file.getFileName(), e.toString());
						}
					}
				}
			} catch (IOException e) {
				LOGGER.warn("Could not list presets in {}", directory, e);
			}
		}
		presets.sort(Comparator.comparing(Preset::modifiedAt).reversed().thenComparing(Preset::name, String.CASE_INSENSITIVE_ORDER));
		cache = List.copyOf(presets);
		return cache;
	}

	private static String lastModified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toString();
		} catch (IOException e) {
			return "?";
		}
	}

	public Optional<Preset> get(String id) {
		return list().stream().filter(p -> p.id().equals(id)).findFirst();
	}

	/** Case-insensitive lookup by display name, optionally ignoring one preset (the one being renamed). */
	public Optional<Preset> findByName(String name, String ignoreId) {
		String trimmed = name.trim();
		return list().stream()
			.filter(p -> !p.id().equals(ignoreId))
			.filter(p -> p.name().trim().equalsIgnoreCase(trimmed))
			.findFirst();
	}

	// ---------------------------------------------------------------- mutations

	public Preset create(String name, Set<OptionCategory> categories, Map<String, String> options, int gameDataVersion) throws IOException {
		Instant now = clock.get();
		String id = uniqueSlug(name);
		Preset preset = new Preset(id, name.trim(), now, now, gameDataVersion, categories, options);
		write(preset);
		refresh();
		return preset;
	}

	/** Replaces the options of an existing preset; its categories and creation date are kept. */
	public Preset updateOptions(Preset preset, Map<String, String> options, int gameDataVersion) throws IOException {
		Preset updated = preset.withOptions(options, gameDataVersion, clock.get());
		write(updated);
		refresh();
		return updated;
	}

	/** "Save as new" onto an existing name: replaces name, categories and options, keeps file and creation date. */
	public Preset overwrite(Preset existing, String name, Set<OptionCategory> categories, Map<String, String> options, int gameDataVersion) throws IOException {
		Preset replaced = new Preset(existing.id(), name.trim(), existing.createdAt(), clock.get(), gameDataVersion, categories, options);
		write(replaced);
		refresh();
		return replaced;
	}

	/** Changes the display name only; the file name stays the same. */
	public Preset rename(Preset preset, String newName) throws IOException {
		Preset renamed = preset.withName(newName.trim(), clock.get());
		write(renamed);
		refresh();
		return renamed;
	}

	public Preset duplicate(Preset preset, String newName) throws IOException {
		return create(newName, preset.categories(), preset.options(), preset.gameDataVersion());
	}

	public void delete(Preset preset) throws IOException {
		Files.deleteIfExists(fileFor(preset.id()));
		refresh();
	}

	/** Returns a name like "PvP (2)" that no other preset uses yet. */
	public String uniqueName(String base) {
		String trimmed = base.trim();
		if (findByName(trimmed, null).isEmpty()) {
			return trimmed;
		}
		for (int i = 2; ; i++) {
			String candidate = trimmed + " (" + i + ")";
			if (findByName(candidate, null).isEmpty()) {
				return candidate;
			}
		}
	}

	// ---------------------------------------------------------------- backups

	/** Copies {@code optionsFile} into {@code .backup/} and keeps only the newest five backups. */
	public Path backup(Path optionsFile) throws IOException {
		Path dir = backupDirectory();
		Files.createDirectories(dir);
		Path target = dir.resolve("options-" + BACKUP_STAMP.format(clock.get()) + ".txt");
		for (int i = 2; Files.exists(target); i++) {
			target = dir.resolve("options-" + BACKUP_STAMP.format(clock.get()) + "-" + i + ".txt");
		}
		Files.copy(optionsFile, target, StandardCopyOption.REPLACE_EXISTING);
		pruneBackups();
		return target;
	}

	private void pruneBackups() throws IOException {
		List<Path> backups;
		try (Stream<Path> files = Files.list(backupDirectory())) {
			backups = files
				.filter(p -> {
					String n = p.getFileName().toString();
					return n.startsWith("options-") && n.endsWith(".txt");
				})
				.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
				.toList();
		}
		for (int i = MAX_BACKUPS; i < backups.size(); i++) {
			Files.deleteIfExists(backups.get(i));
		}
	}

	// ---------------------------------------------------------------- slugs

	/** Lowercase {@code [a-z0-9_-]}, max 64 chars, never empty, never a Windows device name. */
	public static String slugify(String name) {
		String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
		String slug = NON_SLUG.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-");
		slug = DASHES.matcher(slug).replaceAll("-");
		slug = trimDashes(slug);
		if (slug.length() > MAX_SLUG_LENGTH) {
			slug = trimDashes(slug.substring(0, MAX_SLUG_LENGTH));
		}
		if (slug.isEmpty()) {
			slug = "preset";
		}
		if (WINDOWS_RESERVED.contains(slug)) {
			slug = slug + "_";
		}
		return slug;
	}

	String uniqueSlug(String name) {
		String base = slugify(name);
		Set<String> taken = new HashSet<>();
		for (Preset p : list()) {
			taken.add(p.id());
		}
		String candidate = base;
		for (int i = 2; taken.contains(candidate) || Files.exists(fileFor(candidate)); i++) {
			String suffix = "-" + i;
			String trimmedBase = base.length() + suffix.length() > MAX_SLUG_LENGTH
				? base.substring(0, MAX_SLUG_LENGTH - suffix.length())
				: base;
			candidate = trimmedBase + suffix;
		}
		return candidate;
	}

	private static String trimDashes(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && s.charAt(start) == '-') {
			start++;
		}
		while (end > start && s.charAt(end - 1) == '-') {
			end--;
		}
		return s.substring(start, end);
	}

	// ---------------------------------------------------------------- file format

	private Path fileFor(String id) {
		return directory.resolve(id + EXTENSION);
	}

	private void write(Preset preset) throws IOException {
		Files.createDirectories(directory);
		OptionsFile.writeAtomically(fileFor(preset.id()), toJson(preset) + "\n");
	}

	static String toJson(Preset preset) {
		JsonObject root = new JsonObject();
		root.addProperty("formatVersion", Preset.FORMAT_VERSION);
		root.addProperty("name", preset.name());
		root.addProperty("createdAt", preset.createdAt().toString());
		root.addProperty("modifiedAt", preset.modifiedAt().toString());
		root.addProperty("gameDataVersion", preset.gameDataVersion());
		JsonArray categories = new JsonArray();
		for (OptionCategory category : OptionCategory.values()) {
			if (preset.categories().contains(category)) {
				categories.add(category.name());
			}
		}
		root.add("categories", categories);
		JsonObject options = new JsonObject();
		preset.options().forEach(options::addProperty);
		root.add("options", options);
		return GSON.toJson(root);
	}

	private static Preset read(Path file) throws IOException {
		String fileName = file.getFileName().toString();
		String id = fileName.substring(0, fileName.length() - EXTENSION.length());
		return fromJson(id, Files.readString(file, StandardCharsets.UTF_8));
	}

	static Preset fromJson(String id, String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		int format = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 1;
		if (format > Preset.FORMAT_VERSION) {
			throw new IllegalArgumentException("unsupported formatVersion " + format);
		}
		String name = root.get("name").getAsString();
		Instant created = root.has("createdAt") ? Instant.parse(root.get("createdAt").getAsString()) : Instant.EPOCH;
		Instant modified = root.has("modifiedAt") ? Instant.parse(root.get("modifiedAt").getAsString()) : created;
		int dataVersion = root.has("gameDataVersion") ? root.get("gameDataVersion").getAsInt() : 0;

		Set<OptionCategory> categories = EnumSet.noneOf(OptionCategory.class);
		if (root.has("categories")) {
			for (JsonElement element : root.getAsJsonArray("categories")) {
				try {
					categories.add(OptionCategory.valueOf(element.getAsString()));
				} catch (IllegalArgumentException e) {
					LOGGER.warn("Preset {} has unknown category {}, ignoring it", id, element);
				}
			}
		}

		LinkedHashMap<String, String> options = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("options").entrySet()) {
			// Values are stored as raw strings; tolerate hand-edited numbers/booleans too.
			JsonElement value = entry.getValue();
			options.put(entry.getKey(), value.isJsonPrimitive() ? value.getAsString() : value.toString());
		}
		if (categories.isEmpty()) {
			// Hand-written file without categories: derive them from its keys.
			options.keySet().forEach(key -> categories.add(OptionCategory.categorize(key)));
		}
		return new Preset(id, name, created, modified, dataVersion, categories, options);
	}
}
