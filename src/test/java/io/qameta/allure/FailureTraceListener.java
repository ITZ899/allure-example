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
 * Для упавшего или сломанного теста сохраняет стек в {@code statusDetails.trace}
 * и прикладывает его вложением {@code trace}.
 */
public final class FailureTraceListener implements TestLifecycleListener {

    @Override
    public void afterTestStop(final TestResult result) {
        if (!isUnsuccessful(result.getStatus())) {
            return;
        }
        final String trace = ensureTrace(result);
        fillSteps(result.getSteps(), trace);
        attachTrace(result, trace);
    }

    private static void attachTrace(final TestResult result, final String trace) {
        final String source = UUID.randomUUID() + "-attachment.txt";
        final Path directory = Paths.get(System.getProperty("allure.results.directory", "build/allure-results"));
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(source), trace.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write failure trace", e);
        }
        result.getAttachments().add(new Attachment()
                .setName("trace")
                .setType("text/plain")
                .setSource(source));
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
