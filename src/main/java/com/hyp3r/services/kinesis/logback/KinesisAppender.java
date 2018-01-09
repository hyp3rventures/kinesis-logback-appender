package com.hyp3r.services.kinesis.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hyp3r.services.kinesis.logback.models.KinesisLogEvent;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public class KinesisAppender<Event extends ILoggingEvent> extends AppenderBase<Event> {

    private static final boolean DEFAULT_EVENTS_ONLY = true;

    private boolean initializationFailed = false;

    @Setter private String appName;
    @Setter private String environment;
    @Setter private String streamName;
    @Setter private String awsRegion;
    @Setter private Boolean eventsOnly;

    @Setter private KinesisProducer kinesisProducer;
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();

    @Override
    public void start() {
        if (isBlankOrContainsWhitespace(appName)) {
            initializationFailed = true;
            addError("Invalid configuration - appName cannot be null or contain whitespace for appender: " + name);
        }

        if (isBlankOrContainsWhitespace(environment)) {
            initializationFailed = true;
            addError("Invalid configuration - environment cannot be null or contain whitespace for appender: " + name);
        }

        if (isBlankOrContainsWhitespace(streamName)) {
            initializationFailed = true;
            addError("Invalid configuration - streamName cannot be null for appender: " + name);
        }

        eventsOnly = Optional.ofNullable(eventsOnly).orElse(DEFAULT_EVENTS_ONLY);

        try {
            Regions.fromName(awsRegion);
        } catch (Exception e) {
            initializationFailed = true;
            addError("Invalid configuration - awsRegion is not valid for appender: " + name);
        }

        if (!initializationFailed) {
            if (kinesisProducer == null) {
                KinesisProducerConfiguration config = new KinesisProducerConfiguration();
                config.setRegion(awsRegion);

                kinesisProducer = new KinesisProducer(config);
            }

            super.start();
        }
    }

    @Override
    public void stop() {
        if (!initializationFailed) {
            kinesisProducer.flushSync();
            kinesisProducer.destroy();
            super.stop();
        }
    }

    @Override
    protected void append(Event eventObject) {
        KinesisLogEvent kinesisLogEvent = new KinesisLogEvent();
        kinesisLogEvent.setAppName(appName);
        kinesisLogEvent.setEnvironment(environment);
        kinesisLogEvent.setLevel(eventObject.getLevel().toString());
        kinesisLogEvent.setLoggerName(eventObject.getLoggerName());
        kinesisLogEvent.setDescription(eventObject.getFormattedMessage());
        if (eventObject.getLevel().isGreaterOrEqual(Level.ERROR)) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement stackTraceElement : eventObject.getCallerData()) {
                sb.append(stackTraceElement.toString()).append("\n");
            }
            kinesisLogEvent.setStacktrace(sb.toString());
        }
        kinesisLogEvent.setTimestamp(new Date(eventObject.getTimeStamp()));
        kinesisLogEvent.setMetadata(new HashMap<>(eventObject.getMDCPropertyMap()));

        // Place event_type and context on top level
        if (kinesisLogEvent.getMetadata().containsKey("event_type")) {
            kinesisLogEvent.setEventType(kinesisLogEvent.getMetadata().get("event_type"));
            kinesisLogEvent.getMetadata().remove("event_type");
        }

        if (kinesisLogEvent.getMetadata().containsKey("context")) {
            kinesisLogEvent.setContext(kinesisLogEvent.getMetadata().get("context"));
            kinesisLogEvent.getMetadata().remove("context");
        }

        if (eventsOnly.equals(true) && kinesisLogEvent.getEventType() == null) {
            // Do not send to kinesis non event logs if flag is true
            return;
        }

        String eventJson = GSON.toJson(kinesisLogEvent);
        try {
            ListenableFuture<UserRecordResult> f = kinesisProducer.addUserRecord(
                streamName,
                UUID.randomUUID().toString(), ByteBuffer.wrap(eventJson.getBytes("UTF-8"))
            );
            Futures.addCallback(f, new KinesisCallback());
        } catch (Exception e) {
            addError("Failed to send event to kinesis: " + e.getMessage(), e);
        }
    }

    // Config Param Validators
    private boolean isBlankOrContainsWhitespace(String configParam) {
        return StringUtils.isBlank(configParam) || configParam.contains(" ");
    }
}
