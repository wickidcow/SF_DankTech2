package io.github.sefiraat.danktech2.managers;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.inventory.ItemStack;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict reads and staged writes of the existing YAML format; never an empty recovery registry. */
final class PackRegistryFile {
    private static final String LEGACY_ITEM_ALIAS =
        "io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack";

    private PackRegistryFile() {}

    static YamlConfiguration load(Path file) throws IOException, InvalidConfigurationException {
        String contents = Files.readString(file, StandardCharsets.UTF_8);
        YamlConfiguration configuration = new YamlConfiguration();
        // Match Bukkit's registry-size limits rather than SnakeYAML's smaller defaults.
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(Integer.MAX_VALUE);
        options.setCodePointLimit(configuration.options().codePointLimit());
        options.setNestingDepthLimit(100);
        options.setProcessComments(configuration.options().parseComments());
        DumperOptions output = new DumperOptions();
        output.setProcessComments(configuration.options().parseComments());
        StrictRegistryConstructor constructor = new StrictRegistryConstructor(options);
        Yaml yaml = new Yaml(constructor, new Representer(output), output, options);
        try {
            Node root = yaml.compose(new StringReader(contents));
            if (root == null || Tag.COMMENT.equals(root.getTag())) {
                configuration.loadFromString(contents);
                return configuration;
            }
            if (!(root instanceof MappingNode registry)) {
                throw new InvalidConfigurationException("Pack registry root must be a map.");
            }

            Map<String, Node> items = new LinkedHashMap<>();
            boolean recovered = recoverItemAliases(registry, constructor, items);
            // Bukkit can log a deserializer failure and return null. Validate every
            // serialized value before accepting a configuration that could later be saved.
            constructor.construct(registry);
            for (var entry : items.entrySet()) {
                if (!(constructor.construct(entry.getValue()) instanceof ItemStack)) {
                    throw unreadableItem(entry.getKey());
                }
            }
            if (recovered) {
                StringWriter normalized = new StringWriter();
                yaml.serialize(registry, normalized);
                contents = normalized.toString();
            }
            configuration.loadFromString(contents);
            for (String id : items.keySet()) {
                if (configuration.getItemStack(id + ".item") == null) {
                    throw unreadableItem(id);
                }
            }
        } catch (RuntimeException failure) {
            throw new InvalidConfigurationException("Unable to read the complete pack registry.", failure);
        }
        return configuration;
    }

    private static boolean recoverItemAliases(MappingNode registry, YamlConstructor constructor,
                                              Map<String, Node> items) {
        boolean recovered = false;
        constructor.flattenMapping(registry);
        for (NodeTuple entry : registry.getValue()) {
            if (!(entry.getValueNode() instanceof MappingNode record)) {
                continue;
            }
            String id = String.valueOf(constructor.construct(entry.getKeyNode()));
            if (!isPackId(id)) {
                continue;
            }
            constructor.flattenMapping(record);
            for (NodeTuple field : record.getValue()) {
                if (!isKey(field.getKeyNode(), "item")) {
                    continue;
                }
                Node item = field.getValueNode();
                items.put(id, item);
                if (!(item instanceof MappingNode serialized)) {
                    continue;
                }
                constructor.flattenMapping(serialized);
                for (int index = 0; index < serialized.getValue().size(); index++) {
                    NodeTuple value = serialized.getValue().get(index);
                    if (isKey(value.getKeyNode(), "==")
                        && isKey(value.getValueNode(), LEGACY_ITEM_ALIAS)) {
                        ScalarNode old = (ScalarNode) value.getValueNode();
                        ScalarNode alias = new ScalarNode(Tag.STR, ItemStack.class.getName(),
                            old.getStartMark(), old.getEndMark(), old.getScalarStyle());
                        alias.setBlockComments(old.getBlockComments());
                        alias.setInLineComments(old.getInLineComments());
                        alias.setEndComments(old.getEndComments());
                        serialized.getValue().set(index, new NodeTuple(value.getKeyNode(), alias));
                        recovered = true;
                    }
                }
            }
        }
        return recovered;
    }

    private static boolean isKey(Node node, String value) {
        return node instanceof ScalarNode scalar
            && Tag.STR.equals(scalar.getTag()) && value.equals(scalar.getValue());
    }

    private static boolean isPackId(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static InvalidConfigurationException unreadableItem(String id) {
        return new InvalidConfigurationException("Unable to read the item for pack record " + id + ".");
    }

    private static final class StrictRegistryConstructor extends YamlConstructor {
        private StrictRegistryConstructor(LoaderOptions options) {
            super(options);
        }

        @Override
        protected Object constructObject(Node node) {
            Object value = super.constructObject(node);
            if (value == null && !Tag.NULL.equals(node.getTag())) {
                throw new YAMLException("A serialized registry value could not be read.");
            }
            return value;
        }
    }

    static void save(FileConfiguration configuration, Path file) throws IOException {
        // Serialization must complete before opening any output or touching the previous file.
        byte[] contents = configuration.saveToString().getBytes(StandardCharsets.UTF_8);
        Path target = file.toAbsolutePath().normalize();
        // Keep an owner's existing file symlink, writing to its actual target instead.
        if (Files.isSymbolicLink(target)) {
            target = target.toRealPath();
        }
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(contents);
                while (bytes.hasRemaining()) {
                    output.write(bytes);
                }
                output.force(true);
            }
            if (Files.isRegularFile(target)
                    && Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(staged, Files.getPosixFilePermissions(target));
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Only unsupported atomic replacement permits the normal move fallback.
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
