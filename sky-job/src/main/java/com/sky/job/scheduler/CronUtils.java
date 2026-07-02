package com.sky.job.scheduler;

import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class CronUtils {
    public void validate(String expression) {
        if (!isValid(expression)) {
            throw new IllegalArgumentException("Invalid cron expression");
        }
    }

    public boolean isValid(String expression) {
        return CronExpression.isValidExpression(expression);
    }

    public List<ZonedDateTime> nextFireTimes(String expression, int count) {
        validate(expression);
        try {
            CronExpression cronExpression = new CronExpression(expression);
            List<ZonedDateTime> times = new ArrayList<>();
            Date next = new Date();
            int limit = Math.min(Math.max(count, 1), 20);
            for (int i = 0; i < limit; i++) {
                next = cronExpression.getNextValidTimeAfter(next);
                if (next == null) {
                    break;
                }
                times.add(ZonedDateTime.ofInstant(Instant.ofEpochMilli(next.getTime()), ZoneId.systemDefault()));
            }
            return times;
        } catch (ParseException exception) {
            throw new IllegalArgumentException("Invalid cron expression", exception);
        }
    }
}
