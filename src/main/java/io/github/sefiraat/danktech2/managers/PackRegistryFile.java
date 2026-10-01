package io.github.sefiraat.danktech2.managers;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;

/** Strict reads and staged writes of the existing YAML format; never an empty recovery registry. */
final class PackRegistryFile {
    private PackRegistryFile() {}

    static YamlConfiguration load(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        return configuration;
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
