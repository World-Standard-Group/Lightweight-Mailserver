package group.worldstandard.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    public record Result(int exitCode, String stdout, String stderr) {}

    public enum Command {
        POSTFIX_CHECK("postfix", "check"),
        POSTFIX_RELOAD("postfix", "reload"),
        DOVECOT_RELOAD("doveadm", "reload"),
        RSPAMD_CONFIGTEST("rspamadm", "configtest"),
        RSPAMD_RELOAD("rspamadm", "reload"),
        SYSTEMD_RELOAD("systemctl", "daemon-reload"),
        SYSTEMD_START("systemctl", "start"),
        SYSTEMD_STOP("systemctl", "stop"),
        SYSTEMD_RESTART("systemctl", "restart"),
        SYSTEMD_ENABLE("systemctl", "enable"),
        SYSTEMD_DISABLE("systemctl", "disable"),
        SYSTEMD_STATUS("systemctl", "is-active"),
        SYSTEMD_ISENABLED("systemctl", "is-enabled");

        private final String executable;
        private final String[] baseArgs;

        Command(String executable, String... baseArgs) {
            this.executable = executable;
            this.baseArgs = baseArgs;
        }

        public String[] getBaseArgs() {
            return baseArgs;
        }

        public String getExecutable() {
            return executable;
        }
    }

    public Result run(Command command, String... additionalArgs) {
        List<String> args = new ArrayList<>();
        args.add(command.getExecutable());
        Collections.addAll(args, command.getBaseArgs());
        Collections.addAll(args, additionalArgs);
        return run(args.toArray(new String[0]), null, null, Duration.ofSeconds(30));
    }

    public Result run(Command command, Map<String, String> env, Path workingDir, String... additionalArgs) {
        List<String> args = new ArrayList<>();
        args.add(command.getExecutable());
        Collections.addAll(args, command.getBaseArgs());
        Collections.addAll(args, additionalArgs);
        return run(args.toArray(new String[0]), env, workingDir, Duration.ofSeconds(30));
    }

    public Result run(String[] command, Map<String, String> env, Path workingDir, Duration timeout) {
        log.debug("Running command: {}", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        if (env != null) {
            pb.environment().putAll(env);
        }
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Command timed out after " + timeout + ": " + String.join(" ", command));
            }
            
            int exitCode = process.exitValue();
            log.debug("Command exited with code: {}", exitCode);
            
            return new Result(exitCode, stdout, stderr);
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Command interrupted: " + String.join(" ", command), e);
        }
    }

    public Result run(String[] command) {
        return run(command, null, null, Duration.ofSeconds(30));
    }

    public Result runWithTimeout(String[] command, Duration timeout) {
        return run(command, null, null, timeout);
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public boolean checkCommand(Command command, String... additionalArgs) {
        Result result = run(command, additionalArgs);
        return result.exitCode == 0;
    }

    public void requireSuccess(Command command, String... additionalArgs) {
        Result result = run(command, additionalArgs);
        if (result.exitCode != 0) {
            throw new RuntimeException("Command failed: " + command + " " + String.join(" ", additionalArgs) +
                "\nExit code: " + result.exitCode +
                "\nStdout: " + result.stdout +
                "\nStderr: " + result.stderr);
        }
    }
}