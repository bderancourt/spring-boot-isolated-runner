package com.github.bderancourt.springboot.isolatedrunner;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import org.apache.commons.io.FileUtils;
import org.springframework.boot.loader.archive.JarFileArchive;

import com.github.bderancourt.springboot.isolatedrunner.launcher.Dependency;
import com.github.bderancourt.springboot.isolatedrunner.launcher.DirDependency;
import com.github.bderancourt.springboot.isolatedrunner.launcher.JarDependency;
import com.github.bderancourt.springboot.isolatedrunner.util.ClassPathUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * The class you need to use to start your spring-boot apps each in an isolated classpath and thread
 * 
 * @author bderancourt
 */
@Slf4j
public class SpringBootIsolatedRunner {

  String[] dependencyInfos;

  private String mainClass;

  /**
   * Constructor
   * 
   * @param mainClass
   *          the main spring @Configuration class to launch
   * @param dependencyInfos
   *          informations to identify your spring-boot app in the classpath
   */
  public SpringBootIsolatedRunner(String mainClass, String... dependencyInfos) {
    this.dependencyInfos = dependencyInfos;
    this.mainClass = mainClass;
  }

  /**
   * @param args
   *          to be passed to your spring-boot app
   * @throws Exception
   *           hmm, problem !
   */
  public void start(String[] args) throws Exception {
    // Firstly, print current program classpath
    log.debug("##### Current classpath #####");
    Arrays.asList(((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs())
        .stream()
        .map(Objects::toString)
        .forEach(log::debug);
    log.debug("##### Current classpath #####");

    URL dependencyUrl;
    List<URL> classpath = null;

    // Check if we are in a surefire or failsafe test with useSystemClassLoader=true configuration
    // https://maven.apache.org/surefire/maven-surefire-plugin/examples/class-loading.html
    URL surefire = null;
    try {
      surefire = ClassPathUtils.findDependencyURL("surefire", "surefirebooter", "jar");
      log.info("surefirebooter.jar found " + surefire.toString());
    } catch (IllegalArgumentException e) {
    }

    // surefire mode
    if (surefire != null) {
      String surefireManifestClassPath = null;
      try (JarFileArchive surefirebooterJar = new JarFileArchive(FileUtils.toFile(surefire))) {
        surefireManifestClassPath = surefirebooterJar.getManifest()
            .getMainAttributes()
            .getValue(Dependency.MANIFEST_CLASSPATH);
      }

      // deducting surefire current dir from surefire jar URL
      Path surefireDir = Paths.get(surefire.toURI())
          .getParent();
      log.debug("surefire jar path: {}", surefireDir);

      // In this list, we store the jars found in the spring-boot app manifest.
      classpath = new ArrayList<>();
      for (String jar : surefireManifestClassPath.split(" ")) {
        URL url = Paths.get(surefireDir.toString(), jar)
            .toFile()
            .getCanonicalFile()
            .toURI()
            .toURL();
        classpath.add(url);
      }
      log.info("##### Surefire override classpath #####");
      classpath.stream()
          .map(Objects::toString)
          .forEach(log::debug);
      log.info("##### Surefire override classpath #####");
      dependencyUrl = ClassPathUtils.findDependencyURL(classpath, dependencyInfos);

      // not in surefire mode
    } else {
      dependencyUrl = ClassPathUtils.findDependencyURL(dependencyInfos);
      classpath = Arrays.asList(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs());
    }
    log.info("dependencyUrl found " + dependencyUrl.toString());

    StringJoiner stringJoiner = new StringJoiner("-");
    Arrays.asList(dependencyInfos)
        .stream()
        .forEach(stringJoiner::add);
    String name = stringJoiner.toString();

    if (dependencyUrl.getFile()
        .endsWith("/")) {
      // This case is dedicated to eclipse launch
      new DirDependency(dependencyUrl, name, mainClass).start(args);
    } else {
      JarFileArchive springBootJar = new JarFileArchive(FileUtils.toFile(dependencyUrl));
      new JarDependency(classpath, springBootJar, name, mainClass).start(args);
    }
  }

}
