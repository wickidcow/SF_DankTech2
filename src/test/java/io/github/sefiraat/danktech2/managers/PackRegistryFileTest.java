package io.github.sefiraat.danktech2.managers;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackRegistryFileTest {
    @TempDir
    Path directory;

    private Path registry() {
        return directory.resolve("dank_packs.yml");
    }

    @Test
    void validEmptyRegistryRemainsValidAndReadOnly() throws Exception {
        byte[] original = "# Fresh registry\n".getBytes(StandardCharsets.UTF_8);
        Files.write(registry(), original);
        assertTrue(PackRegistryFile.load(registry()).getKeys(false).isEmpty());
        assertArrayEquals(original, Files.readAllBytes(registry()));
    }

    @Test
    void corruptYamlDoesNotBecomeAnEmptyRegistryOrChangeOriginalBytes() throws Exception {
        byte[] original = "'9223372036854775807': [invalid\n".getBytes(StandardCharsets.UTF_8);
        Files.write(registry(), original);
        assertThrows(InvalidConfigurationException.class, () -> PackRegistryFile.load(registry()));
        assertArrayEquals(original, Files.readAllBytes(registry()));
    }

    @Test
    void partialYamlBeforeTheErrorIsNeverReturnedAsUsableRegistry() throws Exception {
        Files.writeString(registry(), "'42':\n  last_user: Existing\ninvalid: [\n");
        assertThrows(InvalidConfigurationException.class, () -> PackRegistryFile.load(registry()));
        assertTrue(Files.readString(registry()).contains("Existing"));
    }

    @Test
    void missingDirectoryAndInvalidUtf8FailWithoutCreatingReplacement() throws Exception {
        assertThrows(IOException.class, () -> PackRegistryFile.load(registry()));
        assertFalse(Files.exists(registry()));
        assertThrows(IOException.class, () -> PackRegistryFile.load(directory));
        byte[] original = {(byte) 0xff, (byte) 0xfe};
        Files.write(registry(), original);
        assertThrows(IOException.class, () -> PackRegistryFile.load(registry()));
        assertArrayEquals(original, Files.readAllBytes(registry()));
    }

    @Test
    void preservesLongIdentityOwnerAndUnknownDataAcrossRepeatedSaveAndReopen() throws Exception {
        Files.writeString(registry(), "'9223372036854775807':\n  last_user: ExactOwner\n  unknown: keep\n"
            + "  amounts: [2147483647, 0, 37]\n'9007199254740993':\n  last_user: OtherOwner\n");
        for (int cycle = 0; cycle < 3; cycle++) {
            var data = PackRegistryFile.load(registry());
            assertEquals("ExactOwner", data.getString("9223372036854775807.last_user"));
            assertEquals("OtherOwner", data.getString("9007199254740993.last_user"));
            assertEquals("keep", data.getString("9223372036854775807.unknown"));
            assertEquals(List.of(2147483647, 0, 37), data.getIntegerList("9223372036854775807.amounts"));
            assertEquals(2, data.getKeys(false).size());
            PackRegistryFile.save(data, registry());
        }
        assertNoTemporaryFiles();
    }

    @Test
    void serializationFailureCannotTruncateThePreviousRegistry() throws Exception {
        byte[] original = "'42':\n  last_user: Existing\n".getBytes(StandardCharsets.UTF_8);
        Files.write(registry(), original);
        YamlConfiguration broken = new YamlConfiguration() {
            @Override
            public String saveToString() {
                throw new IllegalStateException("Synthetic serialization failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> PackRegistryFile.save(broken, registry()));
        assertArrayEquals(original, Files.readAllBytes(registry()));
        assertNoTemporaryFiles();
    }

    @Test
    void replacesOnlyTheRequestedRegistryAfterCompleteSerialization() throws Exception {
        Files.writeString(registry(), "old: data\n");
        var unrelated = directory.resolve("other.yml");
        Files.writeString(unrelated, "leave unchanged\n");
        var data = new YamlConfiguration();
        data.set("42.last_user", "Owner");
        PackRegistryFile.save(data, registry());
        assertFalse(PackRegistryFile.load(registry()).contains("old"));
        assertEquals("Owner", PackRegistryFile.load(registry()).getString("42.last_user"));
        assertEquals("leave unchanged\n", Files.readString(unrelated));
        assertNoTemporaryFiles();
    }

    @Test
    void failedReplacementLeavesTheDestinationAndCleansTheStage() throws Exception {
        Files.createDirectory(registry());
        var child = registry().resolve("do-not-delete");
        Files.writeString(child, "preserve");
        assertThrows(IOException.class, () -> PackRegistryFile.save(new YamlConfiguration(), registry()));
        assertEquals("preserve", Files.readString(child));
        assertNoTemporaryFiles();
    }

    @Test
    void fileSymlinkStillPointsToTheSameStorageTarget() throws Exception {
        var target = directory.resolve("real.yml");
        Files.writeString(target, "'42':\n  last_user: Before\n");
        Files.createSymbolicLink(registry(), target.getFileName());
        var data = PackRegistryFile.load(registry());
        data.set("42.last_user", "After");
        PackRegistryFile.save(data, registry());
        assertTrue(Files.isSymbolicLink(registry()));
        assertEquals(target.getFileName(), Files.readSymbolicLink(registry()));
        assertEquals("After", PackRegistryFile.load(target).getString("42.last_user"));
        assertNoTemporaryFiles();
    }

    @Test
    void danglingSymlinkIsNotReplacedWithAnEmptyFile() throws Exception {
        var target = directory.resolve("missing.yml");
        Files.createSymbolicLink(registry(), target.getFileName());
        assertThrows(IOException.class, () -> PackRegistryFile.load(registry()));
        assertThrows(IOException.class, () -> PackRegistryFile.save(new YamlConfiguration(), registry()));
        assertTrue(Files.isSymbolicLink(registry()));
        assertFalse(Files.exists(target));
        assertNoTemporaryFiles();
    }

    @Test
    void existingPosixPermissionsArePreserved() throws Exception {
        Files.writeString(registry(), "'42': {}\n");
        var expected = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(registry(), expected);
        PackRegistryFile.save(PackRegistryFile.load(registry()), registry());
        assertEquals(expected, Files.getPosixFilePermissions(registry()));
    }

    @Test
    void firstSaveCreatesParentsWithoutRequiringExistingData() throws Exception {
        var file = directory.resolve("new/nested/dank_packs.yml");
        var data = new YamlConfiguration();
        data.set("42.last_user", "FirstOwner");
        PackRegistryFile.save(data, file);
        assertEquals("FirstOwner", PackRegistryFile.load(file).getString("42.last_user"));
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
