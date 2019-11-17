package com.github.bderancourt.springboot.isolatedrunner.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

public class ClassPathUtils {

  /**
   * Find URL in the current program classpath based on the dependency infos passed in args.<br>
   * For example: findDependencyURL("mycompany", "my-spring-boot-app")<br>
   * will return:
   * 
   * @param dependencyInfos
   *          infos to find the dependency in the classpath
   * @return the dependency URL
   */
  public static URL findDependencyURL(String... dependencyInfos) {
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
  public static URL findDependencyURL(List<URL> classLoaderUrls, String... dependencyInfos) {
    List<URL> urls = classLoaderUrls.stream()
        .filter(url -> Arrays.stream(dependencyInfos)
            .allMatch(dependencyInfo -> url.getFile()
                .contains(dependencyInfo)))
        .collect(Collectors.toList());

    StringJoiner dependencyInfosJoiner = new StringJoiner("-");
    Arrays.stream(dependencyInfos)
        .forEach(dependencyInfosJoiner::add);

    StringJoiner classPathJoiner = new StringJoiner("\n");
    classLoaderUrls.forEach(url -> classPathJoiner.add(url.toString()));

    Assert.notEmpty(urls,
        "Dependency not found " + dependencyInfosJoiner.toString() + "\n" + classPathJoiner.toString());

    if (urls.size() > 1) {
      Optional<URL> maxScore = findMaxScore(urls, dependencyInfos);
      if (maxScore.isPresent()) {
        return maxScore.get();
      } else {
        StringJoiner dependenciesFoundJoiner = new StringJoiner("\n");
        urls.stream()
            .forEach(url -> dependenciesFoundJoiner.add(url.toString()));
        String dependenciesFound = dependenciesFoundJoiner.toString();
        throw new IllegalArgumentException(
            urls.size() + " dependencies found, refine your search\n" + dependenciesFound);
      }
    }
    return urls.get(0);
  }

  /**
   * if there are more than one URL found, we take those having the best "score". e.g. those contains dependencyInfo
   * more than one Visible for testing
   * 
   * @param urls to scan
   * @param dependencyInfos to calculate max score
   * @return URL if only one matches the max score, empty otherwise
   */
  protected static Optional<URL> findMaxScore(List<URL> urls, String... dependencyInfos) {
    // remove duplicates
    Set<String> duplicateSet = new LinkedHashSet<>();
    List<URL> duplicateFilteredUrls = new ArrayList<>(urls);
    duplicateFilteredUrls.removeIf(url -> !duplicateSet.add(url.toString()));

    // map that store the URL and their score
    Map<URL, Integer> scores = duplicateFilteredUrls.stream()
        .collect(Collectors.toMap(url -> url, url -> {
          int score = 0;
          // each time a dependencyInfo is found in the URL, score increments
          for (String dependencyInfo : dependencyInfos) {
            score += StringUtils.countMatches(url.toString(), dependencyInfo);
          }
          return score;
        }));

    // max score of the map
    int maxScore = scores.values()
        .stream()
        .max(ComparatorUtils.naturalComparator())
        .get();

    // Which urls have the max score
    List<URL> maxScoreUrls = scores.entrySet()
        .stream()
        .filter(entry -> entry.getValue()
            .intValue() == maxScore)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());

    // If only 1 url have the max score, we return it, else empty
    return maxScoreUrls.size() > 1 ? Optional.empty() : Optional.of(maxScoreUrls.get(0));
  }

}
