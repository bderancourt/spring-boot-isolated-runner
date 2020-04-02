package com.github.bderancourt.springboot.isolatedrunner.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.jar.JarFile;

import com.github.bderancourt.springboot.isolatedrunner.util.ClassPathUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DirDependency implements Dependency {

  protected static final Pattern JAR_WITH_VERSION_PATTERN = Pattern.compile("(.+(?=\\-\\d))\\-(.+(?=\\.jar))(\\.jar)");

  private URL classPathDependencyUrl;

  private String name;

  private String mainClass;

  private Class<?> runnerClass;

  private Object runnerInstance;

  public DirDependency(URL classPathDependencyUrl, String name, String mainClass) {
    this.classPathDependencyUrl = classPathDependencyUrl;
    this.name = name;
    this.mainClass = mainClass;
  }

  /**
   * @param args
   *          to pass to the spring-boot app
   * @throws Exception
   *           if an error occurs during the spring-boot app startup
   */
  public void start(String[] args) throws Exception {

    Path manifestPath = Paths.get(new File(classPathDependencyUrl.toURI()).toString(), "META-INF", "MANIFEST.MF");

    Manifest manifest;
    try (InputStream is = new FileInputStream(manifestPath.toFile())) {
      manifest = new Manifest(is);
    }

    URL[] classPathUrls = constructClassPath(manifest.getMainAttributes()
        .getValue(MANIFEST_CLASSPATH), classPathDependencyUrl);

    log.debug("Loaded isolated classpath for " + name);
    Arrays.stream(classPathUrls)
        .map(Objects::toString)
        .forEach(log::debug);

    JarFile.registerUrlProtocolHandler();
    ClassLoader classLoader = createClassLoader(classPathUrls);

    runnerClass = classLoader.loadClass(RUNNER_CLASS);
    Class<?> configClass = classLoader.loadClass(mainClass);

    Object runner = runnerClass.getDeclaredConstructor(Class.class, String[].class, String.class)
        .newInstance(configClass, args, name);
    runnerClass.getMethod("run")
        .invoke(runner);
  }

  public void stop() throws Exception {
    runnerClass.getMethod("stop")
        .invoke(runnerInstance);
  }

  /**
   * Create a classloader for the specified URLs.
   * 
   * @param urls
   *          the URLs
   * @return the classloader
   * @throws Exception
   *           if the classloader cannot be created
   */
  protected ClassLoader createClassLoader(URL[] urls) throws Exception {
    return new LaunchedURLClassLoader(urls, null);
  }

  /**
   * Constructs the spring-boot app classpath. Based on the jar list on the spring-boot app manifest, we need to match
   * which URL on this application classpath (JVM) is relevant.
   * 
   * @param manifestClassPath
   *          the list of jars defined in the Class-Path key of the manifest
   * @param classPathDependencyUrl
   *          the URL to the spring boot app to run
   * @return spring-boot app classpath
   * @throws Exception
   *           if an URL don't exists
   */
  protected URL[] constructClassPath(String manifestClassPath, URL classPathDependencyUrl) throws Exception {
    URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

    List<URL> urls = new ArrayList<>();

    // We add the spring-boot program itself in the classpath
    urls.add(classPathDependencyUrl);

    urls.add(ClassPathUtils.findDependencyURL("spring-boot-isolated-runner"));
    urls.add(ClassPathUtils.findDependencyURL("org/springframework/boot/spring-boot/"));

    // In this list, we store all this JVM classpath.
    List<URL> classPathUrls = new ArrayList<>();
    classPathUrls.addAll(Arrays.asList(systemClassLoader.getURLs()));

    // In this list, we store the jars found in the spring-boot app manifest.
    List<String> manifestJars = Collections.synchronizedList(new ArrayList<>());
    manifestJars.addAll(Arrays.asList(manifestClassPath.split(" ")));
    log.debug("##### spring-boot dependencies to find #####");
    manifestJars.stream().forEach(log::debug);
    log.debug("##### spring-boot dependencies to find #####");

    // First try, find the exact matching between the jar and the program classpath URL
    // Ex: file:/C:/m2repo/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar matching jsr305-3.0.2.jar
    log.debug("exact match");
    if (!manifestJars.isEmpty()) {
      for (ListIterator<URL> itUrls = classPathUrls.listIterator(); itUrls.hasNext();) {
        URL url = itUrls.next();
        for (ListIterator<String> it = manifestJars.listIterator(); it.hasNext();) {
          String jar = it.next();
          URL matchingUrl = exactMatch(url, jar);
          if (matchingUrl != null) {
            log.debug("adding url {} matching {}", matchingUrl, jar);
            urls.add(matchingUrl);
            it.remove();
            itUrls.remove();
          }
        }
      }
    }

    // Second try. For dependency management reasons, we potentially have jars in the manifest
    // that are not exactly on the same version as in the program classpath URLs.
    // Initialize regex pattern
    log.debug("version not match");
    if (!manifestJars.isEmpty()) {
      for (ListIterator<URL> itUrls = classPathUrls.listIterator(); itUrls.hasNext();) {
        URL url = itUrls.next();
        for (ListIterator<String> it = manifestJars.listIterator(); it.hasNext();) {
          String jar = it.next();
          URL matchingUrl = matchesButNotTheVersion(url, jar);
          if (matchingUrl != null) {
            log.debug("adding url {} matching {}", matchingUrl, jar);
            urls.add(matchingUrl);
            it.remove();
            itUrls.remove();
          }
        }
      }
    }

    // Third try. If you ran your program in eclipse, eclipse put in classpath the related projects target/classes dirs
    log.debug("eclipse's related projects");
    if (!manifestJars.isEmpty()) {
      for (ListIterator<URL> itUrls = classPathUrls.listIterator(); itUrls.hasNext();) {
        URL url = itUrls.next();
        for (ListIterator<String> it = manifestJars.listIterator(); it.hasNext();) {
          String jar = it.next();

          URL matchingUrl = matchesEclipseRelatedProject(url, jar);
          if (matchingUrl != null) {
            log.debug("adding url {} matching {}", matchingUrl, jar);
            urls.add(matchingUrl);
            it.remove();
            itUrls.remove();
          }
        }
      }
    }

    // Fourth try, find the jar in the maven local repository
    log.debug("find jar in maven repo");
    if (!manifestJars.isEmpty()) {

      // retrieve maven local repository path
      String mvnRepoPath = getMavenRepository();

      List<String> jarsToRemove = Collections.synchronizedList(new ArrayList<>());
      synchronized (manifestJars) {
        manifestJars.parallelStream()
            .forEach(jar -> {
              Optional<Path> optMvnJarPath = Optional.empty();
              try (Stream<Path> walk = Files.walk(Paths.get(mvnRepoPath))) {
                optMvnJarPath = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                        .toString()
                        .equals(jar))
                    .findFirst();
                if (optMvnJarPath.isPresent()) {
                  URL url = Paths.get(optMvnJarPath.get()
                      .toString())
                      .toFile()
                      .getCanonicalFile()
                      .toURI()
                      .toURL();
                  log.debug("adding url {} matching {}", url, jar);
                  urls.add(url);
                  jarsToRemove.add(jar);
                }
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
        manifestJars.removeAll(jarsToRemove);
      }
    }
    if (!manifestJars.isEmpty()) {
      log.warn("##### residual unload dependencies #####");
      manifestJars.stream().forEach(log::warn);
      log.warn("##### residual unload dependencies #####");
      throw new Exception("Unable to load all needed dependencies !");
    }
    return urls.toArray(new URL[0]);
  }
  
  protected static URL exactMatch(URL url, String jar) {
    if (url.getFile()
        .contains(jar)) {
      log.debug("adding url {} matching {}", url, jar);
      return url;
    }
    return null;
  }
  
  protected static URL matchesButNotTheVersion(URL url, String jar) throws MalformedURLException {
    Matcher matcher = DirDependency.JAR_WITH_VERSION_PATTERN.matcher(jar);
    if (matcher.find()) {
      String urlRegex = matcher.replaceFirst("$1-(\\\\d[^/]*)$3")
          .replace("-", "\\-")
          .replace(".", "\\.");
      String exactVersion = matcher.replaceFirst("$2");
      Pattern urlPattern = Pattern.compile(urlRegex);
      Matcher urlMatcher = urlPattern.matcher(FilenameUtils.getName(url.getPath()));
      if (urlMatcher.matches()) {
        String notExactVersion = urlMatcher.group(1);
        log.info(
            "classpath url {} matches {} but version {} will be replaced by {} to conform to the app classpath",
            url, jar, notExactVersion, exactVersion);
        URL modifiedUrl = new URL(url.toString()
            .replace(notExactVersion, exactVersion));
        log.debug("adding url {}", modifiedUrl);
        return modifiedUrl;
      }
    }
    return null;
  }
  
  protected static URL matchesEclipseRelatedProject(URL url, String jar) {
    Matcher matcher = DirDependency.JAR_WITH_VERSION_PATTERN.matcher(jar);
    if (matcher.find()) {
      String jarNameWithoutVersionAndExtension = matcher.group(1);
      // we split the part before version of the jar name and we try to find all the words in the remaining
      // program
      // classpath urls
      List<String> jarWordsAndTarget = new ArrayList<>(Arrays.asList(jarNameWithoutVersionAndExtension.split("-")));
      jarWordsAndTarget.add("target/classes");
      if (jarWordsAndTarget
          .stream()
          .allMatch(word -> url.getFile()
              .contains(word))) {
        log.debug("adding url {} matching {}", url, jar);
        return url;
      }
    }
    return null;
  }


  protected String getMavenRepository() throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name")
        .toLowerCase()
        .startsWith("windows")) {
      command.addAll(Arrays.asList("cmd.exe", "/c"));
    } else {
      command.addAll(Arrays.asList("sh", "-c"));
    }
    command
        .addAll(Arrays.asList("mvn", "help:evaluate", "-Dexpression=settings.localRepository", "-q", "-DforceStdout"));

    ProcessBuilder pb = new ProcessBuilder(command);
    Process p = pb.start();
    if (p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0) {
      return IOUtils.toString(p.getInputStream(), Charset.defaultCharset())
          .trim();
    }
    throw new IOException("Unable to find maven local repository path. Is maven installed in your system ?");
  }

}
