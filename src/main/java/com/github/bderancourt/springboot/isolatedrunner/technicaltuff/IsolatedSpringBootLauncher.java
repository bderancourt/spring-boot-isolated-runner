package com.github.bderancourt.springboot.isolatedrunner.technicaltuff;

import java.lang.reflect.Method;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

public class IsolatedSpringBootLauncher {

  private ApplicationContext context;

  private final Class<?> applicationClass;

  private final String[] args;

  private final String name;

  private boolean shouldWait;

  public IsolatedSpringBootLauncher(Class<?> applicationClass, String[] args, String name) {
    this.applicationClass = applicationClass;
    this.args = args;
    this.name = name;
  }

  public void run() {
    if (context != null) {
      throw new IllegalStateException("Context is not null ! App is running");
    }
    Thread thread = new Thread(() -> {
      try {
        // Launch SpringApplication.run(applicationClass, args) with reflection to be compatible with all spring-boot
        // versions
        Class<?> springApplicationClass = Class.forName("org.springframework.boot.SpringApplication");
        Method runMethod;
        try {
          // Spring-boot v1
          runMethod = springApplicationClass.getDeclaredMethod("run", Object.class, String[].class);
        } catch (NoSuchMethodError e) {
          // Spring-boot v2
          runMethod = springApplicationClass.getDeclaredMethod("run", Class.class, String[].class);
        }
        context = (ConfigurableApplicationContext) runMethod.invoke(null, applicationClass, args);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        synchronized (this) {
          shouldWait = false;
          notifyAll();
        }
      }
    });

    thread.setName(name);
    thread.setContextClassLoader(applicationClass.getClassLoader());
    shouldWait = true;
    thread.start();
    try {
      synchronized (this) {
        while (shouldWait) {
          wait(1000);
        }
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  public void stop() {
    SpringApplication.exit(context);
    context = null;
  }

}
