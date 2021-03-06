/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.activity;

import static org.mariotaku.twidere.util.CompareUtils.classEquals;
import static org.mariotaku.twidere.util.CustomTabUtils.getAddedTabPosition;
import static org.mariotaku.twidere.util.CustomTabUtils.getHomeTabs;
import static org.mariotaku.twidere.util.Utils.cleanDatabasesByItemLimit;
import static org.mariotaku.twidere.util.Utils.createFragmentForIntent;
import static org.mariotaku.twidere.util.Utils.getAccountIds;
import static org.mariotaku.twidere.util.Utils.getDefaultAccountId;
import static org.mariotaku.twidere.util.Utils.openDirectMessagesConversation;
import static org.mariotaku.twidere.util.Utils.openSearch;

import android.app.ActionBar;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManagerTrojan;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Toast;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.CanvasTransformer;
import com.readystatesoftware.viewbadger.BadgeView;

import edu.ucdavis.earlybird.ProfilingUtil;

import org.mariotaku.twidere.R;
import org.mariotaku.twidere.activity.support.DualPaneActivity;
import org.mariotaku.twidere.activity.support.SignInActivity;
import org.mariotaku.twidere.adapter.support.SupportTabsAdapter;
import org.mariotaku.twidere.fragment.iface.IBaseFragment;
import org.mariotaku.twidere.fragment.iface.IBasePullToRefreshFragment;
import org.mariotaku.twidere.fragment.iface.RefreshScrollTopInterface;
import org.mariotaku.twidere.fragment.iface.SupportFragmentCallback;
import org.mariotaku.twidere.fragment.support.DirectMessagesFragment;
import org.mariotaku.twidere.fragment.support.TrendsSuggectionsFragment;
import org.mariotaku.twidere.model.Account;
import org.mariotaku.twidere.model.SupportTabSpec;
import org.mariotaku.twidere.provider.TweetStore.Accounts;
import org.mariotaku.twidere.task.AsyncTask;
import org.mariotaku.twidere.util.ArrayUtils;
import org.mariotaku.twidere.util.AsyncTwitterWrapper;
import org.mariotaku.twidere.util.MathUtils;
import org.mariotaku.twidere.util.MultiSelectEventHandler;
import org.mariotaku.twidere.util.ParseUtils;
import org.mariotaku.twidere.util.SwipebackActivityUtils;
import org.mariotaku.twidere.util.ThemeUtils;
import org.mariotaku.twidere.util.UnreadCountUtils;
import org.mariotaku.twidere.util.Utils;
import org.mariotaku.twidere.util.accessor.ViewAccessor;
import org.mariotaku.twidere.view.ExtendedViewPager;
import org.mariotaku.twidere.view.LeftDrawerFrameLayout;
import org.mariotaku.twidere.view.TabPageIndicator;

