package com.github.bderancourt.springboot.isolatedrunner.launcher;

public interface Dependency {

  public static final String RUNNER_CLASS = "com.github.bderancourt.springboot.isolatedrunner.launcher.SpringBootIsolatedLauncher";
  public static final String MANIFEST_CLASSPATH = "Class-Path";

  void start(String[] args) throws Exception;

}
