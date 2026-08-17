package group.worldstandard.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Set;

public class FileManager {
    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

    private static final Set<PosixFilePermission> DIR_PERMS_700 = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> DIR_PERMS_750 = PosixFilePermissions.fromString("rwxr-x---");
    private static final Set<PosixFilePermission> FILE_PERMS_600 = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> FILE_PERMS_640 = PosixFilePermissions.fromString("rw-r-----");

    public void writeFile(Path path, String content, boolean atomic) throws IOException {
        if (atomic) {
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, content);
            setPermissions(temp, FILE_PERMS_600);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.writeString(path, content);
            setPermissions(path, FILE_PERMS_600);
        }
        log.debug("Wrote file: {}", path);
    }

    public void writeFile(Path path, String content) throws IOException {
        writeFile(path, content, true);
    }

    public String readFile(Path path) throws IOException {
        return Files.readString(path);
    }

    public void createDirectory(Path path, String permissions) throws IOException {
        Files.createDirectories(path);
        setPermissions(path, PosixFilePermissions.fromString(permissions));
        log.debug("Created directory: {} with permissions {}", path, permissions);
    }

    public void createDirectory(Path path) throws IOException {
        createDirectory(path, "rwx------");
    }

    public void setPermissions(Path path, String permissions) throws IOException {
        setPermissions(path, PosixFilePermissions.fromString(permissions));
    }

    public void setPermissions(Path path, Set<PosixFilePermission> perms) {
        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            log.warn("POSIX permissions not supported on this filesystem: {}", path);
        } catch (IOException e) {
            log.warn("Failed to set permissions on {}: {}", path, e.getMessage());
        }
    }

    public void setOwner(Path path, String user, String group) {
        try {
            Path ownerPath = path.getFileSystem().getPath("/etc/passwd"); // dummy to trigger UserPrincipalLookupService
            // Java doesn't have built-in chown, would need native or sudo
            log.debug("Would set owner of {} to {}:{}", path, user, group);
        } catch (Exception e) {
            log.warn("Failed to set owner on {}: {}", path, e.getMessage());
        }
    }

    public void atomicReplace(Path source, Path target) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".new");
        Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Atomically replaced {} with {}", target, source);
    }

    public void backupFile(Path path) throws IOException {
        if (Files.exists(path)) {
            Path backup = path.resolveSibling(path.getFileName() + ".bak." + System.currentTimeMillis());
            Files.copy(path, backup);
            log.debug("Backed up {} to {}", path, backup);
        }
    }

    public void deleteFile(Path path) throws IOException {
        Files.deleteIfExists(path);
        log.debug("Deleted file: {}", path);
    }

    public void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to delete: " + p, e);
                    }
                });
            log.debug("Deleted directory: {}", path);
        }
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }

    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    public long getFileSize(Path path) throws IOException {
        return Files.size(path);
    }

    public void createMaildir(Path base, String domain, String user) throws IOException {
        Path maildir = base.resolve(domain).resolve(user);
        Files.createDirectories(maildir.resolve("new"));
        Files.createDirectories(maildir.resolve("cur"));
        Files.createDirectories(maildir.resolve("tmp"));
        setPermissions(maildir, DIR_PERMS_700);
        setPermissions(maildir.resolve("new"), DIR_PERMS_700);
        setPermissions(maildir.resolve("cur"), DIR_PERMS_700);
        setPermissions(maildir.resolve("tmp"), DIR_PERMS_700);
        log.debug("Created maildir: {}", maildir);
    }
}