import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends DualPaneActivity implements OnClickListener, OnPageChangeListener,
		SupportFragmentCallback, SlidingMenu.OnOpenedListener, SlidingMenu.OnClosedListener,
		SearchView.OnQueryTextListener, SearchView.OnSuggestionListener, OnLongClickListener, OnActionExpandListener,
		SearchView.OnCloseListener {

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_TASK_STATE_CHANGED.equals(action)) {
				updateActionsButton();
			} else if (BROADCAST_UNREAD_COUNT_UPDATED.equals(action)) {
				updateUnreadCount();
			}
		}

	};

	private final Handler mHandler = new Handler();

	private final ContentObserver mAccountChangeObserver = new AccountChangeObserver(this, mHandler);

	private final ArrayList<SupportTabSpec> mCustomTabs = new ArrayList<SupportTabSpec>();
	private final SparseArray<Fragment> mAttachedFragments = new SparseArray<Fragment>();

	private final SparseBooleanArray mPullRefreshStates = new SparseBooleanArray();
	private boolean mBottomActionsButton;

	private Account mSelectedAccountToSearch;

	private SharedPreferences mPreferences;

	private AsyncTwitterWrapper mTwitterWrapper;

	private NotificationManager mNotificationManager;

	private MultiSelectEventHandler mMultiSelectHandler;
	private ActionBar mActionBar;
	private SupportTabsAdapter mPagerAdapter;
	private ExtendedViewPager mViewPager;

	private TabPageIndicator mIndicator;
	private SlidingMenu mSlidingMenu;

	private View mHomeActionsActionView, mActionsButtonLayout, mEmptyTabHint;
	private LeftDrawerFrameLayout mLeftDrawerContainer;
	private SearchView mSearchView;
	private Fragment mCurrentVisibleFragment;

	private UpdateUnreadCountTask mUpdateUnreadCountTask;

	private MenuItem mSearchItem;

	private final Rect mRect = new Rect();

	private boolean mTabDisplayLabel;

	public void closeAccountsDrawer() {
		if (mSlidingMenu == null) return;
		mSlidingMenu.showContent();
	}

	@Override
	public Fragment getCurrentVisibleFragment() {
		return mCurrentVisibleFragment;
	}

	public SlidingMenu getSlidingMenu() {
		return mSlidingMenu;
	}

	public void notifyAccountsChanged() {
		if (mPreferences == null) return;
		final long[] account_ids = getAccountIds(this);
		final long default_id = mPreferences.getLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, -1);
		if (account_ids == null || account_ids.length == 0) {
			finish();
		} else if (account_ids.length > 0 && !ArrayUtils.contains(account_ids, default_id)) {
			mPreferences.edit().putLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, account_ids[0]).commit();
		}
	}

	@Override
	public void onAttachFragment(final Fragment fragment) {
		if (fragment instanceof IBaseFragment && ((IBaseFragment) fragment).getTabPosition() != -1) {
			mAttachedFragments.put(((IBaseFragment) fragment).getTabPosition(), fragment);
		}
	}

	@Override
	public void onBackPressed() {
		if (mSlidingMenu != null && mSlidingMenu.isMenuShowing()) {
			mSlidingMenu.showContent();
			return;
		}
		super.onBackPressed();
	}

	@Override
	public void onBackStackChanged() {
		super.onBackStackChanged();
		if (!isDualPaneMode()) return;
		final FragmentManager fm = getSupportFragmentManager();
		setPagingEnabled(!isLeftPaneUsed());
		final int count = fm.getBackStackEntryCount();
		if (count == 0) {
			showLeftPane();
		}
		updateActionsButton();
		updateSlidingMenuTouchMode();
	}

	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
			case R.id.home_actions_item:
			case R.id.home_actions_button: {
				if (mViewPager == null || mPagerAdapter == null) return;
				final int position = mViewPager.getCurrentItem();
				final SupportTabSpec tab = mPagerAdapter.getTab(position);
				if (tab == null) {
					startActivity(new Intent(INTENT_ACTION_COMPOSE));
				} else {
					if (classEquals(DirectMessagesFragment.class, tab.cls)) {
						openDirectMessagesConversation(this, -1, -1);
					} else if (classEquals(TrendsSuggectionsFragment.class, tab.cls)) {
						openSearchView(null);
					} else {
						startActivity(new Intent(INTENT_ACTION_COMPOSE));
					}
				}
				break;
			}
			case R.id.save_search: {
				if (mTwitterWrapper == null || mSearchView == null || mSelectedAccountToSearch == null) return;
				final String query = ParseUtils.parseString(mSearchView.getQuery());
				if (TextUtils.isEmpty(query)) return;
				mTwitterWrapper.createSavedSearchAsync(mSelectedAccountToSearch.account_id, query);
				break;
			}
		}
	}

	@Override
	public boolean onClose() {
		if (mSearchView == null) return false;
		mSearchView.setQuery(null, false);
		return true;
	}

	@Override
	public void onClosed() {
		updatePullToRefreshLayoutScroll(0, true);
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mViewPager = (ExtendedViewPager) findViewById(R.id.main_pager);
		mActionsButtonLayout = findViewById(R.id.home_actions_button);
		mEmptyTabHint = findViewById(R.id.empty_tab_hint);
		if (mSlidingMenu == null) {
			mSlidingMenu = new SlidingMenu(this);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_home, menu);
		mSearchItem = menu.findItem(MENU_SEARCH);
		mSearchItem.setOnActionExpandListener(this);
		final View actionView = mSearchItem.getActionView();
		final View saveSearch = actionView.findViewById(R.id.save_search);
		saveSearch.setOnClickListener(this);
		saveSearch.setOnLongClickListener(this);
		mSearchView = (SearchView) actionView.findViewById(R.id.search_view);
		mSearchView.setOnCloseListener(this);
		mSearchView.setOnQueryTextListener(this);
		mSearchView.setOnSuggestionListener(this);
		final SearchManager sm = (SearchManager) getSystemService(SEARCH_SERVICE);
		mSearchView.setSearchableInfo(sm.getSearchableInfo(getComponentName()));
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onDetachFragment(final Fragment fragment) {
		if (fragment instanceof IBaseFragment && ((IBaseFragment) fragment).getTabPosition() != -1) {
			mAttachedFragments.remove(((IBaseFragment) fragment).getTabPosition());
		}
	}

	@Override
	public boolean onKeyUp(final int keyCode, final KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_MENU: {
				if (mSlidingMenu != null) {
					mSlidingMenu.toggle(true);
					return true;
				}
				break;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onLongClick(final View v) {
		switch (v.getId()) {
			case R.id.home_actions_item:
			case R.id.save_search: {
				Utils.showMenuItemToast(v, v.getContentDescription(), false);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onMenuItemActionCollapse(final MenuItem item) {
		for (int i = 0, j = mAttachedFragments.size(); i < j; i++) {
			final Fragment f = mAttachedFragments.valueAt(i);
			final View headerView = getPullToRefreshHeaderView(f);
			if (headerView != null) {
				headerView.setEnabled(mPullRefreshStates.get(mAttachedFragments.keyAt(i)));
			}
		}
		if (mSearchView != null) {
			mSearchView.setIconified(true);
			mSearchView.setQuery(null, false);
		}
		return true;
	}

	@Override
	public boolean onMenuItemActionExpand(final MenuItem item) {
		for (int i = 0, j = mAttachedFragments.size(); i < j; i++) {
			final Fragment f = mAttachedFragments.valueAt(i);
			final View headerView = getPullToRefreshHeaderView(f);
			if (headerView != null) {
				mPullRefreshStates.put(mAttachedFragments.keyAt(i), headerView.isEnabled());
				headerView.setEnabled(false);
			}
		}
		if (mSearchView != null) {
			mSearchView.setIconified(false);
		}
		return true;
	}

	@Override
	public void onOpened() {
		updatePullToRefreshLayoutScroll(1, true);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				final FragmentManager fm = getSupportFragmentManager();
				final int count = fm.getBackStackEntryCount();

				if (mSlidingMenu.isMenuShowing()) {
					mSlidingMenu.showContent();
					return true;
				} else if (count == 0 && !mSlidingMenu.isMenuShowing()) {
					mSlidingMenu.showMenu();
					return true;
				}
				if (isDualPaneMode() && !FragmentManagerTrojan.isStateSaved(fm)) {
					fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
					updateActionsButton();
				}
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
	}

	@Override
	public void onPageScrollStateChanged(final int state) {

	}

	@Override
	public void onPageSelected(final int position) {
		if (mSlidingMenu.isMenuShowing()) {
			mSlidingMenu.showContent();
		}
		updateSlidingMenuTouchMode();
		updateActionsButton();
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onQueryTextChange(final String newText) {
		return false;
	}

	@Override
	public boolean onQueryTextSubmit(final String query) {
		if (mSelectedAccountToSearch != null) {
			openSearch(this, mSelectedAccountToSearch.account_id, query);
		} else {
			openSearch(this, Utils.getDefaultAccountId(this), query);
		}
		if (mSearchItem != null) {
			mSearchItem.collapseActionView();
		}
		mSearchView.setQuery(null, false);
		return true;
	}

	@Override
	public void onSetUserVisibleHint(final Fragment fragment, final boolean isVisibleToUser) {
		if (isVisibleToUser) {
			mCurrentVisibleFragment = fragment;
		}
	}

	@Override
	public boolean onSuggestionClick(final int position) {
		final CursorAdapter a = mSearchView.getSuggestionsAdapter();
		final Cursor c = a.getCursor();
		c.moveToPosition(position);
		mSearchView.setQuery(a.convertToString(c), true);
		return true;
	}

	@Override
	public boolean onSuggestionSelect(final int position) {
		final CursorAdapter a = mSearchView.getSuggestionsAdapter();
		final Cursor c = a.getCursor();
		c.moveToPosition(position);
		mSearchView.setQuery(a.convertToString(c), false);
		return true;
	}

	@Override
	public void onWindowFocusChanged(final boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		mHandler.postDelayed(new FixPullToRefreshScrollRunnable(this), 100);
	}

	public void openSearchView(final Account account) {
		if (mSearchItem == null) {
			onSearchRequested();
			return;
		}
		mSelectedAccountToSearch = account;
		if (account != null) {
			mSearchView.setQueryHint(Utils.getDisplayName(this, account.account_id, account.name, account.screen_name));
		} else {
			mSearchView.setQueryHint(getString(R.string.search_hint));
		}
		mSearchItem.expandActionView();
	}

	public void setHomeProgressBarIndeterminateVisibility(final boolean visible) {
		final View view = mBottomActionsButton ? mActionsButtonLayout : mHomeActionsActionView;
		if (view == null) return;
		final boolean has_task = hasActivatedTask();
		final ImageView actions_icon = (ImageView) view.findViewById(R.id.actions_icon);
		final ProgressBar progress = (ProgressBar) view.findViewById(R.id.progress);
		actions_icon.setVisibility(has_task ? View.GONE : View.VISIBLE);
		progress.setVisibility(has_task ? View.VISIBLE : View.GONE);
	}

	@Override
	public boolean shouldOverrideActivityAnimation() {
		return false;
	}

	@Override
	public boolean triggerRefresh(final int position) {
		final Fragment f = mAttachedFragments.get(position);
		return f instanceof RefreshScrollTopInterface && !f.isDetached()
				&& ((RefreshScrollTopInterface) f).triggerRefresh();
	}

	public void updateUnreadCount() {
		if (mIndicator == null || mUpdateUnreadCountTask != null
				&& mUpdateUnreadCountTask.getStatus() == AsyncTask.Status.RUNNING) return;
		mUpdateUnreadCountTask = new UpdateUnreadCountTask(mIndicator, mPreferences.getBoolean(
				PREFERENCE_KEY_UNREAD_COUNT, true));
		mUpdateUnreadCountTask.execute();
	}

	@Override
	protected IBasePullToRefreshFragment getCurrentPullToRefreshFragment() {
		if (mCurrentVisibleFragment instanceof IBasePullToRefreshFragment)
			return (IBasePullToRefreshFragment) mCurrentVisibleFragment;
		else if (mCurrentVisibleFragment instanceof SupportFragmentCallback) {
			final Fragment curr = ((SupportFragmentCallback) mCurrentVisibleFragment).getCurrentVisibleFragment();
			if (curr instanceof IBasePullToRefreshFragment) return (IBasePullToRefreshFragment) curr;
		}
		return null;
	}

	@Override
	protected int getDualPaneLayoutRes() {
		return R.layout.home_dual_pane;
	}

	@Override
	protected int getMainViewId() {
		return R.id.main_pager;
	}

	@Override
	protected int getNormalLayoutRes() {
		return R.layout.home;
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
			case REQUEST_SWIPEBACK_ACTIVITY: {
				// closeAccountsDrawer();
				return;
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/** Called when the activity is first created. */
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!Utils.isDatabaseReady(this)) {
			Toast.makeText(this, R.string.preparing_database_toast, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		mTwitterWrapper = getTwitterWrapper();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mMultiSelectHandler = new MultiSelectEventHandler(this);
		mMultiSelectHandler.dispatchOnCreate();
		final Resources res = getResources();
		final boolean displayIcon = res.getBoolean(R.bool.home_display_icon);
		final long[] accountIds = getAccountIds(this);
		if (accountIds.length == 0) {
			final Intent sign_in_intent = new Intent(INTENT_ACTION_TWITTER_LOGIN);
			sign_in_intent.setClass(this, SignInActivity.class);
			startActivity(sign_in_intent);
			finish();
			return;
		} else {
			notifyAccountsChanged();
		}
		final Intent intent = getIntent();
		if (openSettingsWizard()) {
			finish();
			return;
		}
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONCREATE));
		final boolean refreshOnStart = mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ON_START, false);
		final boolean defDisplayLabel = res.getBoolean(R.bool.default_display_tab_label);
		mTabDisplayLabel = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_TAB_LABEL, defDisplayLabel);
		final int initialTabPosition = handleIntent(intent, savedInstanceState == null);
		mActionBar = getActionBar();
		mActionBar.setCustomView(R.layout.home_tabs);

		final View view = mActionBar.getCustomView();
		mIndicator = (TabPageIndicator) view.findViewById(android.R.id.tabs);
		mHomeActionsActionView = view.findViewById(R.id.home_actions_item);
		ThemeUtils.applyBackground(mIndicator);
		mPagerAdapter = new SupportTabsAdapter(this, getSupportFragmentManager(), mIndicator);
		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOffscreenPageLimit(3);
		mIndicator.setViewPager(mViewPager);
		mIndicator.setOnPageChangeListener(this);
		mIndicator.setDisplayLabel(mTabDisplayLabel);
		mHomeActionsActionView.setOnClickListener(this);
		mHomeActionsActionView.setOnLongClickListener(this);
		mActionsButtonLayout.setOnClickListener(this);
		initTabs();
		final boolean tabs_not_empty = mPagerAdapter.getCount() != 0;
		mEmptyTabHint.setVisibility(tabs_not_empty ? View.GONE : View.VISIBLE);
		mActionBar.setDisplayShowHomeEnabled(displayIcon || !tabs_not_empty);
		mActionBar.setHomeButtonEnabled(displayIcon || !tabs_not_empty);
		mActionBar.setDisplayShowTitleEnabled(!tabs_not_empty);
		mActionBar.setDisplayShowCustomEnabled(tabs_not_empty);
		setTabPosition(initialTabPosition);
		setupSlidingMenu();
		showDataProfilingRequest();
		initUnreadCount();
		updateActionsButton();
		updateSlidingMenuTouchMode();

		if (savedInstanceState == null) {
			if (refreshOnStart) {
				mTwitterWrapper.refreshAll();
			}
			if (intent.getBooleanExtra(EXTRA_OPEN_ACCOUNTS_DRAWER, false)) {
				openAccountsDrawer();
			}
		}
	}

	@Override
	protected void onDestroy() {
		// Delete unused items in databases.
		cleanDatabasesByItemLimit(this);
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONDESTROY));
		super.onDestroy();
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		final int tab_position = handleIntent(intent, false);
		if (tab_position >= 0) {
			mViewPager.setCurrentItem(MathUtils.clamp(tab_position, mPagerAdapter.getCount(), 0));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mViewPager.setEnabled(!mPreferences.getBoolean(PREFERENCE_KEY_DISABLE_TAB_SWIPE, false));
		mBottomActionsButton = mPreferences.getBoolean(PREFERENCE_KEY_BOTTOM_COMPOSE_BUTTON, false);
		invalidateOptionsMenu();
		updateActionsButtonStyle();
		updateActionsButton();
		updateSlidingMenuTouchMode();
	}

	@Override
	protected void onStart() {
		super.onStart();
		mMultiSelectHandler.dispatchOnStart();
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONSTART));
		final ContentResolver resolver = getContentResolver();
		resolver.registerContentObserver(Accounts.CONTENT_URI, true, mAccountChangeObserver);
		final IntentFilter filter = new IntentFilter(BROADCAST_TASK_STATE_CHANGED);
		filter.addAction(BROADCAST_UNREAD_COUNT_UPDATED);
		registerReceiver(mStateReceiver, filter);
		if (isTabsChanged(getHomeTabs(this))
				|| mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_TAB_LABEL, mTabDisplayLabel) != mTabDisplayLabel) {
			restart();
		}
		// UCD
		ProfilingUtil.profile(this, ProfilingUtil.FILE_NAME_APP, "App onStart");
		updateUnreadCount();
	}

	@Override
	protected void onStop() {
		mMultiSelectHandler.dispatchOnStop();
		unregisterReceiver(mStateReceiver);
		final ContentResolver resolver = getContentResolver();
		resolver.unregisterContentObserver(mAccountChangeObserver);
		mPreferences.edit().putInt(PREFERENCE_KEY_SAVED_TAB_POSITION, mViewPager.getCurrentItem()).commit();
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONSTOP));

		// UCD
		ProfilingUtil.profile(this, ProfilingUtil.FILE_NAME_APP, "App onStop");
		super.onStop();
	}

	protected void setPagingEnabled(final boolean enabled) {
		if (mIndicator != null && mViewPager != null) {
			mViewPager.setEnabled(!mPreferences.getBoolean(PREFERENCE_KEY_DISABLE_TAB_SWIPE, false));
			mIndicator.setSwitchingEnabled(enabled);
			mIndicator.setEnabled(enabled);
		}
	}

	private LeftDrawerFrameLayout getLeftDrawerContainer() {
		return mLeftDrawerContainer;
	}

	private View getPullToRefreshHeaderView(final Fragment f) {
		if (f.getActivity() == null || !(f instanceof IBasePullToRefreshFragment)) return null;
		final IBasePullToRefreshFragment ptrf = (IBasePullToRefreshFragment) f;
		final PullToRefreshLayout l = ptrf.getPullToRefreshLayout();
		if (l == null) return null;
		return l.getHeaderView();
	}

	private int handleIntent(final Intent intent, final boolean firstCreate) {
		// use packge's class loader to prevent BadParcelException
		intent.setExtrasClassLoader(getClassLoader());
		// reset intent
		setIntent(new Intent(this, HomeActivity.class));
		final String action = intent.getAction();
		if (Intent.ACTION_SEARCH.equals(action)) {
			final String query = intent.getStringExtra(SearchManager.QUERY);
			final long accountId = getDefaultAccountId(this);
			openSearch(this, accountId, query);
			return -1;
		}
		final boolean refreshOnStart = mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ON_START, false);
		final long[] refreshedIds = intent.getLongArrayExtra(EXTRA_IDS);
		if (refreshedIds != null) {
			mTwitterWrapper.refreshAll(refreshedIds);
		} else if (firstCreate && refreshOnStart) {
			mTwitterWrapper.refreshAll();
		}

		final int tab = intent.getIntExtra(EXTRA_INITIAL_TAB, -1);
		final int initialTab = tab != -1 ? tab : getAddedTabPosition(this, intent.getStringExtra(EXTRA_TAB_TYPE));
		if (initialTab != -1 && mViewPager != null) {
			// clearNotification(initial_tab);
		}
		final Intent extraIntent = intent.getParcelableExtra(EXTRA_EXTRA_INTENT);
		if (extraIntent != null && firstCreate) {
			extraIntent.setExtrasClassLoader(getClassLoader());
			if (isTwidereLink(extraIntent.getData()) && isDualPaneMode()) {
				showFragment(createFragmentForIntent(this, extraIntent), true);
			} else {
				SwipebackActivityUtils.startSwipebackActivity(this, extraIntent);
			}
		}
		return initialTab;
	}

	private boolean hasActivatedTask() {
		if (mTwitterWrapper == null) return false;
		return mTwitterWrapper.hasActivatedTask();
	}

	private void initTabs() {
		final List<SupportTabSpec> tabs = getHomeTabs(this);
		mCustomTabs.clear();
		mCustomTabs.addAll(tabs);
		mPagerAdapter.clear();
		mPagerAdapter.addTabs(tabs);
	}

	private void initUnreadCount() {
		for (int i = 0, j = mIndicator.getTabCount(); i < j; i++) {
			final BadgeView badge = new BadgeView(this, mIndicator.getTabItem(i).findViewById(R.id.tab_item_content));
			badge.setId(R.id.unread_count);
			badge.setBadgePosition(BadgeView.POSITION_TOP_RIGHT);
			badge.setTextSize(getResources().getInteger(R.integer.unread_count_text_size));
			badge.hide();
		}
	}

	private boolean isTabsChanged(final List<SupportTabSpec> tabs) {
		if (mCustomTabs.size() == 0 && tabs == null) return false;
		if (mCustomTabs.size() != tabs.size()) return true;
		for (int i = 0, size = mCustomTabs.size(); i < size; i++) {
			if (!mCustomTabs.get(i).equals(tabs.get(i))) return true;
		}
		return false;
	}

	private boolean isTwidereLink(final Uri data) {
		return data != null && SCHEME_TWIDERE.equals(data.getScheme());
	}

	private void openAccountsDrawer() {
		if (mSlidingMenu == null) return;
		mSlidingMenu.showMenu();
	}

	private boolean openSettingsWizard() {
		if (mPreferences == null || mPreferences.getBoolean(PREFERENCE_KEY_SETTINGS_WIZARD_COMPLETED, false))
			return false;
		startActivity(new Intent(this, SettingsWizardActivity.class));
		return true;
	}

	private void setPullToRefreshLayoutScroll(final WindowManager wm, final Fragment f, final int scrollX,
			final int statusBarHeight, final boolean horizontalScroll) {
		if (f == null) return;
		final View headerView = getPullToRefreshHeaderView(f);
		if (headerView != null) {
			headerView.setScrollX(scrollX);
			if (!horizontalScroll) {
				headerView.post(new UpdatePullToRefreshYRunnable(wm, headerView, statusBarHeight));
			}
		}
	}

	private void setTabPosition(final int initial_tab) {
		final boolean remember_position = mPreferences.getBoolean(PREFERENCE_KEY_REMEMBER_POSITION, true);
		if (initial_tab >= 0) {
			mViewPager.setCurrentItem(MathUtils.clamp(initial_tab, mPagerAdapter.getCount(), 0));
		} else if (remember_position) {
			final int position = mPreferences.getInt(PREFERENCE_KEY_SAVED_TAB_POSITION, 0);
			mViewPager.setCurrentItem(MathUtils.clamp(position, mPagerAdapter.getCount(), 0));
		}
	}

	private void setupSlidingMenu() {
		if (mSlidingMenu == null) return;
		final int marginThreshold = getResources().getDimensionPixelSize(R.dimen.default_sliding_menu_margin_threshold);
		mSlidingMenu.setMode(SlidingMenu.LEFT);
		mSlidingMenu.setShadowWidthRes(R.dimen.default_sliding_menu_shadow_width);
		mSlidingMenu.setShadowDrawable(R.drawable.shadow_holo);
		mSlidingMenu.setBehindWidthRes(R.dimen.left_drawer_width);
		mSlidingMenu.setTouchmodeMarginThreshold(marginThreshold);
		mSlidingMenu.setFadeDegree(0.5f);
		mSlidingMenu.attachToActivity(this, SlidingMenu.SLIDING_WINDOW);
		mSlidingMenu.setMenu(R.layout.home_left_drawer_container);
		mSlidingMenu.setOnOpenedListener(this);
		mSlidingMenu.setOnClosedListener(this);
		mLeftDrawerContainer = (LeftDrawerFrameLayout) mSlidingMenu.getMenu().findViewById(R.id.left_drawer_container);
		final boolean isTransparentBackground = ThemeUtils.isTransparentBackground(this);
		mLeftDrawerContainer.setClipEnabled(isTransparentBackground);
		mLeftDrawerContainer.setScrollScale(mSlidingMenu.getBehindScrollScale());
		mSlidingMenu.setBehindCanvasTransformer(new ListenerCanvasTransformer(this));
		if (isTransparentBackground) {
			ViewAccessor.setBackground(mSlidingMenu.getContent(), null);
		}
		if (isDualPaneMode()) {
			mSlidingMenu.addIgnoredView(getSlidingPane().getRightPaneContainer());
		}
	}

	private void showDataProfilingRequest() {
		if (mPreferences.getBoolean(PREFERENCE_KEY_SHOW_UCD_DATA_PROFILING_REQUEST, true)) {
			final Intent intent = new Intent(this, DataProfilingSettingsActivity.class);
			final PendingIntent content_intent = PendingIntent.getActivity(this, 0, intent, 0);
			final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
			builder.setAutoCancel(true);
			builder.setSmallIcon(R.drawable.ic_stat_info);
			builder.setTicker(getString(R.string.data_profiling_notification_ticker));
			builder.setContentTitle(getString(R.string.data_profiling_notification_title));
			builder.setContentText(getString(R.string.data_profiling_notification_desc));
			builder.setContentIntent(content_intent);
			mNotificationManager.notify(NOTIFICATION_ID_DATA_PROFILING, builder.build());
		}
	}

	private void updateActionsButton() {
		if (mViewPager == null || mPagerAdapter == null) return;
		final int button_icon, title;
		final int position = mViewPager.getCurrentItem();
		final SupportTabSpec tab = mPagerAdapter.getTab(position);
		if (tab == null) {
			title = R.string.compose;
			button_icon = R.drawable.ic_menu_status_compose;
		} else {
			if (classEquals(DirectMessagesFragment.class, tab.cls)) {
				button_icon = R.drawable.ic_menu_compose;
				title = R.string.compose;
			} else if (classEquals(TrendsSuggectionsFragment.class, tab.cls)) {
				button_icon = android.R.drawable.ic_menu_search;
				title = android.R.string.search_go;
			} else {
				button_icon = R.drawable.ic_menu_status_compose;
				title = R.string.compose;
			}
		}
		final View view = mBottomActionsButton ? mActionsButtonLayout : mHomeActionsActionView;
		if (view == null) return;
		final boolean has_task = hasActivatedTask();
		final ImageView actions_icon = (ImageView) view.findViewById(R.id.actions_icon);
		final ProgressBar progress = (ProgressBar) view.findViewById(R.id.progress);
		view.setContentDescription(getString(title));
		actions_icon.setImageResource(mBottomActionsButton ? button_icon : button_icon);
		actions_icon.setVisibility(has_task ? View.GONE : View.VISIBLE);
		progress.setVisibility(has_task ? View.VISIBLE : View.GONE);
	}

	private void updateActionsButtonStyle() {
		if (mActionsButtonLayout == null || mHomeActionsActionView == null) return;
		final boolean leftside_compose_button = mPreferences.getBoolean(PREFERENCE_KEY_LEFTSIDE_COMPOSE_BUTTON, false);
		mHomeActionsActionView.setVisibility(mBottomActionsButton ? View.GONE : View.VISIBLE);
		mActionsButtonLayout.setVisibility(mBottomActionsButton ? View.VISIBLE : View.GONE);
		final FrameLayout.LayoutParams compose_lp = (LayoutParams) mActionsButtonLayout.getLayoutParams();
		compose_lp.gravity = Gravity.BOTTOM | (leftside_compose_button ? Gravity.LEFT : Gravity.RIGHT);
		mActionsButtonLayout.setLayoutParams(compose_lp);
	}

	private void updatePullToRefreshLayoutScroll(final float percentOpen, final boolean horizontalScroll) {
		final Window w = getWindow();
		final WindowManager wm = getWindowManager();
		final View decorView = w.getDecorView();
		decorView.getWindowVisibleDisplayFrame(mRect);
		final int statusBarHeight = mRect.top;
		final LeftDrawerFrameLayout ld = getLeftDrawerContainer();
		if (ld == null) return;
		final int scrollX = -Math.round(percentOpen * ld.getMeasuredWidth());
		ld.setPercentOpen(percentOpen);
		for (int i = 0, j = mAttachedFragments.size(); i < j; i++) {
			final Fragment f = mAttachedFragments.valueAt(i);
			setPullToRefreshLayoutScroll(wm, f, scrollX, statusBarHeight, horizontalScroll);
		}
		setPullToRefreshLayoutScroll(wm, getLeftPaneFragment(), scrollX, statusBarHeight, horizontalScroll);
		setPullToRefreshLayoutScroll(wm, getRightPaneFragment(), scrollX, statusBarHeight, horizontalScroll);
	}

	private void updateSlidingMenuTouchMode() {
		if (mViewPager == null || mSlidingMenu == null) return;
		final int position = mViewPager.getCurrentItem();
		final int mode = !mViewPager.isEnabled() || position == 0 && !isLeftPaneUsed() ? SlidingMenu.TOUCHMODE_FULLSCREEN
				: SlidingMenu.TOUCHMODE_MARGIN;
		mSlidingMenu.setTouchModeAbove(mode);
	}

	private static final class AccountChangeObserver extends ContentObserver {
		private final HomeActivity mActivity;

		public AccountChangeObserver(final HomeActivity activity, final Handler handler) {
			super(handler);
			mActivity = activity;
		}

		@Override
		public void onChange(final boolean selfChange) {
			onChange(selfChange, null);
		}

		@Override
		public void onChange(final boolean selfChange, final Uri uri) {
			mActivity.notifyAccountsChanged();
			mActivity.updateUnreadCount();
		}
	}

	private static class FixPullToRefreshScrollRunnable implements Runnable {
		private final HomeActivity mActivity;

		FixPullToRefreshScrollRunnable(final HomeActivity activity) {
			mActivity = activity;
		}

		@Override
		public void run() {
			final SlidingMenu slidingMenu = mActivity.getSlidingMenu();
			if (slidingMenu != null && slidingMenu.isMenuShowing()) {
				mActivity.updatePullToRefreshLayoutScroll(1, false);
			} else {
				mActivity.updatePullToRefreshLayoutScroll(0, false);
			}
		}

	}

	private static class ListenerCanvasTransformer implements CanvasTransformer {
		private final HomeActivity mHomeActivity;

		public ListenerCanvasTransformer(final HomeActivity homeActivity) {
			mHomeActivity = homeActivity;
		}

		@Override
		public void transformCanvas(final Canvas canvas, final float percentOpen) {
			mHomeActivity.updatePullToRefreshLayoutScroll(percentOpen, true);
		}

	}

	private class UpdatePullToRefreshYRunnable implements Runnable {
		private final WindowManager mWindowManager;
		private final View mView;
		private final int mY;

		UpdatePullToRefreshYRunnable(final WindowManager wm, final View view, final int y) {
			mWindowManager = wm;
			mView = view;
			mY = y;
		}

		@Override
		public void run() {
			if (mView == null || mWindowManager == null) return;
			if (mView.getWindowToken() == null) {
				mView.post(this);
				return;
			}
			final ViewGroup.LayoutParams lp = mView.getLayoutParams();
			if (!(lp instanceof WindowManager.LayoutParams)) return;
			final WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
			wlp.y = mY;
			mWindowManager.updateViewLayout(mView, wlp);

		}

	}

	private static class UpdateUnreadCountTask extends AsyncTask<Void, Void, int[]> {
		private final Context mContext;
		private final TabPageIndicator mIndicator;
		private final boolean mEnabled;

		UpdateUnreadCountTask(final TabPageIndicator indicator, final boolean enabled) {
			mIndicator = indicator;
			mContext = indicator.getContext();
			mEnabled = enabled;
		}

		@Override
		protected int[] doInBackground(final Void... params) {
			final int tab_count = mIndicator.getTabCount();
			final int[] result = new int[tab_count];
			for (int i = 0, j = tab_count; i < j; i++) {
				result[i] = UnreadCountUtils.getUnreadCount(mContext, i);
			}
			return result;
		}

		@Override
		protected void onPostExecute(final int[] result) {
			final int tab_count = mIndicator.getTabCount();
			if (result == null || result.length != tab_count) return;
			for (int i = 0, j = tab_count; i < j; i++) {
				final BadgeView badge = (BadgeView) mIndicator.getTabItem(i).findViewById(R.id.unread_count);
				if (!mEnabled) {
					badge.setCount(0);
					badge.hide();
					continue;
				}
				final int count = result[i];
				if (count > 0) {
					badge.setCount(count);
					badge.show();
				} else if (count == 0) {
					badge.setCount(0);
					badge.hide();
				} else {
					badge.setText("\u0387");
					badge.show();
				}
			}
		}

	}

}
