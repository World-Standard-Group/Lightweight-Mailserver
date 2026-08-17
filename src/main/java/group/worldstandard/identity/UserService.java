package group.worldstandard.identity;

import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainRepository;
import group.worldstandard.security.PasswordHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserService {
    private final UserRepository userRepository;
    private final DomainRepository domainRepository;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, DomainRepository domainRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.domainRepository = domainRepository;
        this.passwordHasher = passwordHasher;
    }

    public User addUser(String email, String password, String quota) {
        String[] parts = email.split("@");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
        
        String domainName = parts[1].toLowerCase();
        Domain domain = domainRepository.findByName(domainName)
            .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainName));
        
        if (domain.status() != Domain.Status.ACTIVE) {
            throw new IllegalArgumentException("Domain is not active: " + domainName);
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User already exists: " + email);
        }
        
        String passwordHash = passwordHasher.hash(password);
        User user = User.create(domain.id(), email, passwordHash, quota);
        
        // Create maildir
        createMaildir(user.mailboxPath());
        
        return userRepository.save(user);
    }

    public void removeUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        
        // Remove maildir
        removeMaildir(user.mailboxPath());
        
        userRepository.deleteById(id);
    }

    public User disableUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        User updated = user.withStatus(User.Status.DISABLED);
        return userRepository.save(updated);
    }

    public User enableUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        User updated = user.withStatus(User.Status.ACTIVE);
        return userRepository.save(updated);
    }

    public User changePassword(UUID id, String newPassword) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        String passwordHash = passwordHasher.hash(newPassword);
        User updated = user.withPasswordHash(passwordHash);
        return userRepository.save(updated);
    }

    public User changePasswordByEmail(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        String passwordHash = passwordHasher.hash(newPassword);
        User updated = user.withPasswordHash(passwordHash);
        return userRepository.save(updated);
    }

    public Optional<User> getUser(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> listUsers(UUID domainId) {
        return userRepository.findByDomainId(domainId);
    }

    public List<User> listActiveUsers(UUID domainId) {
        return userRepository.findByDomainIdAndStatus(domainId, User.Status.ACTIVE);
    }

    public List<User> listAllUsers() {
        return userRepository.findAll();
    }

    public User recordLogin(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        User updated = user.withLastLogin();
        return userRepository.save(updated);
    }
    
    public UserRepository getUserRepository() {
        return userRepository;
    }

    private void createMaildir(String path) {
        try {
            Path maildir = Paths.get(path);
            Files.createDirectories(maildir.resolve("new"));
            Files.createDirectories(maildir.resolve("cur"));
            Files.createDirectories(maildir.resolve("tmp"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create maildir: " + path, e);
        }
    }

    private void removeMaildir(String path) {
        try {
            Path maildir = Paths.get(path);
            if (Files.exists(maildir)) {
                Files.walk(maildir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove maildir: " + path, e);
        }
    }
}