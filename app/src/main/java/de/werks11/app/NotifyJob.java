package de.werks11.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Läuft etwa alle 15 Minuten und prüft auf Neuigkeiten. */
public class NotifyJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try { Notifier.check(getApplicationContext()); } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
