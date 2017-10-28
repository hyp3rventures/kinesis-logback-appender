package com.hyp3r.services.kinesis.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hyp3r.services.kinesis.logback.models.KinesisLogEvent;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

public class KinesisAppender<Event extends ILoggingEvent> extends AppenderBase<Event> {

    private boolean initializationFailed = false;

    @Getter @Setter private String appName;
    @Getter @Setter private String environment;
    @Getter @Setter private String streamName;
    @Getter @Setter private String awsRegion;
    @Getter @Setter private Boolean eventsOnly;

    private KinesisProducer kinesisProducer;
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();

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

        eventsOnly = Optional.ofNullable(eventsOnly).orElse(AppenderConstants.DEFAULT_EVENTS_ONLY);

        try {
            Regions.fromName(awsRegion);
        } catch (Exception e) {
            initializationFailed = true;
            addError("Invalid configuration - awsRegion is not valid for appender: " + name);
        }

        if (!initializationFailed) {
            KinesisProducerConfiguration config = new KinesisProducerConfiguration();
            config.setRegion(awsRegion);

            kinesisProducer = new KinesisProducer(config);
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
        if (initializationFailed) {
            addError("Appender has not been initialized. Check necessary config for appender: " + name);
        } else {
            KinesisLogEvent kinesisLogEvent = new KinesisLogEvent(appName, environment, eventObject);
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
                Futures.addCallback(f, kinesisCallback(kinesisLogEvent));
            } catch (UnsupportedEncodingException e) {
                addError("Failed to send event to kinesis: " + e.getMessage(), e);
            }
        }
    }

    private FutureCallback<UserRecordResult> kinesisCallback(KinesisLogEvent event) {
        return new FutureCallback<UserRecordResult>() {
            @Override
            public void onSuccess(UserRecordResult userRecordResult) {
            }

            @Override
            public void onFailure(Throwable e) {
                addError("Failed to send event to kinesis: " + e.getMessage(), e);
            }
        };
    }

    // Config Param Validators
    private boolean isBlankOrContainsWhitespace(String configParam) {
        return StringUtils.isBlank(configParam) || configParam.contains(" ");
    }
}
