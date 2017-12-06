package com.hyp3r.services.kinesis.logback.models;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
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
    private Map<String, String> metadata;
}
