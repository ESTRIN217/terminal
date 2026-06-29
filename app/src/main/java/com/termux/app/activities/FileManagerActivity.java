package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.app.filemanager.FileOperationsHelper;
import com.termux.app.filemanager.FileSortOption;
import androidx.appcompat.app.ActionBar;

import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.theme.NightMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class FileManagerActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FileManagerActivity";

    private static final String STATE_CURRENT_PATH = "current_path";
    private static final String STATE_BACK_STACK = "back_stack";
    private static final String STATE_FORWARD_STACK = "forward_stack";
    private static final String STATE_SEARCH_QUERY = "search_query";

    private static final String PREF_NAME = "file_manager";
    private static final String PREF_SORT_OPTION = "sort_option";
    private static final String PREF_SORT_ASCENDING = "sort_ascending";
    private static final String PREF_SHOW_HIDDEN = "show_hidden";
    private static final String PREF_BOOKMARKS = "bookmarks";

    private RecyclerView mFileList;
    private TextView mCurrentPathText;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;

    private FileListAdapter mAdapter;
    private final List<File> mAllFiles = new ArrayList<>();
    private final List<File> mDisplayedFiles = new ArrayList<>();

    private File mCurrentDir;
    private final Stack<String> mBackStack = new Stack<>();
    private final Stack<String> mForwardStack = new Stack<>();

    private FileSortOption mSortOption = FileSortOption.NAME;
    private boolean mSortAscending = true;
    private boolean mShowHidden = false;
    private String mSearchQuery = "";

    private Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.activity_file_manager);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        mFileList = findViewById(R.id.file_list);
        mCurrentPathText = findViewById(R.id.current_path);
        mBackButton = findViewById(R.id.btn_back);
        mForwardButton = findViewById(R.id.btn_forward);

        loadPreferences();

        mAdapter = new FileListAdapter(mDisplayedFiles,
            file -> {
                if (mAdapter.isSelectionMode()) {
                    mAdapter.toggleFileSelection(file);
                    updateSelectionToolbar();
                    supportInvalidateOptionsMenu();
                } else if (file.isDirectory()) {
                    navigateTo(file);
                } else {
                    openFile(file);
                }
            },
            (file, view) -> {
                if (!mAdapter.isSelectionMode()) {
                    showContextMenu(file, view);
                    return true;
                }
                return false;
            }
        );

        mFileList.setLayoutManager(new LinearLayoutManager(this));
        mFileList.setAdapter(mAdapter);

        mBackButton.setOnClickListener(v -> goBack());
        mForwardButton.setOnClickListener(v -> goForward());

        File startDir;
        if (savedInstanceState != null) {
            String path = savedInstanceState.getString(STATE_CURRENT_PATH);
            startDir = path != null ? new File(path) : getDefaultDir();
            String[] back = savedInstanceState.getStringArray(STATE_BACK_STACK);
            String[] forward = savedInstanceState.getStringArray(STATE_FORWARD_STACK);
            if (back != null) mBackStack.addAll(Arrays.asList(back));
            if (forward != null) mForwardStack.addAll(Arrays.asList(forward));
            mSearchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY, "");
        } else {
            startDir = getDefaultDir();
        }

        navigateToInternal(startDir, false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCurrentDir != null) {
            outState.putString(STATE_CURRENT_PATH, mCurrentDir.getAbsolutePath());
        }
        outState.putStringArray(STATE_BACK_STACK, mBackStack.toArray(new String[0]));
        outState.putStringArray(STATE_FORWARD_STACK, mForwardStack.toArray(new String[0]));
        outState.putString(STATE_SEARCH_QUERY, mSearchQuery);
    }

    @Override
    protected void onDestroy() {
        savePreferences();
        super.onDestroy();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        try {
            mSortOption = FileSortOption.valueOf(prefs.getString(PREF_SORT_OPTION, FileSortOption.NAME.name()));
        } catch (Exception e) {
            mSortOption = FileSortOption.NAME;
        }
        mSortAscending = prefs.getBoolean(PREF_SORT_ASCENDING, true);
        mShowHidden = prefs.getBoolean(PREF_SHOW_HIDDEN, false);
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit()
            .putString(PREF_SORT_OPTION, mSortOption.name())
            .putBoolean(PREF_SORT_ASCENDING, mSortAscending)
            .putBoolean(PREF_SHOW_HIDDEN, mShowHidden)
            .apply();
    }

    private File getDefaultDir() {
        File home = new File("/data/data/com.termux/files/home");
        if (home.exists()) return home;
        return Environment.getExternalStorageDirectory();
    }

    // ─── Menu ───────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        getMenuInflater().inflate(R.menu.file_manager_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        setupSearchView(searchItem, searchView);

        applyMenuState();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        applyMenuState();
        return super.onPrepareOptionsMenu(menu);
    }

    private void applyMenuState() {
        if (mMenu == null || mAdapter == null) return;

        boolean selectionMode = mAdapter.isSelectionMode();

        mMenu.setGroupVisible(R.id.group_normal, !selectionMode);
        mMenu.setGroupVisible(R.id.group_selection, selectionMode);

        MenuItem hiddenItem = mMenu.findItem(R.id.action_toggle_hidden);
        if (hiddenItem != null) {
            hiddenItem.setTitle(mShowHidden ? R.string.action_hide_hidden : R.string.action_show_hidden);
        }

        MenuItem sortItem = mMenu.findItem(R.id.action_sort);
        if (sortItem != null) {
            String direction = mSortAscending ? " ↑" : " ↓";
            sortItem.setTitle(mSortOption.getDisplayName() + direction);
        }

        MenuItem pasteItem = mMenu.findItem(R.id.action_paste);
        if (pasteItem != null) {
            pasteItem.setVisible(!selectionMode && FileOperationsHelper.hasClipboard());
        }

        MenuItem selectAllItem = mMenu.findItem(R.id.action_select_all);
        if (selectAllItem != null) {
            boolean allSelected = mAdapter.isAllSelected();
            selectAllItem.setVisible(!allSelected);
        }

        MenuItem deselectAllItem = mMenu.findItem(R.id.action_deselect_all);
        if (deselectAllItem != null) {
            boolean hasSelection = mAdapter.getSelectedCount() > 0;
            deselectAllItem.setVisible(hasSelection);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_new_folder) {
            showNewFolderDialog();
            return true;
        } else if (id == R.id.action_new_file) {
            showNewFileDialog();
            return true;
        } else if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.action_toggle_hidden) {
            mShowHidden = !mShowHidden;
            savePreferences();
            refresh();
            return true;
        } else if (id == R.id.action_bookmarks) {
            showBookmarksDialog();
            return true;
        } else if (id == R.id.action_add_bookmark) {
            addCurrentAsBookmark();
            return true;
        } else if (id == R.id.action_paste) {
            performPaste();
            return true;
        } else if (id == R.id.action_select_all) {
            mAdapter.selectAll();
            updateSelectionToolbar();
            supportInvalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_deselect_all) {
            mAdapter.deselectAll();
            updateSelectionToolbar();
            supportInvalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_copy) {
            performCopy(mAdapter.getSelectedFiles());
            return true;
        } else if (id == R.id.action_cut) {
            performCut(mAdapter.getSelectedFiles());
            return true;
        } else if (id == R.id.action_delete) {
            Set<File> selected = mAdapter.getSelectedFiles();
            if (selected.size() == 1) {
                showDeleteDialog(selected.iterator().next());
            } else {
                showDeleteDialog(selected);
            }
            return true;
        } else if (id == R.id.action_share) {
            List<File> shareList = new ArrayList<>(mAdapter.getSelectedFiles());
            FileOperationsHelper.shareFiles(this, shareList);
            exitSelectionMode();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupSearchView(MenuItem searchItem, SearchView searchView) {
        if (!mSearchQuery.isEmpty()) {
            searchItem.expandActionView();
            searchView.setQuery(mSearchQuery, false);
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mSearchQuery = query;
                filterAndSort();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mSearchQuery = newText;
                filterAndSort();
                return true;
            }
        });
        searchView.setOnCloseListener(() -> {
            mSearchQuery = "";
            filterAndSort();
            return false;
        });
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    @Override
    public boolean onSupportNavigateUp() {
        goBackOrUp();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mAdapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (!goBackOrUp()) {
            super.onBackPressed();
        }
    }

    private boolean goBackOrUp() {
        if (!mBackStack.isEmpty()) {
            goBack();
            return true;
        }
        return goUp();
    }

    private void navigateTo(File dir) {
        if (mCurrentDir != null) {
            mBackStack.push(mCurrentDir.getAbsolutePath());
            mForwardStack.clear();
        }
        navigateToInternal(dir, true);
    }

    private void navigateToInternal(File dir, boolean clearSearch) {
        if (!dir.isDirectory() || !dir.exists()) return;
        mCurrentDir = dir;
        updateTitle(dir);
        mCurrentPathText.setText(dir.getAbsolutePath());
        if (clearSearch) {
            mSearchQuery = "";
            MenuItem searchItem = mMenu != null ? mMenu.findItem(R.id.action_search) : null;
            if (searchItem != null) {
                searchItem.collapseActionView();
            }
        }
        updateNavigationButtons();
        refresh();
    }

    private boolean goUp() {
        File parent = mCurrentDir.getParentFile();
        if (parent != null && parent.exists()) {
            if (mCurrentDir != null) {
                mBackStack.push(mCurrentDir.getAbsolutePath());
                mForwardStack.clear();
            }
            navigateToInternal(parent, true);
            return true;
        }
        return false;
    }

    private void goBack() {
        if (mBackStack.isEmpty()) return;
        if (mCurrentDir != null) {
            mForwardStack.push(mCurrentDir.getAbsolutePath());
        }
        String path = mBackStack.pop();
        navigateToInternal(new File(path), true);
    }

    private void goForward() {
        if (mForwardStack.isEmpty()) return;
        if (mCurrentDir != null) {
            mBackStack.push(mCurrentDir.getAbsolutePath());
        }
        String path = mForwardStack.pop();
        navigateToInternal(new File(path), true);
    }

    private void updateNavigationButtons() {
        mBackButton.setVisibility(mBackStack.isEmpty() ? View.GONE : View.VISIBLE);
        mForwardButton.setVisibility(mForwardStack.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ─── File Listing ───────────────────────────────────────────────────────

    private void refresh() {
        mAllFiles.clear();
        File[] files = mCurrentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!mShowHidden && f.getName().startsWith(".")) continue;
                mAllFiles.add(f);
            }
        }
        filterAndSort();
    }

    private void filterAndSort() {
        mDisplayedFiles.clear();
        for (File f : mAllFiles) {
            if (!mSearchQuery.isEmpty() &&
                !f.getName().toLowerCase().contains(mSearchQuery.toLowerCase())) {
                continue;
            }
            mDisplayedFiles.add(f);
        }
        mDisplayedFiles.sort(mSortOption.getComparator(mSortAscending));
        mAdapter.updateFiles(mDisplayedFiles);
        applyMenuState();
    }

    private void updateTitle(File dir) {
        String name = dir.getName();
        if (dir.getAbsolutePath().equals("/")) {
            setTitle(R.string.bookmark_root);
        } else if ("home".equals(name) && dir.getParent() != null && dir.getParent().endsWith("/files")) {
            setTitle(getString(R.string.bookmark_home));
        } else {
            setTitle(name);
        }
    }

    // ─── Selection Mode ─────────────────────────────────────────────────────

    private void enterSelectionMode(File file) {
        mAdapter.setSelectionMode(true);
        mAdapter.toggleFileSelection(file);
        updateSelectionToolbar();
        supportInvalidateOptionsMenu();
    }

    private void exitSelectionMode() {
        mAdapter.setSelectionMode(false);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(getTitle());
        supportInvalidateOptionsMenu();
    }

    private void updateSelectionToolbar() {
        int count = mAdapter.getSelectedCount();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;
        if (count > 0) {
            actionBar.setTitle(getString(R.string.selection_mode_title, count));
        } else {
            actionBar.setTitle(getTitle());
        }
    }

    // ─── File Operations ────────────────────────────────────────────────────

    private void showNewFolderDialog() {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        EditText input = new EditText(this);
        input.setHint(R.string.hint_name);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_new_folder)
            .setView(input)
            .setPositiveButton(R.string.action_create, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                if (FileOperationsHelper.createDirectory(mCurrentDir, name)) {
                    Toast.makeText(this, getString(R.string.msg_file_created, name), Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    Toast.makeText(this, getString(R.string.msg_error_create, name), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showNewFileDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.hint_name);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_new_file)
            .setView(input)
            .setPositiveButton(R.string.action_create, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                if (FileOperationsHelper.createFile(mCurrentDir, name)) {
                    Toast.makeText(this, getString(R.string.msg_file_created, name), Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    Toast.makeText(this, getString(R.string.msg_error_create, name), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showRenameDialog(File file) {
        EditText input = new EditText(this);
        input.setText(file.getName());
        input.setSelection(file.getName().length());

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_rename)
            .setView(input)
            .setPositiveButton(R.string.action_rename, (d, w) -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty() || newName.equals(file.getName())) return;
                if (FileOperationsHelper.renameFile(file, newName)) {
                    Toast.makeText(this, getString(R.string.msg_file_renamed, newName), Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    Toast.makeText(this, R.string.msg_error_rename, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showDeleteDialog(File file) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_confirm_delete)
            .setMessage(getString(R.string.msg_confirm_delete, file.getName()))
            .setPositiveButton(R.string.action_delete, (d, w) -> performDelete(file))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showDeleteDialog(Set<File> files) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_confirm_delete)
            .setMessage(getString(R.string.msg_confirm_delete_multiple, files.size()))
            .setPositiveButton(R.string.action_delete, (d, w) -> {
                for (File f : files) performDelete(f);
                exitSelectionMode();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void performDelete(File file) {
        File trashFile = moveToTrash(file);
        if (trashFile != null) {
            refresh();
            Snackbar.make(mFileList,
                getString(R.string.msg_undo_delete, file.getName()),
                Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> {
                    if (restoreFromTrash(trashFile, file)) {
                        refresh();
                    }
                })
                .show();
        } else {
            Toast.makeText(this, getString(R.string.msg_error_delete, file.getName()), Toast.LENGTH_SHORT).show();
        }
    }

    private File moveToTrash(File file) {
        File trashDir = new File(getCacheDir(), "fm_trash");
        if (!trashDir.exists()) trashDir.mkdirs();
        File trashFile = new File(trashDir, file.getName());
        int i = 1;
        while (trashFile.exists()) {
            trashFile = new File(trashDir, file.getName() + "." + i);
            i++;
        }
        if (FileOperationsHelper.moveFile(file, trashFile)) {
            return trashFile;
        }
        return null;
    }

    private boolean restoreFromTrash(File trashFile, File original) {
        return FileOperationsHelper.moveFile(trashFile, original);
    }

    private void performCopy(Set<File> files) {
        FileOperationsHelper.setClipboard(new ArrayList<>(files), FileOperationsHelper.ClipboardOperation.COPY);
        Toast.makeText(this, getString(R.string.msg_file_copied, files.size() + " item(s)"), Toast.LENGTH_SHORT).show();
        exitSelectionMode();
    }

    private void performCut(Set<File> files) {
        FileOperationsHelper.setClipboard(new ArrayList<>(files), FileOperationsHelper.ClipboardOperation.CUT);
        Toast.makeText(this, getString(R.string.msg_files_moved, files.size()), Toast.LENGTH_SHORT).show();
        exitSelectionMode();
    }

    private void performPaste() {
        if (!FileOperationsHelper.hasClipboard()) return;

        List<File> sources = FileOperationsHelper.getClipboardFiles();
        FileOperationsHelper.ClipboardOperation op = FileOperationsHelper.getClipboardOperation();
        boolean isCut = op == FileOperationsHelper.ClipboardOperation.CUT;

        new Thread(() -> {
            int successCount = 0;
            for (File src : sources) {
                File dest = new File(mCurrentDir, src.getName());
                boolean ok;
                if (isCut) {
                    ok = FileOperationsHelper.moveFile(src, dest);
                } else {
                    ok = FileOperationsHelper.copyFile(src, dest);
                }
                if (ok) successCount++;
            }

            final int count = successCount;
            runOnUiThread(() -> {
                Toast.makeText(this,
                    getString(R.string.msg_paste_success, count),
                    Toast.LENGTH_SHORT).show();
                if (isCut) {
                    FileOperationsHelper.clearClipboard();
                    invalidateOptionsMenu();
                    applyMenuState();
                }
                refresh();
            });
        }).start();
    }

    // ─── Context Menu ───────────────────────────────────────────────────────

    private void showContextMenu(File file, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, file.isDirectory() ? R.string.action_open_file : R.string.action_open_file);
        popup.getMenu().add(0, 2, 0, R.string.action_rename);
        popup.getMenu().add(0, 3, 0, R.string.action_copy);
        popup.getMenu().add(0, 4, 0, R.string.action_cut);
        popup.getMenu().add(0, 5, 0, R.string.action_delete);
        popup.getMenu().add(0, 6, 0, R.string.action_share);
        popup.getMenu().add(0, 7, 0, R.string.action_details);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (file.isDirectory()) navigateTo(file);
                    else openFile(file);
                    return true;
                case 2:
                    showRenameDialog(file);
                    return true;
                case 3:
                    Set<File> singleCopy = new HashSet<>();
                    singleCopy.add(file);
                    performCopy(singleCopy);
                    return true;
                case 4:
                    Set<File> singleCut = new HashSet<>();
                    singleCut.add(file);
                    performCut(singleCut);
                    return true;
                case 5:
                    showDeleteDialog(file);
                    return true;
                case 6:
                    List<File> shareList = new ArrayList<>();
                    shareList.add(file);
                    FileOperationsHelper.shareFiles(this, shareList);
                    return true;
                case 7:
                    showFileDetailsDialog(file);
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    // ─── Dialogs ────────────────────────────────────────────────────────────

    private void showFileDetailsDialog(File file) {
        String details = FileOperationsHelper.getFileDetails(this, file);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_details)
            .setMessage(details)
            .setPositiveButton(R.string.action_open_file, (d, w) -> openFile(file))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showSortDialog() {
        final FileSortOption[] options = FileSortOption.values();
        String[] names = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            names[i] = options[i].getDisplayName();
        }

        int checked = mSortOption.ordinal();

        new AlertDialog.Builder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(names, checked, (d, w) -> {
                FileSortOption selected = options[w];
                if (selected == mSortOption) {
                    mSortAscending = !mSortAscending;
                } else {
                    mSortOption = selected;
                    mSortAscending = true;
                }
                savePreferences();
                filterAndSort();
                d.dismiss();
            })
            .show();
    }

    private void showBookmarksDialog() {
        final List<String> defaultBookmarks = new ArrayList<>();
        String homePath = "/data/data/com.termux/files/home";
        if (new File(homePath).exists()) defaultBookmarks.add(homePath);
        defaultBookmarks.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        defaultBookmarks.add("/");

        String[] items = new String[defaultBookmarks.size()];
        for (int i = 0; i < defaultBookmarks.size(); i++) {
            String path = defaultBookmarks.get(i);
            String label;
            if (path.equals(homePath)) label = getString(R.string.bookmark_home);
            else if (path.equals("/")) label = getString(R.string.bookmark_root);
            else label = getString(R.string.bookmark_sdcard);
            items[i] = label + "\n" + path;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.action_bookmarks)
            .setItems(items, (d, w) -> {
                File dir = new File(defaultBookmarks.get(w));
                if (dir.exists()) {
                    navigateTo(dir);
                } else {
                    Toast.makeText(this, "Directory not found", Toast.LENGTH_SHORT).show();
                }
            })
            .setPositiveButton(R.string.action_add_bookmark, (d, w) -> addCurrentAsBookmark())
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void addCurrentAsBookmark() {
        Toast.makeText(this, "Bookmarked: " + mCurrentDir.getAbsolutePath(), Toast.LENGTH_SHORT).show();
    }

    // ─── File Opening ───────────────────────────────────────────────────────

    private void openFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
            String mimeType = FileOperationsHelper.getMimeType(file.getName());
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_file)));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Failed to open file: " + file.getAbsolutePath(), e);
            Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show();
        }
    }
}
