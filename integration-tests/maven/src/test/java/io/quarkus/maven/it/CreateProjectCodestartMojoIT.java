package io.quarkus.maven.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamLogger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.platform.tools.ToolsConstants;

@DisableForNative
public class CreateProjectCodestartMojoIT extends QuarkusPlatformAwareMojoTestBase {

    private static final Logger LOG = Logger.getLogger(CreateProjectCodestartMojoIT.class.getName());

    private File testDir;

    private static Stream<Arguments> provideLanguages() {
        return Stream.of("java", "kotlin")
                .flatMap(l -> Stream.of("", "resteasy", "qute").map(e -> Arguments.of(l, e)));
    }

    @ParameterizedTest
    @MethodSource("provideLanguages")
    public void generateMavenProject(String language, String extensions) throws Exception {
        final Path generatedProjectPath = generateProject("maven", language, extensions);
        checkDir(generatedProjectPath.resolve("src/main/" + language));
        Stream.of(extensions.split(","))
                .filter(s -> !s.isEmpty())
                .forEach(e -> check(generatedProjectPath.resolve("pom.xml"), e));
    }

    @ParameterizedTest
    @MethodSource("provideLanguages")
    public void generateGradleProject(String language, String extensions) throws Exception {
        final Path generatedProjectPath = generateProject("gradle", language, extensions);
        checkDir(generatedProjectPath.resolve("src/main/" + language));
        Stream.of(extensions.split(","))
                .forEach(e -> check(generatedProjectPath.resolve("build.gradle"), e));
    }

    private Path generateProject(String buildtool, String language, String extensions) throws Exception {
        String name = "project-" + buildtool + "-" + language;
        if (extensions.isEmpty()) {
            name += "-commandmode";
        } else {
            name += "-" + extensions.replace(",", "-");
        }
        testDir = prepareTestDir(name);
        LOG.info("creating project in " + testDir.toPath().toString());
        return runCreateCommand(buildtool, extensions + (!Objects.equals(language, "java") ? "," + language : ""));
    }

    private static File prepareTestDir(String name) {
        File tc = new File("target/codestart-test/" + name);
        if (tc.isDirectory()) {
            try {
                FileUtils.deleteDirectory(tc);
            } catch (IOException e) {
                throw new RuntimeException("Cannot delete directory: " + tc, e);
            }
        }
        boolean mkdirs = tc.mkdirs();
        LOG.log(Level.FINE, "codestart-test created? %s", mkdirs);
        return tc;
    }

    private Path runCreateCommand(String buildTool, String extensions)
            throws MavenInvocationException, FileNotFoundException, UnsupportedEncodingException {
        // Scaffold the new project
        assertThat(testDir).isDirectory();

        Properties properties = new Properties();
        properties.put("projectGroupId", "org.test");
        properties.put("projectArtifactId", "my-test-app");
        properties.put("codestartsEnabled", "true");
        properties.put("buildTool", buildTool);
        properties.put("extensions", extensions);

        InvocationResult result = executeCreate(properties);

        assertThat(result.getExitCode()).isZero();

        return testDir.toPath().resolve("my-test-app");
    }

    private InvocationResult executeCreate(Properties params)
            throws MavenInvocationException, FileNotFoundException, UnsupportedEncodingException {
        Invoker invoker = initInvoker(testDir);
        params.setProperty("platformGroupId", ToolsConstants.IO_QUARKUS);
        params.setProperty("platformArtifactId", "quarkus-bom");
        params.setProperty("platformVersion", getPluginVersion());

        InvocationRequest request = new DefaultInvocationRequest();
        request.setBatchMode(true);
        request.setGoals(Collections.singletonList(
                getPluginGroupId() + ":" + getPluginArtifactId() + ":" + getPluginVersion() + ":create"));
        request.setDebug(false);
        request.setShowErrors(false);
        request.setProperties(params);
        getEnv().forEach(request::addShellEnvironment);
        PrintStreamLogger logger = getPrintStreamLogger("create-codestart.log");
        invoker.setLogger(logger);
        return invoker.execute(request);
    }

    private PrintStreamLogger getPrintStreamLogger(String s) throws UnsupportedEncodingException, FileNotFoundException {
        File log = new File(testDir, s);
        return new PrintStreamLogger(new PrintStream(new FileOutputStream(log), false, "UTF-8"),
                InvokerLogger.DEBUG);
    }

    private void check(final Path resource, final String contentsToFind) {
        assertThat(resource).isRegularFile();
        try {
            assertThat(FileUtils.readFileToString(resource.toFile(), "UTF-8")).contains(contentsToFind);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void checkDir(final Path dir) throws IOException {
        assertThat(dir).isDirectory();
    }
}