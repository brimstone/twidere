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

package org.mariotaku.twidere.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.iface.IStatusesAdapter;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.model.ImageSpec;
import org.mariotaku.twidere.model.ParcelableStatus;
import org.mariotaku.twidere.util.ImageLoaderWrapper;
import org.mariotaku.twidere.util.MultiSelectManager;
import org.mariotaku.twidere.util.OnLinkClickHandler;
import org.mariotaku.twidere.util.TwidereLinkify;
import org.mariotaku.twidere.view.holder.StatusViewHolder;

import static android.text.format.DateUtils.getRelativeTimeSpanString;
import static org.mariotaku.twidere.model.ParcelableLocation.isValidLocation;
import static org.mariotaku.twidere.util.Utils.formatSameDayTime;
import static org.mariotaku.twidere.util.Utils.getAccountColor;
import static org.mariotaku.twidere.util.Utils.getAccountScreenName;
import static org.mariotaku.twidere.util.Utils.getAllAvailableImage;
import static org.mariotaku.twidere.util.Utils.getImagePreviewDisplayOptionInt;
import static org.mariotaku.twidere.util.Utils.getNameDisplayOptionInt;
import static org.mariotaku.twidere.util.Utils.getStatusBackground;
import static org.mariotaku.twidere.util.Utils.getStatusTypeIconRes;
import static org.mariotaku.twidere.util.Utils.getThemeColor;
import static org.mariotaku.twidere.util.Utils.getUserColor;
import static org.mariotaku.twidere.util.Utils.getUserTypeIconRes;
import static org.mariotaku.twidere.util.Utils.isFiltered;
import static org.mariotaku.twidere.util.Utils.openImage;
import static org.mariotaku.twidere.util.Utils.openUserProfile;
import android.database.sqlite.SQLiteDatabase;

