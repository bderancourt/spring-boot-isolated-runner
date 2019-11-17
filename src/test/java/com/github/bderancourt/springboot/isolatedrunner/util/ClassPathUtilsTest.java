package com.github.bderancourt.springboot.isolatedrunner.util;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

public class ClassPathUtilsTest {

  @Test
  void findMaxScore() throws Exception {
    String expectedUrl = "file:/C:/m2repo/org/junit/platform/junit-platform-engine/1.4.0/junit-platform-engine-1.4.0.jar";

    List<URL> urls = new ArrayList<>();

    urls.add(new URL(expectedUrl));
    urls.add(new URL(expectedUrl));
    urls.add(
        new URL("file:/C:/m2repo/org/junit/platform/junit-platform-commons/1.4.0/junit-platform-commons-1.4.0.jar"));

    Optional<URL> maxScoreUrl = ClassPathUtils.findMaxScore(urls, "junit", "platform", "engine", "jar");

    assertTrue(maxScoreUrl.isPresent());
    assertEquals(maxScoreUrl.get()
        .toString(), expectedUrl);
  }
  
  @Test
  void findMaxScoreNotSameVersion() throws Exception {
    String expectedUrl = "file:/C:/m2repo/org/junit/platform/junit-platform-engine/1.4.0/junit-platform-engine-1.4.0.jar";

    List<URL> urls = new ArrayList<>();

    urls.add(new URL(expectedUrl));
    urls.add(new URL("file:/C:/m2repo/org/junit/platform/junit-platform-engine/1.4.1/junit-platform-engine-1.4.1.jar"));

    Optional<URL> maxScoreUrl = ClassPathUtils.findMaxScore(urls, "junit", "platform", "engine", "jar");

    assertFalse(maxScoreUrl.isPresent());
  }

  @Test
  void findMaxScoreDuplicates() throws Exception {
    String expectedUrl = "file:/C:/m2repo/org/junit/platform/junit-platform-engine/1.4.0/junit-platform-engine-1.4.0.jar";

    List<URL> urls = new ArrayList<>();

    urls.add(new URL(expectedUrl));
    urls.add(new URL(expectedUrl));

    Optional<URL> maxScoreUrl = ClassPathUtils.findMaxScore(urls, "junit", "platform", "engine", "jar");

    assertTrue(maxScoreUrl.isPresent());
    assertEquals(maxScoreUrl.get()
        .toString(), expectedUrl);
  }

}
