package com.github.bderancourt.springboot.isolatedrunner;

import java.net.URL;
import java.util.Arrays;
import java.util.StringJoiner;

import org.apache.commons.io.FileUtils;
import org.springframework.boot.loader.archive.JarFileArchive;

import com.github.bderancourt.springboot.isolatedrunner.launcher.DirDependency;
import com.github.bderancourt.springboot.isolatedrunner.launcher.JarDependency;
import com.github.bderancourt.springboot.isolatedrunner.util.ClassPathUtils;

public class SpringBootIsolatedRunner {

  private URL dependencyUrl;

  private String name;

  private String mainClass;

  public SpringBootIsolatedRunner(String mainClass, String... dependencyInfos) {
    dependencyUrl = ClassPathUtils.findDependencyURL(dependencyInfos);
    this.mainClass = mainClass;
    StringJoiner stringJoiner = new StringJoiner("-");
    Arrays.asList(dependencyInfos)
        .stream()
        .forEach(stringJoiner::add);
    name = stringJoiner.toString();
  }

  public void start(String[] args) throws Exception {

    if (dependencyUrl.getFile()
        .endsWith("/")) {
      new DirDependency(dependencyUrl, name, mainClass).start(args);

    } else {
      JarFileArchive springBootJar = new JarFileArchive(FileUtils.toFile(dependencyUrl));
      new JarDependency(springBootJar, name, mainClass).start(args);
    }
  }

}
