package com.nigixmosha.app.ui.library;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.snackbar.Snackbar;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.model.CastMember;
import com.nigixmosha.app.data.model.Project;
import com.nigixmosha.app.data.model.ProjectRecord;
import com.nigixmosha.app.data.model.User;
import com.nigixmosha.app.databinding.FragmentLibraryBinding;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Lang;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.main.MainActivity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;

public class LibraryFragment extends Fragment implements PlayerController.Listener, MainActivity.UserAware, MainActivity.Reselectable {
    private enum Screen { LIST, EMPTY, ERROR, NO_MATCH }

    private enum Filter { ALL, RECORDED, DRAFTS }

    private FragmentLibraryBinding b;
    private ApiClient api;
    private LibraryAdapter adapter;
    private GridLayoutManager grid;
    private PlayerController player;
    private Call pending;
    private Cancel opening;
    private User user;
    private TextView avatar;
    private final List<Project> all = new ArrayList<>();
    private boolean loaded;
    private Filter filter = Filter.ALL;
    private String query = "";
    private Screen screen = Screen.LIST;
    private boolean playerShown;
    private int insetBottom;
    private int insetLeft;
    private int insetRight;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clearConfirm = () -> {
        if (adapter != null) adapter.setConfirm(null);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentLibraryBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        api = ApiClient.get(requireContext());
        user = api.cachedUser();
        player = new PlayerController(requireContext(), this);
        b.backdrop.setDrifting(false);
        setupHeader();
        setupFilters();
        setupList();
        setupPlayer();
        setupStates();
        applyInsets();
        load(true);
    }

