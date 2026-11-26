package io.qameta.allure;

import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Для упавшего веб-теста прикладывает Playwright trace.zip
 * ({@code npx playwright show-trace path/to/trace.zip}).
 * Если браузера не было, сохраняет текстовый стек.
 */
public final class FailureTraceListener implements TestLifecycleListener {

    @Override
    public void afterTestStop(final TestResult result) {
        final Path traceZip = WebSteps.takeTraceZip();
        if (!isUnsuccessful(result.getStatus())) {
            deleteQuietly(traceZip);
            return;
        }
        final String trace = ensureTrace(result);
        fillSteps(result.getSteps(), trace);
        if (traceZip != null) {
            attachTraceZip(result, traceZip);
            deleteQuietly(traceZip);
        } else {
            attachTextTrace(result, trace);
        }
    }

    private static void attachTraceZip(final TestResult result, final Path traceZip) {
        final String source = UUID.randomUUID() + "-attachment.zip";
        final Path directory = resultsDirectory();
        try {
            Files.createDirectories(directory);
            Files.copy(traceZip, directory.resolve(source));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Playwright trace", exception);
        }
        result.getAttachments().add(new Attachment()
                .setName("trace.zip")
                .setType("application/zip")
                .setSource(source));
    }

    private static void attachTextTrace(final TestResult result, final String trace) {
        final String source = UUID.randomUUID() + "-attachment.txt";
        final Path directory = resultsDirectory();
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(source), trace.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write failure trace", exception);
        }
        result.getAttachments().add(new Attachment()
                .setName("trace")
                .setType("text/plain")
                .setSource(source));
    }

    private static Path resultsDirectory() {
        return Paths.get(System.getProperty("allure.results.directory", "build/allure-results"));
    }

    private static void deleteQuietly(final Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // temporary trace is no longer needed
        }
    }

    private static String ensureTrace(final TestResult result) {
        StatusDetails details = result.getStatusDetails();
        if (details == null) {
            details = new StatusDetails();
            result.setStatusDetails(details);
        }
        final String trace = details.getTrace();
        if (trace != null && !trace.trim().isEmpty()) {
            return trace;
        }
        final String message = details.getMessage();
        final String fallback = message == null || message.trim().isEmpty()
                ? result.getStatus().value()
                : message;
        details.setTrace(fallback);
        return fallback;
    }

    private static void fillSteps(final List<StepResult> steps, final String fallback) {
        if (steps == null) {
            return;
        }
        for (StepResult step : steps) {
            if (isUnsuccessful(step.getStatus())) {
                StatusDetails details = step.getStatusDetails();
                if (details == null) {
                    details = new StatusDetails();
                    step.setStatusDetails(details);
                }
                if (details.getTrace() == null || details.getTrace().trim().isEmpty()) {
                    details.setTrace(fallback);
                }
            }
            fillSteps(step.getSteps(), fallback);
        }
    }

    private static boolean isUnsuccessful(final Status status) {
        return status == Status.FAILED || status == Status.BROKEN;
    }
}
