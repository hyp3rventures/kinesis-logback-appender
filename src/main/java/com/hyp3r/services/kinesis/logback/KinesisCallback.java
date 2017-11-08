package com.hyp3r.services.kinesis.logback;

import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.FutureCallback;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KinesisCallback implements FutureCallback<UserRecordResult> {
    @Override
    public void onSuccess(UserRecordResult result) {
        log.trace("Done");
    }

    @Override
    public void onFailure(Throwable e) {
        log.error("Failed to send event to kinesis: " + e.getMessage(), e);
    }
}
