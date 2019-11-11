package com.github.bderancourt.springboot.isolatedrunner.technicaltuff;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.jar.JarFile;

public class DirDependency implements Dependency {

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
   * @param propertiesToAdd
   * @throws Exception
   */
  public void start(String[] args) throws Exception {

    Path manifestPath = Paths.get(new File(classPathDependencyUrl.toURI()).toString(), "META-INF", "MANIFEST.MF");

    Manifest manifest;
    try (InputStream is = new FileInputStream(manifestPath.toFile())) {
      manifest = new Manifest(is);
    }

    URL[] classPathUrls = constructClassPath(manifest.getMainAttributes()
        .getValue("Class-Path"), classPathDependencyUrl);

    System.out.println("Loaded classpath for " + name);
    Arrays.stream(classPathUrls)
        .forEach(System.out::println);

    JarFile.registerUrlProtocolHandler();
    ClassLoader classLoader = createClassLoader(classPathUrls);

    runnerClass = classLoader.loadClass(RUNNER_CLASS);
    Class<?> configClass = classLoader.loadClass(mainClass);

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
  protected ClassLoader createClassLoader(URL[] urls) throws Exception {
    return new LaunchedURLClassLoader(urls, null);
  }

  /**
   * Constructs the spring-boot app classpath. Based on the jar list on the spring-boot app manifest, we need to match
   * which URL on this application classpath (JVM) is relevant.
   * 
   * @param manifestClassPath
   * @param classPathDependencyUrl
   * @return spring-boot app classpath
   */
  protected URL[] constructClassPath(String manifestClassPath, URL classPathDependencyUrl) {
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
    List<String> manifestJars = new ArrayList<>();
    manifestJars.addAll(Arrays.asList(manifestClassPath.split(" ")));

    // First try, find the exact matching between the jar and the program classpath URL
    // Ex: file:/C:/m2repo/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar matching jsr305-3.0.2.jar
    for (ListIterator<URL> itUrls = classPathUrls.listIterator(); itUrls.hasNext();) {
      URL url = itUrls.next();
      for (ListIterator<String> it = manifestJars.listIterator(); it.hasNext();) {
        String jar = it.next();
        if (url.getFile()
            .contains(jar)) {
          //System.out.println("adding url " + url + " matching " + jar);
          urls.add(url);
          it.remove();
          itUrls.remove();
        }
      }
    }

    // Second try. For dependency management reasons, we potentially have jars in the manifest
    // that are not exactly on the same version as in the program classpath URLs.
    // Initialize regex pattern
    Pattern jarWithVersionPattern = Pattern.compile("(.+(?=\\-\\d))(.+(?=\\.jar))(\\.jar)");

    for (ListIterator<URL> itUrls = classPathUrls.listIterator(); itUrls.hasNext();) {
      URL url = itUrls.next();
      for (ListIterator<String> it = manifestJars.listIterator(); it.hasNext();) {
        String jar = it.next();

        Matcher matcher = jarWithVersionPattern.matcher(jar);
        if (matcher.find()) {
          String urlRegex = matcher.replaceFirst("$1[^/]*$3")
              .replace("-", "\\-")
              .replace(".", "\\.");
          Pattern urlPattern = Pattern.compile(urlRegex);
          if (urlPattern.matcher(url.getFile())
              .find()) {
            //System.out.println("adding url " + url + " matching " + jar);
            urls.add(url);
            it.remove();
            itUrls.remove();
          }
        }
      }
    }

    // Third try. If you ran your program in eclipse, eclipse put in classpath the related projects target/classes dirs
    for (ListIterator<URL> itUrls = classPathUrls.listIterator(); itUrls.hasNext();) {
      URL url = itUrls.next();
      for (ListIterator<String> it = manifestJars.listIterator(); it.hasNext();) {
        String jar = it.next();

        Matcher matcher = jarWithVersionPattern.matcher(jar);
        if (matcher.find()) {
          String jarNameWithoutVersionAndExtension = matcher.group(1);
          // we split the part before version of the jar name and we try to find all the words in the remaining program
          // classpath urls
          if (Arrays.asList(jarNameWithoutVersionAndExtension.split("-"))
              .stream()
              .allMatch(word -> url.getFile()
                  .contains(word))) {
            //System.out.println("adding url " + url + " matching " + jar);
            urls.add(url);
            it.remove();
            itUrls.remove();
          }
        }
      }
    }

    return urls.toArray(new URL[0]);
  }
}
