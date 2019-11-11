package com.github.bderancourt.springboot.isolatedrunner.technicaltuff;

import java.net.URL;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.JarFile;

public class JarDependency extends JarLauncher implements Dependency {

  private String name;

  private Class<?> runnerClass;

  private Object runnerInstance;

  public JarDependency(Archive archive, String name) {
    super(archive);
    this.name = name;
  }

  /**
   * @param args
   * @param propertiesToAdd
   * @throws Exception
   */
  public void start(String[] args) throws Exception {

    List<Archive> archives = getClassPathArchives();
    System.out.println("Loaded classpath for " + name);
    archives.stream()
        .forEach(System.out::println);

    JarFile.registerUrlProtocolHandler();
    ClassLoader classLoader = createClassLoader(archives);

    runnerClass = classLoader.loadClass(RUNNER_CLASS);
    Class<?> configClass = classLoader.loadClass(getMainClass());

    // hack
    // https://stackoverflow.com/questions/28911560/tomcat-8-embedded-error-org-apache-catalina-core-containerbase-a-child-con
    Class<?> tomcatURLStreamHandlerFactoryClass = classLoader
        .loadClass("org.apache.catalina.webresources.TomcatURLStreamHandlerFactory");
    tomcatURLStreamHandlerFactoryClass.getDeclaredMethod("disable")
        .invoke(null, (Object[]) null);

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
  @Override
  protected ClassLoader createClassLoader(URL[] urls) throws Exception {

    URL[] urlsToAdd = new URL[] { ClassPathUtils.findDependencyURL("spring-boot-isolated-runner"),
        ClassPathUtils.findDependencyURL("org/springframework/boot/spring-boot/") };

    URL[] totalUrls = ArrayUtils.addAll(urls, urlsToAdd);

    return new LaunchedURLClassLoader(totalUrls, null);
  }

}
