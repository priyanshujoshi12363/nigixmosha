package com.nigixmosha.app.ui.main;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.SettingsStore;
import com.nigixmosha.app.data.model.ServerConfig;
import com.nigixmosha.app.data.model.User;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.auth.AuthActivity;
import com.nigixmosha.app.ui.library.LibraryFragment;
import com.nigixmosha.app.ui.profile.ProfileFragment;
import com.nigixmosha.app.ui.studio.StudioFragment;

import okhttp3.Call;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";
    public static final int TAB_LIBRARY = 0;
    public static final int TAB_STUDIO = 1;
    public static final int TAB_PROFILE = 2;
    private static final String STATE_TAB = "tab";
    private static final String[] TAGS = {"library", "studio", "profile"};
    private static final int[] IDS = {R.id.nav_library, R.id.nav_studio, R.id.nav_profile};

    private NavigationBarView nav;
    private int tab = TAB_LIBRARY;
    private int smallestWidth;
    private boolean leaving;
    private boolean syncingNav;
    private Cancel configCall;
    private Call meCall;
    private final OnBackPressedCallback back = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            select(TAB_LIBRARY, true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ApiClient api = ApiClient.get(this);
        if (!api.hasSession()) {
            leaving = true;
            Ui.go(this, AuthActivity.intent(this, 0));
            return;
        }
        setContentView(R.layout.activity_main);
        smallestWidth = getResources().getConfiguration().smallestScreenWidthDp;
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        NavigationRailView rail = findViewById(R.id.rail);
        nav = bottom != null ? bottom : rail;
        nav.setOnItemSelectedListener(item -> {
            if (syncingNav) return true;
            for (int i = 0; i < IDS.length; i++) if (IDS[i] == item.getItemId()) show(i, true);
            return true;
        });
        nav.setOnItemReselectedListener(item -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(TAGS[tab]);
            if (f instanceof Reselectable) ((Reselectable) f).onReselected();
        });
        getOnBackPressedDispatcher().addCallback(this, back);

        int start = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, TAB_LIBRARY)
                : getIntent().getIntExtra(EXTRA_TAB, TAB_LIBRARY);
        select(start, false);
        refreshServer();
        refreshUser();
    }

    public interface Reselectable {
        void onReselected();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_TAB) && nav != null) select(intent.getIntExtra(EXTRA_TAB, tab), true);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, tab);
    }

    public void select(int index, boolean animate) {
        show(index, animate);
        if (nav.getSelectedItemId() != IDS[index]) {
            syncingNav = true;
            nav.setSelectedItemId(IDS[index]);
            syncingNav = false;
        }
    }

    private void show(int index, boolean animate) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        if (animate && Ui.motionEnabled()) tx.setCustomAnimations(R.anim.tab_in, R.anim.tab_out);
        for (int i = 0; i < TAGS.length; i++) {
            Fragment f = fm.findFragmentByTag(TAGS[i]);
            if (i == index) {
                if (f == null) tx.add(R.id.container, create(i), TAGS[i]);
                else tx.show(f);
            } else if (f != null && !f.isHidden()) {
                tx.hide(f);
            }
        }
        tx.commitNowAllowingStateLoss();
        tab = index;
        back.setEnabled(index != TAB_LIBRARY);
    }

    private Fragment create(int index) {
        if (index == TAB_STUDIO) return new StudioFragment();
        if (index == TAB_PROFILE) return new ProfileFragment();
        return new LibraryFragment();
    }

    public void openStudio() {
        select(TAB_STUDIO, true);
    }

    public void openProfile() {
        select(TAB_PROFILE, true);
    }

    public void openLibrary() {
        select(TAB_LIBRARY, true);
    }

    public void refreshServer() {
        if (configCall != null) configCall.cancel();
        ApiClient api = ApiClient.get(this);
        configCall = api.run(() -> api.config(new Cancel()), new ApiClient.Callback<ServerConfig>() {
            @Override
            public void onSuccess(ServerConfig value) {
                if (value != null) SettingsStore.get(MainActivity.this).setServer(value);
            }

            @Override
            public void onError(ApiException error) {
            }
        });
    }

    private void refreshUser() {
        meCall = ApiClient.get(this).me(new ApiClient.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user == null) toAuth(R.string.notice_expired);
                else {
                    for (String tag : TAGS) {
                        Fragment f = getSupportFragmentManager().findFragmentByTag(tag);
                        if (f instanceof UserAware) ((UserAware) f).onUser(user);
                    }
                }
            }

            @Override
            public void onError(ApiException error) {
                if (error.isUnauthorized()) toAuth(R.string.notice_expired);
            }
        });
    }

    public interface UserAware {
        void onUser(User user);
    }

    public void toAuth(@StringRes int notice) {
        if (leaving) return;
        leaving = true;
        StudioJobs.get(this).cancelAll();
        Ui.go(this, AuthActivity.intent(this, notice));
    }

    public void handleError(ApiException error) {
        if (error != null && error.isUnauthorized()) toAuth(R.string.notice_expired);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean wasWide = smallestWidth >= 600;
        boolean isWide = newConfig.smallestScreenWidthDp >= 600;
        smallestWidth = newConfig.smallestScreenWidthDp;
        if (wasWide != isWide) recreate();
    }

    @Override
    protected void onDestroy() {
        if (configCall != null) configCall.cancel();
        if (meCall != null) meCall.cancel();
        super.onDestroy();
    }

    public View root() {
        return findViewById(R.id.root);
    }
}
