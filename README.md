# ITarazzo

Bringing automated testing of API-Workflows to life with OAS and Arazzo.

Overview
---
This project provides API developers using OpenAPI Specification (OAS) and Arazzo with an automated and simple way to test workflows. The framework leverages Rest-Assured for API testing and Maven Surefire for generating test reports, making it easy to integrate into existing CI/CD pipelines using Docker.

Features
--
* Parsing: Arazzo and OAS parsing (Work In Progress)
* Validation: JSON Schema validation using Everit (Work In Progress)
* Execution: automated workflow execution using Rest-Assured (Work In Progress)
* Test Reporting:
test execution reports with Maven Surefire (Work In Progress)
* Configuration:
fully configurable through the pom.xml - No source code modifications required
  - Path to OAS file
  - Path to Arazzo file
  - Path to input values file 
* Authorization Support: 
  - `BasicAuth` support (Not yet implemented)
* CI Compatibility: available as a Docker image for seamless CI/CD integration (Not yet implemented)
* Example Application Structure: The repository is organized into separate modules for clarity:

Getting Started
--
* TBD

Maven Dependency
--
* TBD

Docker Image on DockerHub
--
* TBD

Technologies Used
--
* SpringBoot: for application development
* JUnit: for testing and assertions
* OAI Arazzo: for defining and executing workflows
* OAI OpenAPI: for API specification
* Swagger: for API documentation
* Jackson: for parsing
* Everit JSON: for JSON Schema validation
* JSONPath: JSON path validation
* XPath: XML path validation

