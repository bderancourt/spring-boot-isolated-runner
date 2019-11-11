package com.github.bderancourt.springboot.isolatedrunner.launcher;

public interface Dependency {

  static final String RUNNER_CLASS = "com.github.bderancourt.springboot.isolatedrunner.launcher.SpringBootIsolatedLauncher";

  void start(String[] args) throws Exception;

}
