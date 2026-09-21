package com.nigixmosha.app.ui.studio;

import android.net.Uri;

import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.SettingsStore;
import com.nigixmosha.app.studio.LibrarySync;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;

public interface StudioHost {
    interface FilePicked {
        void onPicked(Uri uri);
    }

    StudioStore store();

    StudioJobs jobs();

    SettingsStore settings();

    ApiClient api();

    LibrarySync sync();

    void pickFile(FilePicked callback);

    void snackbar(String message);

    void openProfile();

    void openLibrary();

    void handleError(ApiException error);

    int bottomInset();
}
