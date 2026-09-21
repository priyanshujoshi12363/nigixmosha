package com.nigixmosha.app;

import android.app.Application;

import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.studio.LibrarySync;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.WorkService;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Catalog.load(this);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(com.nigixmosha.app.data.SettingsStore.get(this).themeMode());
        LibrarySync.get(this);
        StudioJobs jobs = StudioJobs.get(this);
        jobs.addListener(() -> WorkService.sync(this, jobs));
    }
}
