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

package org.mariotaku.twidere.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ListView;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.mariotaku.jsonserializer.JSONSerializer;
import org.mariotaku.twidere.adapter.ParcelableActivitiesAdapter;
import org.mariotaku.twidere.model.ParcelableActivity;
import org.mariotaku.twidere.model.ParcelableStatus;
import org.mariotaku.twidere.model.ParcelableUser;

import static org.mariotaku.twidere.util.Utils.openStatus;
import static org.mariotaku.twidere.util.Utils.openUserFollowers;
import static org.mariotaku.twidere.util.Utils.openUserProfile;
import static org.mariotaku.twidere.util.Utils.parseString;

public abstract class BaseActivitiesListFragment extends PullToRefreshListFragment implements
		LoaderCallbacks<List<ParcelableActivity>> {

	private ParcelableActivitiesAdapter mAdapter;
	private SharedPreferences mPreferences;

	private boolean mIsActivitiesSaved;

	private List<ParcelableActivity> mData;

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new ParcelableActivitiesAdapter(getActivity());
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		setListAdapter(mAdapter);
		getLoaderManager().initLoader(0, getArguments(), this);
		setListShown(false);
	}

	@Override
	public void onDestroy() {
		saveActivities();
		super.onDestroy();
	}

	@Override
	public void onDestroyView() {
		saveActivities();
		super.onDestroyView();
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id) {
		final int adapter_pos = position - l.getHeaderViewsCount();
		final ParcelableActivity item = mAdapter.getItem(adapter_pos);
		if (item == null) return;
		final ParcelableUser[] sources = item.sources;
		final ParcelableStatus[] target_statuses = item.target_statuses;
		final int sources_length = sources != null ? sources.length : 0;
		if (sources_length > 0) {
			final ParcelableStatus[] target_objects = item.target_object_statuses;
			switch (item.action) {
				case ParcelableActivity.ACTION_FAVORITE: {
						if (sources_length == 1) {
							openUserProfile(getActivity(), sources[0]);
						} else {
							if (target_statuses != null && target_statuses.length > 0) {
								openStatus(getActivity(), target_statuses[0]);
							}
						}
						break;
					}
				case ParcelableActivity.ACTION_FOLLOW: {
						if (sources_length == 1) {
							openUserProfile(getActivity(), sources[0]);
						} else {
							openUserFollowers(getActivity(), item.account_id, item.account_id, null);
						}
						break;
					}
				case ParcelableActivity.ACTION_MENTION: {
						if (target_objects != null && target_objects.length > 0) {
							openStatus(getActivity(), target_objects[0]);
						}
						break;
					}
				case ParcelableActivity.ACTION_REPLY: {
						if (target_statuses != null && target_statuses.length > 0) {
							openStatus(getActivity(), target_statuses[0]);
						}
						break;
					}
				case ParcelableActivity.ACTION_RETWEET: {
						if (sources_length == 1) {
							openUserProfile(getActivity(), sources[0]);
						} else {
							if (target_objects != null && target_objects.length > 0) {
								openStatus(getActivity(), target_objects[0]);
							}
						}
						break;
					}
			}
		}
	}

	@Override
	public void onLoaderReset(final Loader<List<ParcelableActivity>> loader) {
		mAdapter.setData(null);
		mData = null;
	}

	@Override
	public void onLoadFinished(final Loader<List<ParcelableActivity>> loader, final List<ParcelableActivity> data) {
		setProgressBarIndeterminateVisibility(false);
		mData = data;
		mAdapter.setData(data);
		onRefreshComplete();
		setListShown(true);
	}

	@Override
	public void onPullDownToRefresh() {
		getLoaderManager().restartLoader(0, getArguments(), this);
	}

	@Override
	public void onPullUpToRefresh() {

	}

	@Override
	public void onResume() {
		super.onResume();
		final float text_size = mPreferences.getInt(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		final boolean show_absolute_time = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_ABSOLUTE_TIME, false);
		mAdapter.setDisplayProfileImage(display_profile_image);
		mAdapter.setTextSize(text_size);
		mAdapter.setShowAbsoluteTime(show_absolute_time);
	}
	
	protected final List<ParcelableActivity> getData() {
		return mData;
	}

	protected void saveActivities() {
		if (getActivity() == null || getView() == null || mIsActivitiesSaved) return;
		if (saveActivitiesInternal()) {
			mIsActivitiesSaved = true;
		}
	}
	
	protected final boolean saveActivitiesInternal() {
		if (mIsActivitiesSaved) return true;
		final int items_limit = mPreferences.getInt(PREFERENCE_KEY_DATABASE_ITEM_LIMIT, PREFERENCE_DEFAULT_DATABASE_ITEM_LIMIT);
		final List<ParcelableActivity> data = getData();
		final List<ParcelableActivity> activities = data.subList(0, Math.min(items_limit, data.size()));
		try {
			final File file = JSONSerializer.getSerializationFile(getActivity(), getSavedActivitiesFileArgs());
			JSONSerializer.toFile(file, activities.toArray(new ParcelableActivity[activities.size()]));
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	protected abstract String[] getSavedActivitiesFileArgs();

}