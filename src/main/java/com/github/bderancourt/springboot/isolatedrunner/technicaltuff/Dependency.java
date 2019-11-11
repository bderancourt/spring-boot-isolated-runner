package com.github.bderancourt.springboot.isolatedrunner.technicaltuff;

public interface Dependency {

  static final String RUNNER_CLASS = "com.github.bderancourt.springboot.isolatedrunner.IsolatedSpringBootLauncher";

  void start(String[] args) throws Exception;

}
