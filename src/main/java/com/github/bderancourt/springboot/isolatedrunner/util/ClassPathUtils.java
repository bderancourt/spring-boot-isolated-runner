package com.github.bderancourt.springboot.isolatedrunner.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

public interface ClassPathUtils {

  static URL findDependencyURL(String... dependencyInfos) {
    URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

    List<URL> urls = Arrays.stream(systemClassLoader.getURLs())
        .filter(url -> Arrays.stream(dependencyInfos)
            .allMatch(dependencyInfo -> url.getFile()
                .contains(dependencyInfo)))
        .collect(Collectors.toList());

    StringJoiner resultJoiner = new StringJoiner("\n");
    urls.stream()
        .forEach(url -> resultJoiner.add(url.toString()));
    String result = resultJoiner.toString();

    Assert.notEmpty(urls, "Dependency not found");
    Assert.isTrue(urls.size() == 1, urls.size() + " dependencies found, refine your search\n" + result);
    return urls.get(0);
  }

}
