package group.worldstandard.mail;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;

/**
 * The uid/gid that owns every mailbox on disk, and the helper that applies it.
 *
 * <p>Three components have to agree on it, and they run in different
 * containers:</p>
 * <ul>
 *   <li>Postfix's virtual delivery agent, via
 *       {@code virtual_uid_maps = static:<uid>} and
 *       {@code virtual_gid_maps = static:<gid>};</li>
 *   <li>Dovecot, via {@code mail_uid} / {@code mail_gid};</li>
 *   <li>mailctl, which creates the maildir and has to give it to that
 *       uid.</li>
 * </ul>
 *
 * <p>uid 8 is the Debian {@code mail} account. It is used numerically rather
 * than by name so that the value does not depend on which users the image
 * happens to define - the Alpine-based Postfix image and the Debian-based
 * Dovecot image disagree about the <em>group</em> id of the {@code mail}
 * user, and a numeric uid/gid pair is identical on both sides of the shared
 * {@code mail_data} volume.</p>
 *
 * <p>Dovecot's {@code first_valid_uid} must be at or below this uid:
 * Dovecot refuses to open a mailbox whose owner is below the configured
 * range, and it applies that check to the globally configured
 * {@code mail_uid} as well as to a per-user uid returned by the
 * userdb.</p>
 */
public final class MailOwner {
    private MailOwner() {}

    /** Numeric uid of the mailbox owner. */
    public static final int UID = 8;

    /** Numeric gid of the mailbox owner. */
    public static final int GID = 8;

    /** The account name, used where a name is required. */
    public static final String USER = "mail";

    /** The group name, used where a name is required. */
    public static final String GROUP = "mail";

    /** Directory mode for a maildir and its subdirectories. */
    public static final Set<PosixFilePermission> DIR_PERMS =
            PosixFilePermissions.fromString("rwx------");

    /**
     * Gives a maildir tree to the mailbox owner, recursively.
     *
     * <p>mailctl runs as root in its container, so the chown succeeds there.
     * The numeric ids are what matter across the shared volume: the files
     * belong to uid {@value #UID}:gid {@value #GID} on the host, and both
     * Postfix's delivery agent and Dovecot's mail processes use exactly
     * those numbers.</p>
     *
     * <p>Best effort by design. On a host without POSIX ownership (Windows)
     * or without the {@code mail} account the call reports failure and the
     * caller logs it rather than aborting: the maildir is still created and
     * still usable by a server that runs as the same user.</p>
     *
     * @param path the maildir to take ownership of
     * @return true when ownership was applied to the whole tree
     */
    public static boolean applyOwnership(Path path) {
        try {
            UserPrincipalLookupService lookup =
                    path.getFileSystem().getUserPrincipalLookupService();
            UserPrincipal user = lookup.lookupPrincipalByName(USER);
            GroupPrincipal group = lookup.lookupPrincipalByGroupName(GROUP);

            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return chown(dir, user, group);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    return chown(file, user, group);
                }

                private FileVisitResult chown(Path p, UserPrincipal u, GroupPrincipal g) {
                    try {
                        Files.setOwner(p, u);
                        Files.setGroup(p, g);
                    } catch (IOException | UnsupportedOperationException | SecurityException e) {
                        // Fall through: the directory is still usable, and the
                        // caller logs the outcome of the whole operation.
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Restricts a directory to the owner. Failure is not fatal: some
     * filesystems (and Windows) have no POSIX modes.
     */
    public static void applyOwnerOnlyPermissions(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, DIR_PERMS);
        } catch (IOException | UnsupportedOperationException e) {
            // No POSIX permissions here; nothing to enforce.
        }
    }
}
