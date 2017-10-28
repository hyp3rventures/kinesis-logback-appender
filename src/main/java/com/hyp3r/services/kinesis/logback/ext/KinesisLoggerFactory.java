package com.hyp3r.services.kinesis.logback.ext;

import org.slf4j.LoggerFactory;

public class KinesisLoggerFactory {
    public static KinesisLogger getLogger(String name) {
        return new KinesisLogger(LoggerFactory.getLogger(name));
    }

    public static KinesisLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }
}
