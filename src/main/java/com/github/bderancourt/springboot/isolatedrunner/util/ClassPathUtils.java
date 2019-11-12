package com.github.bderancourt.springboot.isolatedrunner.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

public interface ClassPathUtils {

  /**
   * Find URL in the current program classpath based on the dependency infos passed in args.<br>
   * For example: findDependencyURL("mycompany", "my-spring-boot-app")<br>
   * will return:
   * 
   * @param dependencyInfos
   *          infos to find the dependency in the classpath
   * @return the dependency URL
   */
  static URL findDependencyURL(String... dependencyInfos) {
    URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    return findDependencyURL(Arrays.asList(systemClassLoader.getURLs()), dependencyInfos);
  }

  /**
   * Find URL in the classLoaderUrls list based on the dependency infos for example: findDependencyURL("mycompany",
   * "my-spring-boot-app") will return:
   * 
   * @param classLoaderUrls
   *          the list in which to find the needed URL
   * @param dependencyInfos
   *          infos to find the dependency in the classpath
   * @return the dependency URL
   */
  static URL findDependencyURL(List<URL> classLoaderUrls, String... dependencyInfos) {
    List<URL> urls = classLoaderUrls.stream()
        .filter(url -> Arrays.stream(dependencyInfos)
            .allMatch(dependencyInfo -> url.getFile()
                .contains(dependencyInfo)))
        .collect(Collectors.toList());

    StringJoiner resultJoiner = new StringJoiner("\n");
    urls.stream()
        .forEach(url -> resultJoiner.add(url.toString()));
    String result = resultJoiner.toString();

    StringJoiner dependencyInfosJoiner = new StringJoiner("-");
    Arrays.stream(dependencyInfos)
        .forEach(dependencyInfosJoiner::add);

    StringJoiner classPathJoiner = new StringJoiner("\n");
    classLoaderUrls.forEach(url -> classPathJoiner.add(url.toString()));

    Assert.notEmpty(urls,
        "Dependency not found " + dependencyInfosJoiner.toString() + "\n" + classPathJoiner.toString());
    Assert.isTrue(urls.size() == 1, urls.size() + " dependencies found, refine your search\n" + result);

    return urls.get(0);
  }

}
