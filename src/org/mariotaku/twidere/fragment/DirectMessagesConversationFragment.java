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

import static android.text.TextUtils.isEmpty;
import static org.mariotaku.twidere.util.Utils.buildDirectMessageConversationUri;
import static org.mariotaku.twidere.util.Utils.openUserProfile;
import static org.mariotaku.twidere.util.Utils.parseString;
import static org.mariotaku.twidere.util.Utils.showInfoMessage;
import static org.mariotaku.twidere.util.Utils.showOkMessage;

import org.mariotaku.popupmenu.PopupMenu;
import org.mariotaku.popupmenu.PopupMenu.OnMenuItemClickListener;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.AutoCompleteAdapter;
import org.mariotaku.twidere.adapter.DirectMessagesConversationAdapter;
import org.mariotaku.twidere.model.Account;
import org.mariotaku.twidere.model.Panes;
import org.mariotaku.twidere.model.ParcelableDirectMessage;
import org.mariotaku.twidere.provider.TweetStore.DirectMessages;
import org.mariotaku.twidere.util.AsyncTwitterWrapper;
import org.mariotaku.twidere.util.ClipboardUtils;
import org.mariotaku.twidere.view.holder.DirectMessageConversationViewHolder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.twitter.Validator;

public class DirectMessagesConversationFragment extends BaseListFragment implements LoaderCallbacks<Cursor>,
		OnItemClickListener, OnItemLongClickListener, OnMenuItemClickListener, TextWatcher, OnClickListener,
		Panes.Right, OnItemSelectedListener, OnEditorActionListener {

	private final Validator mValidator = new Validator();
	private AsyncTwitterWrapper mTwitterWrapper;
	private SharedPreferences mPreferences;

	private ListView mListView;
	private EditText mEditText;
	private TextView mTextCount;
	private AutoCompleteTextView mEditScreenName;
	private ImageButton mSendButton;
	private Button mScreenNameConfirmButton;
	private View mConversationContainer, mScreenNameContainer;
	private Spinner mAccountSelector;

	private PopupMenu mPopupMenu;

	private ParcelableDirectMessage mSelectedDirectMessage;
	private final Bundle mArguments = new Bundle();
	private Account mSelectedAccount;

	private DirectMessagesConversationAdapter mAdapter;
	private AutoCompleteAdapter mUserAutoCompleteAdapter;
	private AccountsAdapter mAccountsAdapter;

	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (getActivity() == null || !isAdded() || isDetached()) return;
			final String action = intent.getAction();
			if (BROADCAST_RECEIVED_DIRECT_MESSAGES_DATABASE_UPDATED.equals(action)
					|| BROADCAST_SENT_DIRECT_MESSAGES_DATABASE_UPDATED.equals(action)) {
				getLoaderManager().restartLoader(0, mArguments, DirectMessagesConversationFragment.this);
			} else if (BROADCAST_RECEIVED_DIRECT_MESSAGES_REFRESHED.equals(action)
					|| BROADCAST_SENT_DIRECT_MESSAGES_REFRESHED.equals(action)) {
				getLoaderManager().restartLoader(0, mArguments, DirectMessagesConversationFragment.this);
			} else if (BROADCAST_TASK_STATE_CHANGED.equals(action)) {
				setProgressBarIndeterminateVisibility(mTwitterWrapper.isReceivedDirectMessagesRefreshing()
						|| mTwitterWrapper.isSentDirectMessagesRefreshing());
			}
		}
	};

	private final TextWatcher mScreenNameTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(final Editable s) {

		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {

		}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
			if (mScreenNameConfirmButton == null) return;
			mScreenNameConfirmButton.setEnabled(s.length() > 0 && s.length() < 20);
		}
	};

	@Override
	public void afterTextChanged(final Editable s) {

	}

	@Override
	public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {

	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mTwitterWrapper = getTwitterWrapper();

		mAdapter = new DirectMessagesConversationAdapter(getActivity());
		setListAdapter(mAdapter);
		mListView = getListView();
		mListView.setFastScrollEnabled(mPreferences.getBoolean(PREFERENCE_KEY_FAST_SCROLL_THUMB, false));
		mListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		mListView.setStackFromBottom(true);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		final Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState.getBundle(INTENT_KEY_DATA);
		if (args != null) {
			mArguments.putAll(args);
		}
		setListShownNoAnimation(false);
		getLoaderManager().initLoader(0, mArguments, this);

		if (mPreferences.getBoolean(PREFERENCE_KEY_QUICK_SEND, false)) {
			mEditText.setOnEditorActionListener(this);
		}
		mEditText.addTextChangedListener(this);
		final String text = savedInstanceState != null ? savedInstanceState.getString(INTENT_KEY_TEXT) : null;
		if (text != null) {
			mEditText.setText(text);
		}

		mAccountsAdapter = new AccountsAdapter(getActivity());
		mAccountSelector.setAdapter(mAccountsAdapter);
		mAccountSelector.setOnItemSelectedListener(this);

		mUserAutoCompleteAdapter = new AutoCompleteAdapter(getActivity());

		mEditScreenName.addTextChangedListener(mScreenNameTextWatcher);
		mEditScreenName.setAdapter(mUserAutoCompleteAdapter);

		mSendButton.setOnClickListener(this);
		mSendButton.setEnabled(false);
		mScreenNameConfirmButton.setOnClickListener(this);
		mScreenNameConfirmButton.setEnabled(false);
		updateTextCount();
	}

	@Override
	public void onClick(final View view) {
		switch (view.getId()) {
			case R.id.send: {
				send();
				break;
			}
			case R.id.screen_name_confirm: {
				final CharSequence text = mEditScreenName.getText();
				if (text == null || mSelectedAccount == null) return;
				final String screen_name = text.toString();
				mArguments.putString(INTENT_KEY_SCREEN_NAME, screen_name);
				mArguments.putLong(INTENT_KEY_ACCOUNT_ID, mSelectedAccount.account_id);
				setListShownNoAnimation(false);
				getLoaderManager().restartLoader(0, mArguments, this);
				break;
			}
		}

	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		// if (args == null || !args.containsKey(INTENT_KEY_ACCOUNT_ID))
		// return new CursorLoader(getActivity(), TweetStore.NULL_CONTENT_URI,
		// null, null, null, null);
		final String[] cols = new String[] { DirectMessages._ID, DirectMessages.ACCOUNT_ID, DirectMessages.MESSAGE_ID,
				DirectMessages.MESSAGE_TIMESTAMP, DirectMessages.SENDER_ID, DirectMessages.RECIPIENT_ID,
				DirectMessages.IS_OUTGOING, DirectMessages.TEXT_HTML, DirectMessages.SENDER_NAME,
				DirectMessages.RECIPIENT_NAME, DirectMessages.SENDER_SCREEN_NAME, DirectMessages.RECIPIENT_SCREEN_NAME,
				DirectMessages.SENDER_PROFILE_IMAGE_URL, DirectMessages.RECIPIENT_PROFILE_IMAGE_URL };
		final long account_id = args != null ? args.getLong(INTENT_KEY_ACCOUNT_ID, -1) : -1;
		final long conversation_id = args != null ? args.getLong(INTENT_KEY_CONVERSATION_ID, -1) : -1;
		final String screen_name = args != null ? args.getString(INTENT_KEY_SCREEN_NAME) : null;
		mConversationContainer
				.setVisibility(account_id <= 0 || conversation_id <= 0 && isEmpty(screen_name) ? View.GONE
						: View.VISIBLE);
		mScreenNameContainer
				.setVisibility(account_id <= 0 || conversation_id <= 0 && isEmpty(screen_name) ? View.VISIBLE
						: View.GONE);
		final Uri uri = buildDirectMessageConversationUri(account_id, conversation_id, screen_name);
		return new CursorLoader(getActivity(), uri, cols, null, null, DirectMessages.Conversation.DEFAULT_SORT_ORDER);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.direct_messages_conversation, null);
		final FrameLayout list_container = (FrameLayout) view.findViewById(R.id.list_container);
		final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		list_container.addView(super.onCreateView(inflater, container, savedInstanceState), lp);
		mEditText = (EditText) view.findViewById(R.id.edit_text);
		mTextCount = (TextView) view.findViewById(R.id.text_count);
		mSendButton = (ImageButton) view.findViewById(R.id.send);
		mConversationContainer = view.findViewById(R.id.conversation_container);
		mScreenNameContainer = view.findViewById(R.id.screen_name_container);
		mEditScreenName = (AutoCompleteTextView) view.findViewById(R.id.edit_screen_name);
		mAccountSelector = (Spinner) view.findViewById(R.id.account_selector);
		mScreenNameConfirmButton = (Button) view.findViewById(R.id.screen_name_confirm);
		return view;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onEditorAction(final TextView view, final int actionId, final KeyEvent event) {
		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_ENTER: {
				send();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object tag = view.getTag();
		if (tag instanceof DirectMessageConversationViewHolder) {
		}
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		final Object tag = view.getTag();
		if (tag instanceof DirectMessageConversationViewHolder) {
			final ParcelableDirectMessage dm = mSelectedDirectMessage = mAdapter.findItem(id);
			mPopupMenu = PopupMenu.getInstance(getActivity(), view);
			mPopupMenu.inflate(R.menu.action_direct_message);
			final Menu menu = mPopupMenu.getMenu();
			final MenuItem view_profile_item = menu.findItem(MENU_VIEW_PROFILE);
			if (view_profile_item != null && dm != null) {
				view_profile_item.setVisible(dm.account_id != dm.sender_id);
			}
			mPopupMenu.setOnMenuItemClickListener(this);
			mPopupMenu.show();
			return true;
		}
		return false;
	}

	@Override
	public void onItemSelected(final AdapterView<?> parent, final View view, final int pos, final long id) {
		mSelectedAccount = null;
		if (mAccountsAdapter == null) return;
		mSelectedAccount = mAccountsAdapter.getItem(pos);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		mAdapter.swapCursor(cursor);
		setListShown(true);
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		if (mSelectedDirectMessage != null) {
			final long message_id = mSelectedDirectMessage.id;
			final long account_id = mSelectedDirectMessage.account_id;
			switch (item.getItemId()) {
				case MENU_DELETE: {
					mTwitterWrapper.destroyDirectMessage(account_id, message_id);
					break;
				}
				case MENU_COPY: {
					if (ClipboardUtils.setText(getActivity(), Html.fromHtml(mSelectedDirectMessage.text_html))) {
						showOkMessage(getActivity(), R.string.text_copied, false);
					}
					break;
				}
				case MENU_VIEW_PROFILE: {
					if (mSelectedDirectMessage == null) return false;
					openUserProfile(getActivity(), account_id, mSelectedDirectMessage.sender_id,
							mSelectedDirectMessage.sender_screen_name);
					break;
				}
				default:
					return false;
			}
		}
		return true;
	}

	@Override
	public void onNothingSelected(final AdapterView<?> view) {

	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		if (mEditText != null) {
			outState.putString(INTENT_KEY_TEXT, parseString(mEditText.getText()));
		}
		outState.putBundle(INTENT_KEY_DATA, mArguments);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(BROADCAST_TASK_STATE_CHANGED);
		filter.addAction(BROADCAST_RECEIVED_DIRECT_MESSAGES_DATABASE_UPDATED);
		filter.addAction(BROADCAST_SENT_DIRECT_MESSAGES_DATABASE_UPDATED);
		filter.addAction(BROADCAST_RECEIVED_DIRECT_MESSAGES_REFRESHED);
		filter.addAction(BROADCAST_SENT_DIRECT_MESSAGES_REFRESHED);
		registerReceiver(mStatusReceiver, filter);

		final float text_size = mPreferences.getInt(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		final String name_display_option = mPreferences.getString(PREFERENCE_KEY_NAME_DISPLAY_OPTION,
				NAME_DISPLAY_OPTION_BOTH);
		mAdapter.setDisplayProfileImage(display_profile_image);
		mAdapter.setTextSize(text_size);
		mAdapter.setNameDisplayOption(name_display_option);
	}

	@Override
	public void onStop() {
		unregisterReceiver(mStatusReceiver);
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		super.onStop();
	}

	@Override
	public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
		updateTextCount();
		if (mSendButton == null || s == null) return;
		mSendButton.setEnabled(mValidator.isValidTweet(s.toString()));
	}

	public void showConversation(final long account_id, final long conversation_id, final String screen_name) {
		mArguments.putLong(INTENT_KEY_ACCOUNT_ID, account_id);
		mArguments.putLong(INTENT_KEY_CONVERSATION_ID, conversation_id);
		mArguments.putString(INTENT_KEY_SCREEN_NAME, screen_name);
		getLoaderManager().restartLoader(0, mArguments, this);
	}

	private void send() {
		final Editable text = mEditText.getText();
		if (isEmpty(text)) return;
		final String message = text.toString();
		if (mValidator.isValidTweet(message)) {
			final long account_id = mArguments.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			final long conversation_id = mArguments.getLong(INTENT_KEY_CONVERSATION_ID, -1);
			final String screen_name = mArguments.getString(INTENT_KEY_SCREEN_NAME);
			mTwitterWrapper.sendDirectMessage(account_id, screen_name, conversation_id, message);
			text.clear();
		}
	}

	private void updateTextCount() {
		if (mTextCount != null) {
			final String text = mEditText != null ? parseString(mEditText.getText()) : null;
			final int count = mValidator.getTweetLength(text);
			final float hue = count < Validator.MAX_TWEET_LENGTH ? count >= Validator.MAX_TWEET_LENGTH - 10 ? 5 * (Validator.MAX_TWEET_LENGTH - count)
					: 50
					: 0;
			final float[] hsv = new float[] { hue, 1.0f, 1.0f };
			mTextCount
					.setTextColor(count >= Validator.MAX_TWEET_LENGTH - 10 ? Color.HSVToColor(0x80, hsv) : 0x80808080);
			mTextCount.setText(parseString(Validator.MAX_TWEET_LENGTH - count));
		}
	}

	private static class AccountsAdapter extends ArrayAdapter<Account> {

		public AccountsAdapter(final Context context) {
			super(context, R.layout.spinner_item, Account.getAccounts(context, true));
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}

	}

}