public class ParcelableStatusesAdapter extends ArrayAdapter<ParcelableStatus> implements IStatusesAdapter<List<ParcelableStatus>>,
		OnClickListener, ImageLoadingListener {

	private final Context mContext;
	private final Resources mResources;
	private final ImageLoaderWrapper mLazyImageLoader;
	private final MultiSelectManager mMultiSelectManager;
	private final TwidereLinkify mLinkify;
	private final SQLiteDatabase mDatabase;
	private final Map<View, String> mLoadingViewsMap = new HashMap<View, String>();

	private final float mDensity;

	private boolean mDisplayProfileImage, mShowAccountColor, mShowAbsoluteTime, mGapDisallowed, mMultiSelectEnabled,
			mMentionsHighlightDisabled, mDisplaySensitiveContents, mIndicateMyStatusDisabled, mLinkHighlightingEnabled,
			mFastTimelineProcessingEnabled, mIsLastItemFiltered, mFiltersEnabled;
	private float mTextSize;
	private int mNameDisplayOption, mImagePreviewDisplayOption, mLinkHighlightStyle;
	private boolean mFilterIgnoreSource, mFilterIgnoreScreenName, mFilterIgnoreTextHtml, mFilterIgnoreTextPlain;
	
	public ParcelableStatusesAdapter(final Context context) {
		super(context, R.layout.status_list_item);
		mContext = context;
		mResources = context.getResources();
		final TwidereApplication application = TwidereApplication.getInstance(context);
		mMultiSelectManager = application.getMultiSelectManager();
		mLazyImageLoader = application.getImageLoaderWrapper();
		mDatabase = application.getSQLiteDatabase();
		mDensity = mResources.getDisplayMetrics().density;
		mLinkify = new TwidereLinkify(new OnLinkClickHandler(mContext));
	}

	public long findItemIdByPosition(final int position) {
		if (position >= 0 && position < getCount()) return getItem(position).id;
		return -1;
	}

	public int findItemPositionByStatusId(final long status_id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItem(i).id == status_id) return i;
		}
		return -1;
	}


	@Override
	public int getCount() {
		final int count = super.getCount();
		return mFiltersEnabled && mIsLastItemFiltered && count > 0 ? count - 1 : count;
	}

	@Override
	public long getItemId(final int position) {
		final ParcelableStatus item = getItem(position);
		return item != null ? item.id : -1;
	}

	@Override
	public ParcelableStatus getLastStatus() {
		if (super.getCount() == 0) return null;
		return getItem(super.getCount() - 1);
	}

	@Override
	public ParcelableStatus getStatus(final int position) {
		return getItem(position);
	}

	@Override
	public boolean isLastItemFiltered() {
		return mIsLastItemFiltered;
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		final View view = super.getView(position, convertView, parent);
		final Object tag = view.getTag();
		final StatusViewHolder holder;

		if (tag instanceof StatusViewHolder) {
			holder = (StatusViewHolder) tag;
		} else {
			holder = new StatusViewHolder(view);
			view.setTag(holder);
		}

		// Clear images in prder to prevent images in recycled view shown.
//		holder.profile_image.setImageDrawable(null);
//		holder.my_profile_image.setImageDrawable(null);
//		holder.image_preview.setImageDrawable(null);

		final ParcelableStatus status = getItem(position);

		final boolean show_gap = status.is_gap && !mGapDisallowed && position != getCount() - 1;

		holder.setShowAsGap(show_gap);

		if (!show_gap) {

			holder.setAccountColorEnabled(mShowAccountColor);

			if (mFastTimelineProcessingEnabled) {
				holder.text.setText(status.text_plain);
			} else if (mLinkHighlightingEnabled) {
				holder.text.setText(Html.fromHtml(status.text_html));
				mLinkify.applyAllLinks(holder.text, status.account_id, status.is_possibly_sensitive);
			} else {
				holder.text.setText(status.text_unescaped);
			}
			holder.text.setMovementMethod(null);

			if (mShowAccountColor) {
				holder.setAccountColor(getAccountColor(mContext, status.account_id));
			}
			final String retweeted_by_name = status.retweeted_by_name;
			final String retweeted_by_screen_name = status.retweeted_by_screen_name;

			if (mMultiSelectEnabled) {
				holder.setSelected(mMultiSelectManager.isStatusSelected(status.id));
			} else {
				holder.setSelected(false);
			}
			final String account_screen_name = getAccountScreenName(mContext, status.account_id);
			final boolean is_mention = mFastTimelineProcessingEnabled ? false : !TextUtils.isEmpty(status.text_plain) &&
					status.text_plain.toLowerCase().contains('@' + account_screen_name.toLowerCase());
			final boolean is_my_status = status.account_id == status.user_id;
			holder.setUserColor(getUserColor(mContext, status.user_id));
			holder.setHighlightColor(mFastTimelineProcessingEnabled ? 0 : getStatusBackground(
					mMentionsHighlightDisabled ? false : is_mention, status.is_favorite, status.is_retweet));
			holder.setTextSize(mTextSize);

			holder.setIsMyStatus(is_my_status && !mIndicateMyStatusDisabled);

			holder.name.setCompoundDrawablesWithIntrinsicBounds(0, 0,
					getUserTypeIconRes(status.user_is_verified, status.user_is_protected), 0);
			switch (mNameDisplayOption) {
				case NAME_DISPLAY_OPTION_CODE_NAME: {
					holder.name.setText(status.user_name);
					holder.screen_name.setText(null);
					holder.screen_name.setVisibility(View.GONE);
					break;
				}
				case NAME_DISPLAY_OPTION_CODE_SCREEN_NAME: {
					holder.name.setText("@" + status.user_screen_name);
					holder.screen_name.setText(null);
					holder.screen_name.setVisibility(View.GONE);
					break;
				}
				default: {
					holder.name.setText(status.user_name);
					holder.screen_name.setText("@" + status.user_screen_name);
					holder.screen_name.setVisibility(View.VISIBLE);
					break;
				}
			}
			if (mLinkHighlightingEnabled) {
				mLinkify.applyUserProfileLink(holder.name, status.account_id, status.user_id, status.user_screen_name);
				mLinkify.applyUserProfileLink(holder.screen_name, status.account_id, status.user_id, status.user_screen_name);
				holder.name.setMovementMethod(null);
				holder.screen_name.setMovementMethod(null);
			}
			if (mShowAbsoluteTime) {
				holder.time.setText(formatSameDayTime(mContext, status.timestamp));
			} else {
				holder.time.setText(getRelativeTimeSpanString(status.timestamp));
			}
			holder.time.setCompoundDrawablesWithIntrinsicBounds(0, 0, mFastTimelineProcessingEnabled ? 0
					: getStatusTypeIconRes(status.is_favorite, isValidLocation(status.location), status.has_media,
							status.is_possibly_sensitive), 0);
			holder.reply_retweet_status
					.setVisibility(status.in_reply_to_status_id != -1 || status.is_retweet ? View.VISIBLE : View.GONE);
			if (status.is_retweet && !TextUtils.isEmpty(retweeted_by_name)
					&& !TextUtils.isEmpty(retweeted_by_screen_name)) {
				if (mNameDisplayOption == NAME_DISPLAY_OPTION_CODE_SCREEN_NAME) {
					holder.reply_retweet_status.setText(status.retweet_count > 1 ? mContext.getString(
							R.string.retweeted_by_with_count, retweeted_by_screen_name, status.retweet_count - 1)
							: mContext.getString(R.string.retweeted_by, retweeted_by_screen_name));
				} else {
					holder.reply_retweet_status.setText(status.retweet_count > 1 ? mContext.getString(
							R.string.retweeted_by_with_count, retweeted_by_name, status.retweet_count - 1) : mContext
							.getString(R.string.retweeted_by, retweeted_by_name));
				}
				holder.reply_retweet_status.setText(status.retweet_count > 1 ? mContext.getString(
						R.string.retweeted_by_with_count, retweeted_by_name, status.retweet_count - 1) : mContext
						.getString(R.string.retweeted_by, retweeted_by_name));
				holder.reply_retweet_status.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_indicator_retweet, 0,
						0, 0);
			} else if (status.in_reply_to_status_id > 0 && !TextUtils.isEmpty(status.in_reply_to_screen_name)) {
				holder.reply_retweet_status.setText(mContext.getString(R.string.in_reply_to,
						status.in_reply_to_screen_name));
				holder.reply_retweet_status.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_indicator_reply, 0,
						0, 0);
			}
			if (mDisplayProfileImage) {
				mLazyImageLoader.displayProfileImage(holder.my_profile_image, status.user_profile_image_url);
				mLazyImageLoader.displayProfileImage(holder.profile_image, status.user_profile_image_url);
				holder.profile_image.setTag(position);
				holder.my_profile_image.setTag(position);
			} else {
				holder.profile_image.setVisibility(View.GONE);
				holder.my_profile_image.setVisibility(View.GONE);
			}
			final boolean has_preview = mFastTimelineProcessingEnabled ? false
					: mImagePreviewDisplayOption != IMAGE_PREVIEW_DISPLAY_OPTION_CODE_NONE
							&& status.has_media && status.image_preview_url != null;
			holder.image_preview_container.setVisibility(!mFastTimelineProcessingEnabled && has_preview ? View.VISIBLE
					: View.GONE);
			if (has_preview) {
				holder.setImagePreviewDisplayOption(mImagePreviewDisplayOption);
				if (status.is_possibly_sensitive && !mDisplaySensitiveContents) {
					holder.image_preview.setImageResource(R.drawable.image_preview_nsfw);
					holder.image_preview_progress.setVisibility(View.GONE);
				} else if (!status.image_preview_url.equals(mLoadingViewsMap.get(holder.image_preview))) {
					mLazyImageLoader.displayPreviewImage(holder.image_preview, status.image_preview_url, this);
				}
				holder.image_preview.setTag(position);
			}
		}
		holder.profile_image.setOnClickListener(mMultiSelectEnabled ? null : this);
		holder.my_profile_image.setOnClickListener(mMultiSelectEnabled ? null : this);
		holder.image_preview.setOnClickListener(mMultiSelectEnabled ? null : this);
		return view;
	}

	@Override
	public void onClick(final View view) {
		if (mMultiSelectEnabled) return;
		final Object tag = view.getTag();
		final ParcelableStatus status = tag instanceof Integer ? getStatus((Integer) tag) : null;
		if (status == null) return;
		switch (view.getId()) {
			case R.id.image_preview: {
				final ImageSpec spec = getAllAvailableImage(status.image_original_url, true);
				if (spec != null) {
					openImage(mContext, spec.image_full_url, spec.image_original_url, status.is_possibly_sensitive);
				} else {
					openImage(mContext, status.image_original_url, null, status.is_possibly_sensitive);
				}
				break;
			}
			case R.id.my_profile_image:
			case R.id.profile_image: {
				if (mContext instanceof Activity) {
					openUserProfile((Activity) mContext, status.account_id, status.user_id, status.user_screen_name);
				}
				break;
			}
		}
	}

	@Override
	public void onLoadingStarted(final String url, final View view) {
		if (view == null || url == null || url.equals(mLoadingViewsMap.get(view))) return;
		mLoadingViewsMap.put(view, url);
		final View parent = (View) view.getParent();
		final ProgressBar progress = (ProgressBar) parent.findViewById(R.id.image_preview_progress);
		if (progress != null) {
			progress.setVisibility(View.VISIBLE);
			progress.setIndeterminate(true);
			progress.setMax(100);
		}
	}

	@Override
	public void onLoadingFailed(final String url, final View view, final FailReason reason) {
		if (view == null) return;
		mLoadingViewsMap.remove(view);
		final View parent = (View) view.getParent();
		final View progress = parent.findViewById(R.id.image_preview_progress);
		if (progress != null) {
			progress.setVisibility(View.GONE);
		}
	}

	@Override
	public void onLoadingComplete(final String url, final View view, final Bitmap bitmap) {
		if (view == null) return;
		mLoadingViewsMap.remove(view);
		final View parent = (View) view.getParent();
		final View progress = parent.findViewById(R.id.image_preview_progress);
		if (progress != null) {
			progress.setVisibility(View.GONE);
		}
	}

	@Override
	public void onLoadingCancelled(final String url, final View view) {
		if (view == null || url == null || url.equals(mLoadingViewsMap.get(view))) return;
		mLoadingViewsMap.remove(view);
		final View parent = (View) view.getParent();
		final View progress = parent.findViewById(R.id.image_preview_progress);
		if (progress != null) {
			progress.setVisibility(View.GONE);
		}
	}

	@Override
	public void onLoadingProgressChanged(String imageUri, View view, int current, int total) {
		if (total == 0) return;
		final View parent = (View) view.getParent();
		final ProgressBar progress = (ProgressBar) parent.findViewById(R.id.image_preview_progress);
		if (progress != null) {
			progress.setIndeterminate(false);
			progress.setProgress(100 * current / total);
		}
	}

	@Override
	public void setData(final List<ParcelableStatus> data) {
		clear();
		if (data != null && !data.isEmpty()) {
			addAll(data);
			notifyDataSetChanged();
		}
		rebuildFilterInfo();
	}

	@Override
	public void setDisplayProfileImage(final boolean display) {
		if (display == mDisplayProfileImage) return;
		mDisplayProfileImage = display;
		notifyDataSetChanged();
	}

	@Override
	public void setDisplaySensitiveContents(final boolean display) {
		if (display == mDisplaySensitiveContents) return;
		mDisplaySensitiveContents = display;
		notifyDataSetChanged();
	}

	@Override
	public void setFastTimelineProcessingEnabled(final boolean enabled) {
		if (mFastTimelineProcessingEnabled == enabled) return;
		mFastTimelineProcessingEnabled = enabled;
		notifyDataSetChanged();
	}

	@Override
	public void setFiltersEnabled(boolean enabled) {
		if (mFiltersEnabled == enabled) return;
		mFiltersEnabled = enabled;
		rebuildFilterInfo();
		notifyDataSetChanged();
	}

	@Override
	public void setGapDisallowed(final boolean disallowed) {
		if (mGapDisallowed == disallowed) return;
		mGapDisallowed = disallowed;
		notifyDataSetChanged();
	}

	@Override
	public void setIgnoredFilterFields(boolean text_plain, boolean text_html, boolean screen_name, boolean source) {
		mFilterIgnoreTextPlain = text_plain;
		mFilterIgnoreTextHtml = text_html;
		mFilterIgnoreScreenName = screen_name;
		mFilterIgnoreSource = source;
		rebuildFilterInfo();
		notifyDataSetChanged();
	}

	@Override
	public void setIndicateMyStatusDisabled(final boolean disable) {
		if (mIndicateMyStatusDisabled == disable) return;
		mIndicateMyStatusDisabled = disable;
		notifyDataSetChanged();
	}

	@Override
	public void setImagePreviewDisplayOption(final String option) {
		final int option_int = getImagePreviewDisplayOptionInt(option);
		if (option_int == mImagePreviewDisplayOption) return;
		mImagePreviewDisplayOption = option_int;
		notifyDataSetChanged();
	}

	@Override
	public void setLinkHightlightingEnabled(final boolean enable) {
		if (mLinkHighlightingEnabled == enable) return;
		mLinkHighlightingEnabled = enable;
		notifyDataSetChanged();
	}

	@Override
	public void setLinkUnderlineOnly(boolean underline_only) {
		final int style = underline_only ? TwidereLinkify.HIGHLIGHT_STYLE_UNDERLINE : TwidereLinkify.HIGHLIGHT_STYLE_COLOR;
		if (mLinkHighlightStyle == style) return;
		mLinkify.setHighlightStyle(style);
		mLinkHighlightStyle = style;
		notifyDataSetChanged();
	}

	@Override
	public void setMentionsHightlightDisabled(final boolean disable) {
		if (disable == mMentionsHighlightDisabled) return;
		mMentionsHighlightDisabled = disable;
		notifyDataSetChanged();
	}

	@Override
	public void setMultiSelectEnabled(final boolean multi) {
		if (mMultiSelectEnabled == multi) return;
		mMultiSelectEnabled = multi;
		notifyDataSetChanged();
	}

	@Override
	public void setNameDisplayOption(final String option) {
		final int option_int = getNameDisplayOptionInt(option);
		if (option_int == mNameDisplayOption) return;
		mNameDisplayOption = option_int;
		notifyDataSetChanged();
	}

	@Override
	public void setShowAbsoluteTime(final boolean show) {
		if (show == mShowAbsoluteTime) return;
		mShowAbsoluteTime = show;
		notifyDataSetChanged();
	}

	@Override
	public void setShowAccountColor(final boolean show) {
		if (show == mShowAccountColor) return;
		mShowAccountColor = show;
		notifyDataSetChanged();
	}

	@Override
	public void setTextSize(final float text_size) {
		if (text_size == mTextSize) return;
		mTextSize = text_size;
		notifyDataSetChanged();
	}
	
	private void rebuildFilterInfo() {
		if (!isEmpty()) {
			final ParcelableStatus last = getItem(super.getCount() - 1);
			final String text_plain = mFilterIgnoreTextPlain ? null : last.text_plain;
			final String text_html = mFilterIgnoreTextHtml ? null : last.text_html;
			final String screen_name = mFilterIgnoreScreenName ? null : last.user_screen_name;
			final String source = mFilterIgnoreSource ? null : last.source;
			mIsLastItemFiltered = isFiltered(mDatabase, text_plain, text_html, screen_name, source);
		} else {
			mIsLastItemFiltered = false;
		}
	}
}
