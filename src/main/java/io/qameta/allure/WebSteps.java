package io.qameta.allure;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Attachment;
import io.qameta.allure.Step;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public class WebSteps {

    private static final ThreadLocal<TraceSession> SESSION = new ThreadLocal<TraceSession>();

    /**
     * Забирает trace.zip текущей сессии. Файл открывается так:
     * {@code npx playwright show-trace path/to/trace.zip}.
     */
    public static Path takeTraceZip() {
        final TraceSession session = SESSION.get();
        SESSION.remove();
        return session == null ? null : session.traceZip;
    }

    @Step("Starting web driver")
    public void startDriver() {
        startBrowser();
        renderPage();
        maybeThrowSeleniumTimeoutException();
    }

    @Step("Stopping web driver")
    public void stopDriver() {
        try {
            maybeThrowSeleniumTimeoutException();
        } finally {
            saveTraceAndClose();
        }
    }

    @Step("Open issues page `{owner}/{repo}`")
    public void openIssuesPage(final String owner, final String repo) {
        renderPage();
        attachPageSource();
        maybeThrowElementNotFoundException();
    }

    @Step("Open pull requests page `{owner}/{repo}`")
    public void openPullRequestsPage(final String owner, final String repo) {
        renderPage();
        attachPageSource();
        maybeThrowElementNotFoundException();
    }

    @Step("Create pull request from branch `{branch}`")
    public void createPullRequestFromBranch(final String branch) {
        maybeThrowElementNotFoundException();
    }

    @Step("Create issue with title `{title}`")
    public void createIssueWithTitle(String title) {
        maybeThrowAssertionException(title);
    }

    @Step("Close pull request for branch `{branch}`")
    public void closePullRequestForBranch(final String branch) {
        maybeThrowAssertionException(branch);
    }

    @Step("Close issue with title `{title}`")
    public void closeIssueWithTitle(final String title) {
        maybeThrowAssertionException(title);
    }

    @Step("Check pull request for branch `{branch}` exists")
    public void shouldSeePullRequestForBranch(final String branch) {
        maybeThrowAssertionException(branch);
    }

    @Step("Check issue with title `{title}` exists")
    public void shouldSeeIssueWithTitle(final String title) {
        maybeThrowAssertionException(title);
    }

    @Step("Check pull request for branch `{branch}` not exists")
    public void shouldNotSeePullRequestForBranch(final String branch) {
        maybeThrowAssertionException(branch);
    }

    @Step("Check issue with title `{title}` not exists")
    public void shouldNotSeeIssueWithTitle(final String title) {
        maybeThrowAssertionException(title);
    }

    @Attachment(value = "Page", type = "text/html", fileExtension = "html")
    public byte[] attachPageSource() {
        return readPageBytes();
    }

    private static final class TraceSession {
        private Playwright playwright;
        private Browser browser;
        private BrowserContext context;
        private Page page;
        private Path traceZip;
    }

    private void startBrowser() {
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            context = browser.newContext();
            context.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true));
            final Page page = context.newPage();
            final TraceSession session = new TraceSession();
            session.playwright = playwright;
            session.browser = browser;
            session.context = context;
            session.page = page;
            SESSION.set(session);
        } catch (RuntimeException exception) {
            closeQuietly(context, browser, playwright);
            throw exception;
        }
    }

    private void renderPage() {
        final TraceSession session = SESSION.get();
        if (session == null || session.page == null) {
            return;
        }
        session.page.setContent(new String(readPageBytes(), Charset.forName("UTF-8")));
        session.page.locator("h1").textContent();
    }

    private void saveTraceAndClose() {
        final TraceSession session = SESSION.get();
        if (session == null || session.context == null) {
            return;
        }
        try {
            final Path zip = Files.createTempFile("playwright-trace-", ".zip");
            session.context.tracing().stop(new Tracing.StopOptions().setPath(zip));
            session.traceZip = zip;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Playwright trace", exception);
        } finally {
            closeQuietly(session.context, session.browser, session.playwright);
            session.context = null;
            session.browser = null;
            session.playwright = null;
            session.page = null;
        }
    }

    private static void closeQuietly(final BrowserContext context, final Browser browser, final Playwright playwright) {
        try {
            if (context != null) {
                context.close();
            }
        } catch (RuntimeException ignored) {
            // browser is already closing
        }
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (RuntimeException ignored) {
            // browser is already closing
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private byte[] readPageBytes() {
        try {
            final InputStream stream = ClassLoader.getSystemResourceAsStream("index.html");
            return IOUtils.toString(stream, Charset.forName("UTF-8")).getBytes();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void maybeThrowSeleniumTimeoutException() {
        if (isTimeToThrowException()) {
            fail(webDriverIsNotReachable("Allure"));
        }
    }

    private void maybeThrowElementNotFoundException() {
        try {
            Thread.sleep(1000);
            if (isTimeToThrowException()) {
                fail(elementNotFoundMessage("[//div[@class='something']]"));
            }
        } catch (InterruptedException e) {
            //do nothing, it's dummy test
        }
    }

    private void maybeThrowAssertionException(String text) {
        if (isTimeToThrowException()) {
            fail(textEqual(text, "another text"));
        }
    }

    private boolean isTimeToThrowException() {
        return new Random().nextBoolean()
                && new Random().nextBoolean()
                && new Random().nextBoolean()
                && new Random().nextBoolean();
    }

    private String webDriverIsNotReachable(final String text) {
        return String.format("WebDriverException: chrome not reachable\n" +
                "Element not found {By.xpath: //a[@href='/eroshenkoam/allure-example']}\n" +
                "Expected: text '%s'\n" +
                "Page source: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973861960.0.html\n" +
                "Timeout: 4 s.", text);
    }

    private String textEqual(final String expected, final String actual) {
        return String.format("Element should text '%s' {By.xpath: //a[@href='/eroshenkoam/allure-example']}\n" +
                "Element: '<a class=\"v-align-middle\">%s</a>'\n" +
                "Screenshot: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973703632.0.png\n" +
                "Page source: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973703632.0.html\n" +
                "Timeout: 4 s.\n", expected, actual);
    }

    private String elementNotFoundMessage(String selector) {
        return String.format("Element not found {By.xpath: %s}\n" +
                "Expected: visible or transparent: visible or have css value opacity=0\n" +
                "Screenshot: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973516437.0.png\n" +
                "Page source: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973516437.0.html\n" +
                "Timeout: 4 s.\n", selector);
    }
}
