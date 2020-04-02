package com.github.bderancourt.springboot.isolatedrunner.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class DirDependencyTest {

  @Test
  void exactMatchOK() throws MalformedURLException {
    URL url = new URL("file:/C:/m2repo/org/apache/logging/log4j/log4j-to-slf4j/2.12.1/log4j-to-slf4j-2.12.1.jar");
    String jar = "log4j-to-slf4j-2.12.1.jar";
    assertEquals(url, DirDependency.exactMatch(url, jar));
  }

  @Test
  void exactMatchKO() throws MalformedURLException {
    URL url = new URL("file:/C:/m2repo/org/apache/logging/log4j/log4j-to-slf4j/2.12.1/log4j-to-slf4j-2.12.1.jar");
    String jar = "log4j-1.2.17.jar";
    assertNull(DirDependency.exactMatch(url, jar));
  }

  @Test
  void matchesButNotTheVersionOK() throws MalformedURLException {
    URL url = new URL("file:/C:/m2repo/org/apache/logging/log4j/log4j-to-slf4j/2.12.1/log4j-to-slf4j-2.12.1.jar");
    String jar = "log4j-to-slf4j-7.3.8.jar";
    assertEquals(new URL("file:/C:/m2repo/org/apache/logging/log4j/log4j-to-slf4j/7.3.8/log4j-to-slf4j-7.3.8.jar"),
        DirDependency.matchesButNotTheVersion(url, jar));
  }

  @Test
  void matchesButNotTheVersionKO() throws MalformedURLException {
    URL url = new URL("file:/C:/m2repo/org/apache/logging/log4j/log4j-to-slf4j/2.12.1/log4j-to-slf4j-2.12.1.jar");
    String jar = "log4j-1.2.17.jar";
    assertNull(DirDependency.matchesButNotTheVersion(url, jar));
  }

  @Test
  void matchesEclipseRelatedProjectOK() throws MalformedURLException {
    URL url = new URL("file:/C:/git/commons/web/target/classes/");
    String jar = "commons-web-1.0-SNAPSHOT.jar";
    assertEquals(url, DirDependency.matchesEclipseRelatedProject(url, jar));
  }

  @Test
  void matchesEclipseRelatedProjectKO() throws MalformedURLException {
    URL url = new URL("file:/C:/m2repo/org/apache/logging/log4j/log4j-to-slf4j/2.12.1/log4j-to-slf4j-2.12.1.jar");
    String jar = "log4j-1.2.17.jar";
    assertNull(DirDependency.matchesEclipseRelatedProject(url, jar));
  }

}
