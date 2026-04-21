package io.qameta.allure;

import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.util.AnnotationUtils;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Проставляет {@link StatusDetails#setFlaky(boolean)} для текущего теста, если на методе или классе
 * есть {@link Flaky}. Стандартный allure-junit-platform это для JUnit 5 не делает.
 */
public final class FlakyLabelExtension implements BeforeTestExecutionCallback {

    @Override
    public void beforeTestExecution(final ExtensionContext context) {
        boolean flaky = context.getTestMethod().map(AnnotationUtils::isFlaky).orElse(false);
        if (!flaky) {
            flaky = AnnotationUtils.isFlaky(context.getRequiredTestClass());
        }
        if (!flaky) {
            return;
        }
        Allure.getLifecycle().updateTestCase(tr -> {
            StatusDetails sd = tr.getStatusDetails();
            if (sd == null) {
                tr.setStatusDetails(new StatusDetails().setFlaky(true));
            } else {
                sd.setFlaky(true);
            }
        });
    }
}
