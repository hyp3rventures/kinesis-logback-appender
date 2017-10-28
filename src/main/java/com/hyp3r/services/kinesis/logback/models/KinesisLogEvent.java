package com.hyp3r.services.kinesis.logback.models;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class KinesisLogEvent {
    @SerializedName("app_name")
    private String appName;
    private String environment;
    private String level;
    @SerializedName("logger_name")
    private String loggerName;
    @SerializedName("event_type")
    private String eventType;
    private String context;
    private String description;
    private String stacktrace;
    private Date timestamp;
    private Map<String, Object> metadata;

    public KinesisLogEvent(String appName, String environment, ILoggingEvent event) {
        this.appName = appName;
        this.environment = environment;
        this.level = event.getLevel().toString();
        this.loggerName = event.getLoggerName();
        this.description = event.getMessage();
        if (event.getLevel().isGreaterOrEqual(Level.ERROR) && event.getCallerData() != null) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement stackTraceElement : event.getCallerData()) {
                sb.append(stackTraceElement.toString()).append("\n");
            }
            this.stacktrace = sb.toString();
        }
        this.timestamp = new Date(event.getTimeStamp());

        Map<String, String> properties = event.getMDCPropertyMap();
        if (properties != null) {
            this.metadata = new HashMap<>();
            this.metadata.putAll(properties);

            // Place event_type and context on top level
            if (properties.containsKey("event_type")) {
                this.eventType = properties.get("event_type");
                this.metadata.remove("event_type");
            }

            if (properties.containsKey("context")) {
                this.context = properties.get("context");
                this.metadata.remove("context");
            }
        }
    }
}
