package com.example.todocheckboxlist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "TodoPrefs";
    private static final String TODO_KEY = "TodoList";
    private static final String UNPROTECTED_APPS_KEY = "UnprotectedApps";
    private static final String FAVORITE_APPS_KEY = "FavoriteApps";
    
    private final List<String> todos = new ArrayList<>();
    private final List<AppInfo> allApps = new ArrayList<>();
    private final Set<String> unprotectedApps = new HashSet<>();
    private final List<String> favoriteApps = new ArrayList<>();
    
    private LinearLayout todoContainer;
    private GridLayout appsGrid;
    private GridLayout protectedAppsGrid;
    private GridLayout favoritesDock;
    private TextView protectedAppsTitle;
    private EditText todoInput;
    private EditText appSearch;
    private TextView emptyMessage;
    private TextView clockText;
    
    private View sectionApps;
    private View sectionChecklist;
    private Button tabApps;
    private Button tabChecklist;

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;

    static class AppInfo {
        String id; // packageName#userSerial
        String label;
        String packageName;
        ComponentName componentName;
        Drawable icon;
        UserHandle user;

        AppInfo(String id, String label, String packageName, ComponentName componentName, Drawable icon, UserHandle user) {
            this.id = id;
            this.label = label;
            this.packageName = packageName;
            this.componentName = componentName;
            this.icon = icon;
            this.user = user;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Components
        clockText = findViewById(R.id.clockText);
        sectionApps = findViewById(R.id.sectionApps);
        sectionChecklist = findViewById(R.id.sectionChecklist);
        tabApps = findViewById(R.id.tabApps);
        tabChecklist = findViewById(R.id.tabChecklist);
        appSearch = findViewById(R.id.appSearch);
        appsGrid = findViewById(R.id.appsGrid);
        protectedAppsGrid = findViewById(R.id.protectedAppsGrid);
        protectedAppsTitle = findViewById(R.id.protectedAppsTitle);
        favoritesDock = findViewById(R.id.favoritesDock);
        todoInput = findViewById(R.id.todoInput);
        todoContainer = findViewById(R.id.todoContainer);
        emptyMessage = findViewById(R.id.emptyMessage);
        Button addButton = findViewById(R.id.addButton);

        setupTabs();

        addButton.setOnClickListener(view -> addTodo());
        todoInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTodo();
                return true;
            }
            return false;
        });

        appSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadTodos();
        loadUnprotectedApps();
        loadFavoriteApps();
        renderTodos();
        loadApps();
    }

    private void startClock() {
        if (clockRunnable != null) return;
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                clockText.setText(sdf.format(new Date()));
                clockHandler.postDelayed(this, 1000);
            }
        };
        clockHandler.post(clockRunnable);
    }

    private void stopClock() {
        if (clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
            clockRunnable = null;
        }
    }

    private void setupTabs() {
        tabApps.setOnClickListener(v -> switchTab(true));
        tabChecklist.setOnClickListener(v -> switchTab(false));
    }

    private void switchTab(boolean showApps) {
        sectionApps.setVisibility(showApps ? View.VISIBLE : View.GONE);
        sectionChecklist.setVisibility(showApps ? View.GONE : View.VISIBLE);
        tabApps.setTextColor(showApps ? getColor(R.color.accent_color) : getColor(R.color.text_secondary));
        tabChecklist.setTextColor(showApps ? getColor(R.color.text_secondary) : getColor(R.color.accent_color));
    }

    private void loadApps() {
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        
        allApps.clear();
        List<UserHandle> profiles = userManager.getUserProfiles();
        
        for (UserHandle user : profiles) {
            long serial = userManager.getSerialNumberForUser(user);
            List<LauncherActivityInfo> apps = launcherApps.getActivityList(null, user);
            for (LauncherActivityInfo app : apps) {
                String pkg = app.getComponentName().getPackageName();
                if (pkg.equals(getPackageName())) continue;

                Drawable icon = getPackageManager().getUserBadgedIcon(app.getIcon(0), user);
                String id = pkg + "#" + serial;
                allApps.add(new AppInfo(id, app.getLabel().toString(), pkg, app.getComponentName(), icon, user));
            }
        }
        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        renderApps(allApps);
    }

    private void filterApps(String query) {
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo app : allApps) {
            if (app.label.toLowerCase().contains(query.toLowerCase())) filtered.add(app);
        }
        renderApps(filtered);
    }

    private void renderApps(List<AppInfo> apps) {
        appsGrid.removeAllViews();
        protectedAppsGrid.removeAllViews();
        favoritesDock.removeAllViews();

        List<AppInfo> regularList = new ArrayList<>();
        List<AppInfo> protectedList = new ArrayList<>();

        for (AppInfo app : apps) {
            if (!unprotectedApps.contains(app.id)) protectedList.add(app);
            else regularList.add(app);
        }

        // Sort favorites according to their index in favoriteApps
        List<AppInfo> sortedFavorites = new ArrayList<>();
        for (String favId : favoriteApps) {
            for (AppInfo app : apps) {
                if (app.id.equals(favId)) {
                    sortedFavorites.add(app);
                    break;
                }
            }
        }

        renderGrid(appsGrid, regularList, false);
        renderGrid(protectedAppsGrid, protectedList, false);
        renderGrid(favoritesDock, sortedFavorites, true);

        protectedAppsTitle.setVisibility(protectedList.isEmpty() ? View.GONE : View.VISIBLE);
        protectedAppsGrid.setVisibility(protectedList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderGrid(GridLayout grid, List<AppInfo> apps, boolean isDock) {
        for (AppInfo app : apps) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, 16, 0, 16);
            item.setClickable(true);
            item.setFocusable(true);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            item.setLayoutParams(params);

            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(app.icon);
            int size = isDock ? 130 : 150; 
            iconView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            
            boolean isProtected = !unprotectedApps.contains(app.id);
            if (isProtected) iconView.setAlpha(0.5f);

            TextView appTextView = new TextView(this);
            appTextView.setText(app.label);
            appTextView.setTextColor(getColor(R.color.text_primary));
            appTextView.setTextSize(isDock ? 10 : 11);
            appTextView.setGravity(Gravity.CENTER);
            appTextView.setLines(1);
            appTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            appTextView.setPadding(8, 8, 8, 0);

            item.setOnClickListener(v -> {
                if (!unprotectedApps.contains(app.id)) {
                    showProtectedLaunchDialog(app);
                } else {
                    launchApp(app);
                }
            });

            item.setOnLongClickListener(v -> {
                showAppOptions(app);
                return true;
            });

            item.addView(iconView);
            item.addView(appTextView);
            grid.addView(item);
        }
    }

    private void showProtectedLaunchDialog(AppInfo app) {
        final int[] remainingSeconds = {5};
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Opening " + app.label)
                .setMessage("Take a deep breath. App opens in " + remainingSeconds[0] + "s...")
                .setPositiveButton("Open Now", (d, which) -> {
                    stopCountdown();
                    launchApp(app);
                })
                .setNegativeButton("Cancel", (d, which) -> stopCountdown())
                .setCancelable(false)
                .create();

        dialog.show();

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                remainingSeconds[0]--;
                if (remainingSeconds[0] > 0) {
                    dialog.setMessage("Take a deep breath. App opens in " + remainingSeconds[0] + "s...");
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    dialog.dismiss();
                    launchApp(app);
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }

    private void stopCountdown() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
    }

    private void showAppOptions(AppInfo app) {
        boolean isProtected = !unprotectedApps.contains(app.id);
        int favIndex = favoriteApps.indexOf(app.id);
        boolean isFavorite = favIndex != -1;

        List<String> optionsList = new ArrayList<>();
        String protectOption = isProtected ? "Unprotect App" : "Protect App";
        optionsList.add(protectOption);
        optionsList.add((isFavorite ? "Remove from" : "Add to") + " Favorites");

        if (isFavorite) {
            if (favIndex > 0) optionsList.add("Move Left");
            if (favIndex < favoriteApps.size() - 1) optionsList.add("Move Right");
        }

        String[] options = optionsList.toArray(new String[0]);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> {
                    String selection = options[which];
                    if (selection.equals(protectOption)) {
                        toggleAppProtection(app.id);
                    } else if (selection.contains("Favorites")) {
                        toggleFavorite(app.id);
                    } else if (selection.equals("Move Left")) {
                        Collections.swap(favoriteApps, favIndex, favIndex - 1);
                        saveFavoriteApps();
                    } else if (selection.equals("Move Right")) {
                        Collections.swap(favoriteApps, favIndex, favIndex + 1);
                        saveFavoriteApps();
                    }
                    filterApps(appSearch.getText().toString());
                }).show();
    }

    private void launchApp(AppInfo app) {
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        try {
            appSearch.setText(""); 
            launcherApps.startMainActivity(app.componentName, app.user, null, null);
        } catch (Exception e) {
            Toast.makeText(this, "Launch failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleAppProtection(String id) {
        if (unprotectedApps.contains(id)) {
            unprotectedApps.remove(id);
        } else {
            unprotectedApps.add(id);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putStringSet(UNPROTECTED_APPS_KEY, new HashSet<>(unprotectedApps))
                .apply();
    }

    private void toggleFavorite(String id) {
        if (favoriteApps.contains(id)) favoriteApps.remove(id);
        else if (favoriteApps.size() < 4) favoriteApps.add(id);
        else Toast.makeText(this, "Max 4 favorites", Toast.LENGTH_SHORT).show();
        saveFavoriteApps();
    }

    private void saveFavoriteApps() {
        JSONArray arr = new JSONArray(favoriteApps);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(FAVORITE_APPS_KEY, arr.toString()).apply();
    }

    private void loadUnprotectedApps() {
        Set<String> saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getStringSet(UNPROTECTED_APPS_KEY, null);
        if (saved != null) { unprotectedApps.clear(); unprotectedApps.addAll(saved); }
    }

    private void loadFavoriteApps() {
        String saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(FAVORITE_APPS_KEY, null);
        if (saved != null) {
            try {
                JSONArray arr = new JSONArray(saved);
                favoriteApps.clear();
                for (int i = 0; i < arr.length(); i++) favoriteApps.add(arr.getString(i));
            } catch (JSONException e) { e.printStackTrace(); }
        }
    }

    private void loadTodos() {
        String json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(TODO_KEY, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                todos.clear();
                for (int i = 0; i < arr.length(); i++) todos.add(arr.getString(i));
            } catch (JSONException e) { e.printStackTrace(); }
        }
    }

    private void addTodo() {
        String text = todoInput.getText().toString().trim();
        if (text.isEmpty()) { todoInput.setError("Enter task"); return; }
        todos.add(0, text);
        saveTodos();
        todoInput.setText("");
        renderTodos();
    }

    private void saveTodos() {
        JSONArray arr = new JSONArray(todos);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(TODO_KEY, arr.toString()).apply();
    }

    private void renderTodos() {
        todoContainer.removeAllViews();
        emptyMessage.setVisibility(todos.isEmpty() ? View.VISIBLE : View.GONE);
        for (String todo : todos) {
            CheckBox cb = new CheckBox(this);
            cb.setText(todo);
            cb.setTextColor(getColor(R.color.text_primary));
            cb.setTextSize(18);
            cb.setPadding(0, 12, 0, 12);
            cb.setOnCheckedChangeListener((bv, isChecked) -> {
                if (isChecked) { todos.remove(todo); saveTodos(); renderTodos(); }
            });
            todoContainer.addView(cb);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCountdown();
        stopClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startClock();
    }
}
