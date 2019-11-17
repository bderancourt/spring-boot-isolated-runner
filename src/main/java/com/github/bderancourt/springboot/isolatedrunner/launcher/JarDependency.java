package com.github.bderancourt.springboot.isolatedrunner.launcher;

import java.net.URL;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.JarFile;

import com.github.bderancourt.springboot.isolatedrunner.util.ClassPathUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JarDependency extends JarLauncher implements Dependency {

  private List<URL> classpath;

  private String name;

  private String mainClass;

  private Class<?> runnerClass;

  private Object runnerInstance;

  public JarDependency(List<URL> classpath, Archive archive, String name, String mainClass) {
    super(archive);
    this.classpath = classpath;
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

    List<Archive> archives = getClassPathArchives();
    log.debug("Loaded classpath for {}", name);
    archives.stream().map(Objects::toString).forEach(log::debug);

    JarFile.registerUrlProtocolHandler();
    ClassLoader classLoader = createClassLoader(archives);

    runnerClass = classLoader.loadClass(RUNNER_CLASS);
    Class<?> configClass = classLoader.loadClass(mainClass);

    Object runner = runnerClass.getDeclaredConstructor(Class.class, String[].class, String.class)
        .newInstance(configClass, args, name);
    runnerClass.getMethod("run").invoke(runner);
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
  @Override
  protected ClassLoader createClassLoader(URL[] urls) throws Exception {

    URL[] urlsToAdd = new URL[] { 
        ClassPathUtils.findDependencyURL(classpath, "spring-boot-isolated-runner"),
        ClassPathUtils.findDependencyURL(classpath, "org/springframework/boot/spring-boot/") };

    URL[] totalUrls = ArrayUtils.addAll(urls, urlsToAdd);

    return new LaunchedURLClassLoader(totalUrls, null);
  }

}
