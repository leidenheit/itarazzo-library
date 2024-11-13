# Itarazzo Library
Enables seamless execution of OpenAPI Initiative (OAI) [Arazzo Specifications](https://spec.openapis.org/arazzo/latest.html) in an integration testing 
context. Designed for developers who need automated and test-driven verification of API workflows, *Itarazzo Library* 
extends integration testing capabilities using a range of powerful technologies.

---

**Note**: This project is developed in my free time alongside family and full-time work. Any feedback or contributions are greatly appreciated as I strive to make this tool as helpful as possible for other developers.

---

## Key Features
- **Arazzo Specification Integration**: Executes Arazzo Specifications within integration tests, allowing automated e2e-testing 
  of complex workflows with detailed logging.
---

## Technologies Used
Itarazzo Library leverages a robust stack of technologies to provide extensive API testing support:
- **JUnit 5**: For structuring and running integration tests.
- **Jackson**: For efficient JSON parsing and serialization.
- **Everit JSON Path**: Enables flexible, path-based JSON validation.
- **XPath**: Supports XML handling, useful for APIs returning XML responses.
- **RestAssured**: Simplifies HTTP requests and validations for RESTful APIs.
- **Swagger**: Parses OpenAPI specifications, enabling standardized API testing against specifications.
---

## Getting Started
### Prerequisites
Ensure you have the following dependencies in your project’s pom.xml:

```xml
<dependencies>
  <dependency>
    <groupId>io.github.leidenheit</groupId>
    <artifactId>itarazzo</artifactId>
    <version>0.1.0</version>
  </dependency>
  
  <!-- a logging implementation e.g. Logback -->
  <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>1.5.6</version>
  </dependency>
  <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-core</artifactId>
      <version>1.5.6</version>
  </dependency>
</dependencies>
```

### Basic Usage
To use Itarazzo Library in your integration tests, start by creating a test class that extends the capabilities of the 
library with the JUnit 5 `ItarazzoExtension`.

```java
@ExtendWith(ItarazzoExtension.class)
class ExampleArazzoIT {

    @TestFactory
    @DisplayName("Workflow")
    Stream<DynamicTest> executeWorkflow(final ArazzoSpecification arazzoSpec, final String inputsFilePath) {
        var inputs = InputsReader.readInputs(inputsFilePath);
        ItarazzoDynamicTest dynamicTest = new ItarazzoDynamicTest();
        return dynamicTest.generateWorkflowTests(arazzoSpec, inputs);
    }
}
```

### Configuration Options
#### Environment Variables
To define specific Arazzo files and inputs:

- `ARAZZO_FILE`: Specifies the path to the Arazzo YAML specification file. 
- `ARAZZO_INPUTS_FILE`: Path to a valid JSON file containing test inputs.

These can be set directly in the test environment or passed as system properties in Maven.

---
## Custom Extension Points (coming soon)
The Itarazzo Library features extensibility, allowing you to configure and customize API interactions precisely to 
match your testing requirements. Below are some common extension points.

### Custom Server Selection
To designate a server specifically for the integration test, use the `x-itarazzo-designated-server` extension. 
If no server is designated, the library considers the first server from the referenced source description.

Example in an OpenAPI file:
```yaml
servers:
- url: https://api.example.com/v1
  x-itarazzo-designated-server: true
```
This configuration ensures that the Itarazzo Library targets the specified server during test execution.

---
## Running Tests
To execute the tests, run the following Maven command:

```bash
mvn verify -Darazzo.file="/path/to/arazzo.yaml" -Darazzo.inputs.file="/path/to/inputs.json"
```
This command triggers the workflow specified in your Arazzo YAML and applies test inputs from the JSON file.

---
### Contributions
Contributions are welcome! If you encounter any issues or have suggestions, please submit them as issues or pull requests.
