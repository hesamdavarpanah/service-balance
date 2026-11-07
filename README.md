# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.2/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.2/maven-plugin/build-image.html)
* [Spring Batch](https://docs.spring.io/spring-boot/4.1.2/how-to/batch.html)
* [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j.html)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/4.1.2/specification/configuration-metadata/annotation-processor.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.2/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Spring Data Reactive Redis](https://docs.spring.io/spring-boot/4.1.2/reference/data/nosql.html#data.nosql.redis)
* [Rest Repositories](https://docs.spring.io/spring-boot/4.1.2/how-to/data-access.html#howto.data-access.exposing-spring-data-repositories-as-rest)
* [JDBC API](https://docs.spring.io/spring-boot/4.1.2/reference/data/sql.html)
* [Spring Security](https://docs.spring.io/spring-boot/4.1.2/reference/web/spring-security.html)
* [Reactive HTTP Client](https://docs.spring.io/spring-boot/4.1.2/reference/io/rest-client.html#io.rest-client.webclient)
* [SpringDoc OpenAPI](https://springdoc.org/)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/4.1.2/reference/web/reactive.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Creating a Batch Service](https://spring.io/guides/gs/batch-processing/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Messaging with Redis](https://spring.io/guides/gs/messaging-redis/)
* [Accessing JPA Data with REST](https://spring.io/guides/gs/accessing-data-rest/)
* [Accessing Neo4j Data with REST](https://spring.io/guides/gs/accessing-neo4j-data-rest/)
* [Accessing MongoDB Data with REST](https://spring.io/guides/gs/accessing-mongodb-data-rest/)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
* [SpringDoc OpenAPI](https://github.com/springdoc/springdoc-openapi-demos/)
* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM. While most of the inheritance is
fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent. To prevent this, the
project POM contains empty overrides for these elements. If you manually switch to a different parent and actually want
the inheritance, you need to remove those overrides.