    private void setupHeader() {
        b.collapsing.setExpandedTitleTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        b.collapsing.setCollapsedTitleTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.toolbar.inflateMenu(R.menu.menu_library);
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_new) {
                ((MainActivity) requireActivity()).openStudio();
                return true;
            }
            return false;
        });
        MenuItem account = b.toolbar.getMenu().findItem(R.id.action_account);
        View action = account.getActionView();
        if (action != null) {
            avatar = action.findViewById(R.id.avatarText);
            action.setOnClickListener(v -> ((MainActivity) requireActivity()).openProfile());
        }
        b.greeting.setText(greeting());
        b.stats.setText(R.string.home_stats_loading);
        bindUser();
        if (Ui.motionEnabled()) {
            View[] parts = {b.greeting, b.stats};
            for (int i = 0; i < parts.length; i++) {
                parts[i].setAlpha(0f);
                parts[i].setTranslationY(Ui.dp(requireContext(), 12));
                parts[i].animate().alpha(1f).translationY(0f).setStartDelay(120L + i * 90L).setDuration(520)
                        .setInterpolator(Ui.DECELERATE).start();
            }
        }
    }

    private void bindUser() {
        if (b == null) return;
        String first = user == null ? "" : user.firstName();
        b.collapsing.setTitle(first.isEmpty() ? getString(R.string.library_title) : getString(R.string.home_title, first));
        if (avatar != null) avatar.setText(user == null ? "" : Format.initials(user.name()));
    }

    @Override
    public void onUser(User user) {
        this.user = user;
        bindUser();
    }

    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) return getString(R.string.greeting_morning);
        if (hour >= 12 && hour < 17) return getString(R.string.greeting_afternoon);
        if (hour >= 17 && hour < 22) return getString(R.string.greeting_evening);
        return getString(R.string.greeting_night);
    }

    private void setupFilters() {
        b.filterChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            filter = id == R.id.filterRecorded ? Filter.RECORDED : id == R.id.filterDrafts ? Filter.DRAFTS : Filter.ALL;
            applyFilter(false);
        });
        b.search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter(false);
            }
        });
        b.search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Ui.hideKeyboard(v);
                return true;
            }
            return false;
        });
        updateCounts();
    }

    private void updateCounts() {
        int recorded = 0;
        for (Project p : all) if (p.hasAudio()) recorded++;
        b.filterAll.setText(getString(R.string.filter_all, all.size()));
        b.filterRecorded.setText(getString(R.string.filter_recorded, recorded));
        b.filterDrafts.setText(getString(R.string.filter_drafts, all.size() - recorded));
        b.filters.setVisibility(all.isEmpty() && loaded ? View.GONE : View.VISIBLE);
    }

    private void setupList() {
        adapter = new LibraryAdapter(new LibraryAdapter.Listener() {
            @Override
            public void onPlay(Project project) {
                player.toggle(project);
            }

            @Override
            public void onOpen(Project project) {
                open(project);
            }

            @Override
            public void onDownload(Project project) {
                download(project);
            }

            @Override
            public void onDelete(Project project) {
                delete(project);
            }
        });
        grid = new GridLayoutManager(requireContext(), spanCount());
        b.list.setLayoutManager(grid);
        b.list.setAdapter(adapter);
        b.list.addItemDecoration(new GridSpacing(getResources().getDimensionPixelSize(R.dimen.grid_spacing)));
        if (b.list.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) b.list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        b.refresh.setColorSchemeResources(R.color.accent);
        b.refresh.setProgressBackgroundColorSchemeResource(R.color.surface_high);
        b.refresh.setOnRefreshListener(() -> load(false));
    }

    private void setupPlayer() {
        b.playerEq.setColor(Color.WHITE);
        b.playerToggle.setOnClickListener(v -> player.togglePlayback());
        b.playerClose.setOnClickListener(v -> player.stop());
    }

    private void setupStates() {
        b.empty.emptyAction.setText(R.string.open_studio);
        b.empty.emptyAction.setIconResource(R.drawable.ic_arrow_forward);
        b.empty.emptyAction.setOnClickListener(v -> ((MainActivity) requireActivity()).openStudio());
        b.empty.emptyWave.setBarCount(22);
        b.empty.emptyWave.setEnergy(0.7f);
        b.error.errorRetry.setOnClickListener(v -> load(true));
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.player, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            insetLeft = bars.left;
            insetRight = bars.right;
            insetBottom = requireActivity().findViewById(R.id.bottomNav) != null ? 0 : system.bottom;
            int gutter = getResources().getDimensionPixelSize(R.dimen.screen_gutter);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.leftMargin = gutter + bars.left;
            params.rightMargin = gutter + bars.right;
            params.bottomMargin = Ui.dpi(requireContext(), 12);
            v.setLayoutParams(params);
            updateListPadding();
            return insets;
        });
    }

    private void updateListPadding() {
        if (b == null) return;
        int gutter = getResources().getDimensionPixelSize(R.dimen.screen_gutter);
        int half = getResources().getDimensionPixelSize(R.dimen.grid_spacing) / 2;
        int bottom = insetBottom + Ui.dpi(requireContext(), 24);
        if (playerShown) bottom += b.player.getHeight() + Ui.dpi(requireContext(), 12);
        b.list.setPadding(gutter - half + insetLeft, Ui.dpi(requireContext(), 4), gutter - half + insetRight, bottom);
    }

    private int spanCount() {
        int width = getResources().getConfiguration().screenWidthDp;
        if (width >= 1100) return 3;
        if (width >= 640) return 2;
        return 1;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (b == null) return;
        grid.setSpanCount(spanCount());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && loaded) load(false);
        if (hidden && player != null) player.pause();
    }

    @Override
    public void onReselected() {
        if (b == null) return;
        b.list.smoothScrollToPosition(0);
        b.appBar.setExpanded(true, true);
    }

    private void load(boolean skeleton) {
        if (pending != null) pending.cancel();
        if (skeleton && adapter.count() == 0) {
            show(Screen.LIST);
            adapter.showSkeleton(spanCount() * 2);
        }
        pending = api.projects(new ApiClient.Callback<List<Project>>() {
            @Override
            public void onSuccess(List<Project> projects) {
                if (b == null) return;
                b.refresh.setRefreshing(false);
                loaded = true;
                all.clear();
                all.addAll(projects);
                updateCounts();
                updateStats();
                applyFilter(true);
                Project playing = player.current();
                if (playing != null && !contains(projects, playing.id)) player.stop();
            }

            @Override
            public void onError(ApiException error) {
                if (b == null) return;
                b.refresh.setRefreshing(false);
                if (error.isUnauthorized()) {
                    ((MainActivity) requireActivity()).toAuth(R.string.notice_expired);
                    return;
                }
                if (!all.isEmpty()) {
                    snackbar(error.getMessage(), R.string.retry, () -> load(false));
                } else {
                    adapter.submit(new ArrayList<>());
                    b.error.errorMessage.setText(error.getMessage());
                    b.stats.setText(R.string.home_stats_empty);
                    show(Screen.ERROR);
                }
            }
        });
    }

    private static boolean contains(List<Project> list, String id) {
        for (Project p : list) if (p.id.equals(id)) return true;
        return false;
    }

    private void applyFilter(boolean fromLoad) {
        if (b == null || !loaded) return;
        List<Project> visible = new ArrayList<>();
        for (Project p : all) {
            if (filter == Filter.RECORDED && !p.hasAudio()) continue;
            if (filter == Filter.DRAFTS && p.hasAudio()) continue;
            if (!query.isEmpty() && !matches(p, query)) continue;
            visible.add(p);
        }
        adapter.submit(visible);
        if (all.isEmpty()) show(Screen.EMPTY);
        else if (visible.isEmpty()) {
            b.noMatch.setText(query.isEmpty() ? getString(R.string.nothing_in_filter) : getString(R.string.nothing_matches, query));
            show(Screen.NO_MATCH);
        } else show(Screen.LIST);
    }

    private static boolean matches(Project p, String q) {
        if (p.displayTitle().toLowerCase(Locale.ROOT).contains(q)) return true;
        for (CastMember c : p.cast()) if (c.name != null && c.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        return p.language != null && Lang.languageName(p.language).toLowerCase(Locale.ROOT).contains(q);
    }

    private void updateStats() {
        int ready = 0;
        for (Project p : all) if (p.hasAudio()) ready++;
        CharSequence text = all.isEmpty() ? getString(R.string.home_stats_empty)
                : getString(R.string.home_stats,
                getResources().getQuantityString(R.plurals.home_chapters, all.size(), all.size()),
                getResources().getQuantityString(R.plurals.home_ready, ready, ready));
        b.stats.setText(text);
    }

    private void show(Screen next) {
        if (screen == next) return;
        screen = next;
        fade(b.empty.getRoot(), next == Screen.EMPTY);
        fade(b.error.getRoot(), next == Screen.ERROR);
        fade(b.noMatch, next == Screen.NO_MATCH);
        if (next == Screen.EMPTY) b.empty.emptyWave.start();
        else b.empty.emptyWave.stop();
    }

    private void fade(View view, boolean visible) {
        if (visible == (view.getVisibility() == View.VISIBLE)) return;
        view.animate().cancel();
        if (!Ui.motionEnabled()) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }
        if (visible) {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(360).setInterpolator(Ui.DECELERATE).start();
        } else {
            view.animate().alpha(0f).setDuration(160).withEndAction(() -> view.setVisibility(View.GONE)).start();
        }
    }

    private void open(Project project) {
        if (opening != null) return;
        adapter.setOpening(project.id);
        opening = api.run(() -> api.project(project.id, new Cancel()), new ApiClient.Callback<ProjectRecord>() {
            @Override
            public void onSuccess(ProjectRecord record) {
                opening = null;
                if (b == null) return;
                adapter.setOpening(null);
                player.stop();
                StudioJobs jobs = StudioJobs.get(requireContext());
                jobs.cancelAll();
                StudioStore.get(requireContext()).loadProject(record);
                ((MainActivity) requireActivity()).openStudio();
            }

            @Override
            public void onError(ApiException error) {
                opening = null;
                if (b == null) return;
                adapter.setOpening(null);
                if (error.isUnauthorized()) ((MainActivity) requireActivity()).toAuth(R.string.notice_expired);
                else snackbar(error.getMessage(), 0, null);
            }
        });
    }

    private void download(Project project) {
        if (project.audio == null || project.audio.downloadUrl == null) return;
        Context c = requireContext();
        String name = slug(project.displayTitle()) + ".mp3";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DownloadManager dm = ContextCompat.getSystemService(c, DownloadManager.class);
            if (dm != null) {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(project.audio.downloadUrl))
                        .setTitle(project.displayTitle())
                        .setDescription(getString(R.string.app_name))
                        .setMimeType("audio/mpeg")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                dm.enqueue(req);
                snackbar(getString(R.string.downloading, name), 0, null);
                return;
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(project.audio.downloadUrl)));
        } catch (Exception e) {
            snackbar(getString(R.string.no_browser), 0, null);
        }
    }

    static String slug(String s) {
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        if (out.length() > 60) out = out.substring(0, 60);
        return out.isEmpty() ? "audiobook" : out;
    }

    private void delete(Project project) {
        handler.removeCallbacks(clearConfirm);
        if (!project.id.equals(adapter.confirmId())) {
            adapter.setConfirm(project.id);
            Ui.haptic(b.list, false);
            handler.postDelayed(clearConfirm, 3000);
            return;
        }
        adapter.setConfirm(null);
        Project playing = player.current();
        if (playing != null && playing.id.equals(project.id)) player.stop();
        List<Project> previous = new ArrayList<>(all);
        all.remove(project);
        updateCounts();
        updateStats();
        applyFilter(false);
        api.run(() -> {
            api.deleteProject(project.id, new Cancel());
            return true;
        }, new ApiClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean value) {
                if (!isAdded()) return;
                StudioStore store = StudioStore.get(requireContext());
                if (project.id.equals(store.state().projectId)) store.setProjectId(null, null, true);
            }

            @Override
            public void onError(ApiException error) {
                if (b == null) return;
                all.clear();
                all.addAll(previous);
                updateCounts();
                updateStats();
                applyFilter(false);
                snackbar(error.getMessage(), 0, null);
            }
        });
    }

    @Override
    public void onPlayerState(Project project, PlayerController.State state) {
        if (b == null) return;
        boolean playing = state == PlayerController.State.PLAYING;
        adapter.setPlayback(project == null || state == PlayerController.State.IDLE ? null : project.id, playing);
        if (project == null || state == PlayerController.State.IDLE) {
            hidePlayer();
            return;
        }
        b.playerTitle.setText(project.displayTitle());
        b.playerEq.setPlaying(playing);
        boolean buffering = state == PlayerController.State.PREPARING;
        b.playerToggleIcon.setVisibility(buffering ? View.INVISIBLE : View.VISIBLE);
        if (buffering) b.playerBuffering.show();
        else b.playerBuffering.hide();
        b.playerToggleIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        b.playerToggle.setContentDescription(getString(playing ? R.string.pause : R.string.play));
        b.playerSubtitle.setTextColor(ContextCompat.getColor(requireContext(),
                state == PlayerController.State.ERROR ? R.color.error : R.color.text_secondary));
        if (buffering) {
            b.playerSubtitle.setText(R.string.player_buffering);
            b.playerProgress.setProgressCompat(0, false);
        } else if (state == PlayerController.State.ERROR) {
            b.playerSubtitle.setText(R.string.player_error);
        }
        showPlayer();
    }

    @Override
    public void onPlayerProgress(long positionMs, long durationMs) {
        if (b == null) return;
        int value = durationMs > 0 ? (int) Math.min(1000, positionMs * 1000 / durationMs) : 0;
        b.playerProgress.setProgressCompat(value, true);
        b.playerSubtitle.setText(getString(R.string.player_time, Format.clock(positionMs), Format.clock(durationMs)));
        adapter.setProgress(value / 1000.0);
    }

    private void showPlayer() {
        if (playerShown) return;
        playerShown = true;
        b.player.setVisibility(View.VISIBLE);
        b.player.post(() -> {
            if (b == null) return;
            updateListPadding();
            if (!Ui.motionEnabled()) return;
            b.player.setTranslationY(b.player.getHeight() + Ui.dp(requireContext(), 24));
            b.player.animate().translationY(0f).setDuration(460)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
        });
    }

    private void hidePlayer() {
        if (!playerShown) return;
        playerShown = false;
        updateListPadding();
        if (!Ui.motionEnabled()) {
            b.player.setVisibility(View.GONE);
            return;
        }
        b.player.animate().translationY(b.player.getHeight() + Ui.dp(requireContext(), 24)).setDuration(260)
                .setInterpolator(Ui.ACCELERATE)
                .withEndAction(() -> {
                    if (b != null && !playerShown) b.player.setVisibility(View.GONE);
                }).start();
    }

    private void snackbar(String message, int action, Runnable onAction) {
        if (b == null) return;
        Snackbar bar = Snackbar.make(b.root, message, Snackbar.LENGTH_LONG);
        if (action != 0 && onAction != null) {
            bar.setAction(action, v -> onAction.run());
            bar.setActionTextColor(ContextCompat.getColor(requireContext(), R.color.accent));
        }
        if (playerShown) bar.setAnchorView(b.player);
        bar.show();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (player != null && !requireActivity().isChangingConfigurations()) player.pause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (pending != null) pending.cancel();
        if (opening != null) opening.cancel();
        if (player != null) player.release();
        b = null;
        super.onDestroyView();
    }
}
