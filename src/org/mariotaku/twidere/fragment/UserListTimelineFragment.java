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
import android.os.Bundle;
import android.support.v4.content.Loader;
import java.util.List;
import org.mariotaku.twidere.adapter.iface.IStatusesAdapter;
import org.mariotaku.twidere.loader.UserListTimelineLoader;
import org.mariotaku.twidere.model.ParcelableStatus;

public class UserListTimelineFragment extends ParcelableStatusesListFragment {

	@Override
	public Loader<List<ParcelableStatus>> newLoaderInstance(final Context context, final Bundle args) {
		if (args == null) return null;
		final int list_id = args.getInt(INTENT_KEY_LIST_ID, -1);
		final long account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
		final long max_id = args.getLong(INTENT_KEY_MAX_ID, -1);
		final long since_id = args.getLong(INTENT_KEY_SINCE_ID, -1);
		final long user_id = args.getLong(INTENT_KEY_USER_ID, -1);
		final String screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		final String list_name = args.getString(INTENT_KEY_LIST_NAME);
		final int tab_position = args.getInt(INTENT_KEY_TAB_POSITION, -1);
		return new UserListTimelineLoader(getActivity(), account_id, list_id, user_id, screen_name, list_name, max_id,
				since_id, getData(), getSavedStatusesFileArgs(), tab_position);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		final IStatusesAdapter<List<ParcelableStatus>> adapter = getListAdapter();
		adapter.setFiltersEnabled(true);
		adapter.setIgnoredFilterFields(false, false, false, false);
	}
	
	protected String[] getSavedStatusesFileArgs() {
		final Bundle args = getArguments();
		if (args == null) return null;
		final int list_id = args.getInt(INTENT_KEY_LIST_ID, -1);
		final long account_id = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
		final long user_id = args.getLong(INTENT_KEY_USER_ID, -1);
		final String screen_name = args.getString(INTENT_KEY_SCREEN_NAME);
		final String list_name = args.getString(INTENT_KEY_LIST_NAME);
		return new String[] { AUTHORITY_LIST_TIMELINE, "account" + account_id, "list_id" + list_id, "list_name" +
				list_name, "user" + user_id, "screen_name" + screen_name };
	}

}
