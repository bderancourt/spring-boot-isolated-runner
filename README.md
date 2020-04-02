
# spring-boot-isolated-runner
Run multiple spring-boot applications each in its own class loader and thread context

[![Build Status](https://travis-ci.com/bderancourt/spring-boot-isolated-runner.svg?branch=master)](https://travis-ci.com/bderancourt/spring-boot-isolated-runner)

## Usage

To start your spring-boot app:

    SpringBootIsolatedRunner runner = new SpringBootIsolatedRunner(SPRING_BOOT_CONFIGURATION_CLASS,
            "infos to find your", "springboot-boot-app", "in the classpath");
    runner.start(new String[] {"server.port=8080"});


## License

See [LICENSE](LICENSE).