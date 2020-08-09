package com.mishiranu.dashchan.ui.navigator;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.app.ActionBar;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.ActionMenuConfigurator;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.navigator.entity.Page;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.page.ListPage;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.widget.CustomSearchView;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.MenuExpandListener;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;
import java.util.UUID;

public final class PageFragment extends Fragment implements ActivityHandler, ListPage.Callback,
		AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
	private static final String EXTRA_PAGE = "page";
	private static final String EXTRA_RETAIN_ID = "retainId";

	private static final String EXTRA_LIST_POSITION = "listPosition";
	private static final String EXTRA_PARCELABLE_EXTRA = "parcelableExtra";

	public interface Callback {
		UiManager getUiManager();
		ActionIconSet getActionIconSet();
		Object getRetainExtra(String retainId);
		void storeRetainExtra(String retainId, Object extra);
		ActionBar getActionBar();
		void setPageTitle(String title);
		void invalidateHomeUpState();
		void setActionBarLocked(String locker, boolean locked);
		void handleRedirect(Page page, String chanName, String boardName, String threadNumber, String postNumber);
	}

	public PageFragment() {}

	public PageFragment(Page page, String retainId) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_PAGE, page);
		args.putString(EXTRA_RETAIN_ID, retainId);
		setArguments(args);
	}

	public Page getPage() {
		return requireArguments().getParcelable(EXTRA_PAGE);
	}

	public String getRetainId() {
		return requireArguments().getString(EXTRA_RETAIN_ID);
	}

	private Callback getCallback() {
		return (Callback) requireActivity();
	}

	private final ActionMenuConfigurator actionMenuConfigurator = new ActionMenuConfigurator();

	private ListPage<?> listPage;
	private View progressView;
	private View errorView;
	private TextView errorText;
	private PullableListView listView;
	private CustomSearchView searchView;

	private String actionBarLockerPull;
	private String actionBarLockerSearch;

	private ListPosition listPosition;
	private Parcelable parcelableExtra;

	private ListPage.InitRequest initRequest;
	private boolean resetScroll = false;

	private Runnable doOnResume;
	private Menu currentMenu;
	private boolean fillMenuOnResume;
	private boolean searchMode = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		listPosition = savedInstanceState != null && !resetScroll
				? savedInstanceState.getParcelable(EXTRA_LIST_POSITION) : null;
		parcelableExtra = savedInstanceState != null ? savedInstanceState.getParcelable(EXTRA_PARCELABLE_EXTRA) : null;
		resetScroll = false;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.activity_common, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		actionBarLockerPull = "pull-" + UUID.randomUUID();
		actionBarLockerSearch = "search-" + UUID.randomUUID();

		listPage = getPage().content.newPage();
		progressView = view.findViewById(R.id.progress);
		errorView = view.findViewById(R.id.error);
		errorText = view.findViewById(R.id.error_text);
		listView = view.findViewById(android.R.id.list);
		listView.setSaveEnabled(false);
		if (Preferences.isActiveScrollbar()) {
			listView.setFastScrollEnabled(true);
			if (!C.API_LOLLIPOP) {
				ListViewUtils.colorizeListThumb4(listView);
			}
		}
		listView.setOnItemClickListener(this);
		listView.setOnItemLongClickListener(this);
		listView.getWrapper().setOnPullListener(listPage);
		listView.getWrapper().setPullStateListener((wrapper, busy) -> getCallback()
				.setActionBarLocked(actionBarLockerPull, busy));
		ScrollListenerComposite.obtain(listView).add(new BusyScrollListener((isBusy, listView) -> {
			if (listPage != null) {
				listPage.setListViewBusy(isBusy, listView);
			}
		}));
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		getCallback().setActionBarLocked(actionBarLockerPull, false);
		getCallback().setActionBarLocked(actionBarLockerSearch, false);

		listPage.cleanup();
		getCallback().getUiManager().view().notifyUnbindListView(listView);

		listPage = null;
		progressView = null;
		errorView = null;
		errorText = null;
		listView = null;
		searchView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		listPage.init(this, getPage(), listView,
				listPosition, getCallback().getUiManager(), getCallback().getActionIconSet(),
				getCallback().getRetainExtra(getRetainId()), parcelableExtra, initRequest);
		initRequest = null;
		notifyTitleChanged();
	}

	@Override
	public void onResume() {
		super.onResume();

		listPage.resume();
		if (currentMenu != null && fillMenuOnResume) {
			fillMenuOnResume = false;
			// Menu can be requested too early on some Android 4.x
			currentMenu.clear();
			onCreateOptionsMenu(currentMenu);
			onPrepareOptionsMenu(currentMenu);
		}
		Runnable doOnResume = this.doOnResume;
		this.doOnResume = null;
		if (doOnResume != null) {
			doOnResume.run();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		listPage.pause();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (listPage != null) {
			listPosition = listPage.getListPosition();
			Pair<Object, Parcelable> extraPair = listPage.getExtraToStore();
			getCallback().storeRetainExtra(getRetainId(), extraPair.first);
			parcelableExtra = extraPair.second;
		}
		outState.putParcelable(EXTRA_LIST_POSITION, listPosition);
		outState.putParcelable(EXTRA_PARCELABLE_EXTRA, parcelableExtra);
	}

	@Override
	public void onTerminate() {
		currentMenu = null;
	}

	private CustomSearchView getSearchView(boolean required) {
		if (searchView == null && required) {
			searchView = new CustomSearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(requireContext(),
					R.style.Theme_Special_White) : getCallback().getActionBar().getThemedContext());
			searchView.setOnSubmitListener(query -> listPage.onSearchSubmit(query));
			searchView.setOnChangeListener(query -> listPage.onSearchQueryChange(query));
		}
		return searchView;
	}

	private boolean setSearchMode(MenuItem menuItem, boolean search, boolean toggle) {
		if (searchMode != search) {
			searchMode = search;
			if (search) {
				CustomSearchView searchView = getSearchView(true);
				searchView.setHint(menuItem.getTitle());
				listPage.onSearchQueryChange(searchView.getQuery());
			} else {
				listPage.onSearchQueryChange("");
				listPage.onSearchCancel();
			}
			updateOptionsMenu();
			getCallback().setActionBarLocked(actionBarLockerSearch, search);
			getCallback().invalidateHomeUpState();
			if (toggle) {
				if (search) {
					menuItem.expandActionView();
				} else {
					menuItem.collapseActionView();
				}
			}
			return true;
		}
		return false;
	}

	private boolean setSearchMode(boolean search) {
		if (currentMenu != null) {
			MenuItem menuItem = currentMenu.findItem(ListPage.OPTIONS_MENU_SEARCH);
			return setSearchMode(menuItem, search, true);
		}
		return false;
	}

	public void setInitRequest(ListPage.InitRequest initRequest) {
		this.initRequest = initRequest;
	}

	public void requestResetScroll() {
		resetScroll = true;
	}

	public void onAppearanceOptionChanged(int what) {
		listPage.onAppearanceOptionChanged(what);
	}

	public int onDrawerNumberEntered(int number) {
		return listPage.onDrawerNumberEntered(number);
	}

	public void updatePageConfiguration(String postNumber) {
		listPage.updatePageConfiguration(postNumber);
	}

	public void handleNewPostDataListNow() {
		listPage.handleNewPostDataListNow();
	}

	public void notifyAdapterChanged() {
		((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		onCreateOptionsMenu(menu);
	}

	private void onCreateOptionsMenu(Menu menu) {
		currentMenu = menu;
		if (listPage != null && listPage.isRunning()) {
			listPage.onCreateOptionsMenu(menu);
			actionMenuConfigurator.onAfterCreateOptionsMenu(menu);
			MenuItem searchMenuItem = menu.findItem(ListPage.OPTIONS_MENU_SEARCH);
			if (searchMenuItem != null) {
				searchMenuItem.setActionView(getSearchView(true));
				searchMenuItem.setOnActionExpandListener(new MenuExpandListener((menuItem, expand) -> {
					setSearchMode(menuItem, expand, false);
					return true;
				}));
			}
		} else {
			fillMenuOnResume = true;
		}
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		currentMenu = menu;
		if (listPage != null && listPage.isRunning()) {
			for (int i = 0; i < menu.size(); i++) {
				MenuItem menuItem = menu.getItem(i);
				menuItem.setVisible(!searchMode || menuItem.getItemId() == ListPage.OPTIONS_MENU_SEARCH);
			}
			if (searchMode) {
				return;
			}
			listPage.onPrepareOptionsMenu(menu);
			actionMenuConfigurator.onAfterPrepareOptionsMenu(menu);
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == ListPage.OPTIONS_MENU_SEARCH) {
			return false;
		}
		if (listPage.onOptionsItemSelected(item)) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onSearchRequested() {
		return setSearchMode(true) || searchMode;
	}

	@Override
	public boolean onBackPressed() {
		return setSearchMode(false);
	}

	@Override
	public void notifyTitleChanged() {
		getCallback().setPageTitle(listPage.obtainTitle());
	}

	@Override
	public void updateOptionsMenu() {
		if (currentMenu != null) {
			onPrepareOptionsMenu(currentMenu);
		}
	}

	@Override
	public void setCustomSearchView(View view) {
		getSearchView(true).setCustomView(view);
	}

	@Override
	public ActionMode startActionMode(ActionMode.Callback callback) {
		return requireActivity().startActionMode(callback);
	}

	@Override
	public void switchView(ListPage.ViewType viewType, String message) {
		progressView.setVisibility(viewType == ListPage.ViewType.PROGRESS ? View.VISIBLE : View.GONE);
		errorView.setVisibility(viewType == ListPage.ViewType.ERROR ? View.VISIBLE : View.GONE);
		if (viewType == ListPage.ViewType.ERROR) {
			errorText.setText(message != null ? message : getString(R.string.message_unknown_error));
		}
	}

	@Override
	public void showScaleAnimation() {
		Animator animator = AnimatorInflater.loadAnimator(requireContext(), R.animator.fragment_in);
		animator.setTarget(listView);
		animator.start();
	}

	@Override
	public void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber) {
		if (isResumed()) {
			getCallback().handleRedirect(getPage(), chanName, boardName, threadNumber, postNumber);
		} else {
			// Fragment transactions allowed in resumed state only
			doOnResume = () -> handleRedirect(chanName, boardName, threadNumber, postNumber);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		listPage.onItemClick(view, position);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		return listPage.onItemLongClick(view, position);
	}
}
