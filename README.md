# Logback Appender for Amazon Kinesis
This Logback Appender for Amazon Kinesis enables Java applications to send their logs in a structured format to an Amazon Kinesis stream. 

## Requirements
* [AWS SDK 1.11](https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk/1.11.221)
* [Kinesis Producer Library](https://mvnrepository.com/artifact/com.amazonaws/amazon-kinesis-producer/0.12.5)
* [Java 1.8 (Java SE 8)](http://www.oracle.com/technetwork/java/javase/documentation/jdk8-doc-downloads-2133158.html) or later
* [Logback Classic](https://mvnrepository.com/artifact/ch.qos.logback/logback-classic/1.2.3)

## Overview
This appender makes use of the [Kinesis Producer Library](http://docs.aws.amazon.com/streams/latest/dev/developing-producers-with-kpl.html) for seamless integration with Kinesis:
* Automatic retry mechanism
* Log aggregation and batch Kinesis writes

The log messages are sent to Kinesis in the structured format below:
```json
{
    "app_name": "myApp",
    "environment": "staging",
    "level": "ERROR",
    "logger_name": "com.hyp3r.myApp.MyClass",
    "event_type": "test_event",
    "context": "event creation",
    "description": "this is an error message",
    "stacktrace": "com.hyp3r.myApp.MyClass.java:102)\ncom.hyp3r.myApp.MyClass.java:72)\n",
    "timestamp": "2017-10-31T20:40:36+0000",
    "metadata": {
        "key1": "value 1"
    }
}
```

To facilitate adding the extra fields (`event_type`, `context`, `metadata`). This library also contains a 
`KinesisLoggerFactory` that follows the logger factory convention so you can initialize like:
```java
private static final KinesisLogger LOGGER = KinesisLoggerFactory.getLogger(MyClass.class);
``` 
can be used to create a `Logger` that has all the regular logging methods that come with a logger as well as 
accompanying versions `kDebug`, `kInfo`, `kWarn`, `kError` that allow you to pass the `eventType`,`context`, and 
a `metadata` map like:
```java
Map<String, String> metadata = new HashMap<>();
metadata.put("key1", "value 1");
if (request.getDebug()) {
    LOGGER.kDebug("test_event", "event_creation", metadata,"This is a debug message");
}

if (request.getInfo()) {
    LOGGER.kInfo("test_event", "event_creation", metadata,"This is an info message");
}

if (request.getWarn()) {
    LOGGER.kWarn("test_event", "event_creation", metadata,"This is a warn message");
}

if (request.getError()) {
    try {
        throw new ValidationException("This is a ValidationException message");
    } catch (ValidationException e) {
        LOGGER.kError("test_event", "event_creation", metadata,"this is an error message", e);
    }
}
```

## Sample Configuration
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="KINESIS" class="com.hyp3r.services.kinesis.logback.KinesisAppender">
        <appName>myApp</appName>
        <environment>staging</environment>
        <streamName>testStream</streamName>
        <awsRegion>us-east-1</awsRegion>
        <eventsOnly>true</eventsOnly>
    </appender>
    <appender name="stdout" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%5p [%t] (%F:%L) - %m%n</pattern>
        </encoder>
    </appender>
    <logger name="KinesisLogger" additivity="false" level="INFO">
        <appender-ref ref="KINESIS"/>
    </logger>
    <root level="INFO">
        <appender-ref ref="stdout"/>
    </root>
</configuration>
``` 
<sub>Sample: [logback-sample.xml](src/main/resources/logback-sample.xml)</sub>

### Appender Options
| **Option** | **Default** | **required** | **Description**
|:-----------|:-----------:|:------------:|:---------------
|  appName   |             |      yes     | The name of the service using this appender. This value is added to the event object so you can filter events. 
| environment|             |      yes     | Environment of the service using this appender. This value is added to the event object so you can filter events.
| streamName |             |      yes     | Kinesis stream that events will be sent to
|  awsRegion |             |      yes     | AWS Region of the Kinesis stream
| eventsOnly |     true    |      no      | To reduce noise in your Kinesis stream, you can set this flag to true and only logs that have an `event_type` (i.e. used the `KinesisLogger`) will be sent to Kinesis.

### Amazon Credentials
The Amazon Credentials are picked up automatically from environment variables. In order to properly authenticate make sure to set these environment variables:
```bash
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
AWS_DEFAULT_REGION=your_region
``` 

## Related Resources
* [Amazon Kinesis Developer Guide](http://docs.aws.amazon.com/kinesis/latest/dev/introduction.html)  
* [Amazon Kinesis API Reference](http://docs.aws.amazon.com/kinesis/latest/APIReference/Welcome.html)
* [AWS SDK for Java](http://aws.amazon.com/sdkforjava)
* [Logback](https://logback.qos.ch/)