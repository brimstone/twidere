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

package org.mariotaku.twidere.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import org.mariotaku.twidere.Constants;
import org.mariotaku.twidere.R;

import static org.mariotaku.twidere.util.Utils.getBestBannerType;

/**
 * Lazy image loader for {@link ListView} and {@link GridView} etc.</br> </br>
 * Inspired by <a href="https://github.com/thest1/LazyList">LazyList</a>, this
 * class has extra features like image loading/caching image to
 * /mnt/sdcard/Android/data/[package name]/cache features.</br> </br> Requires
 * Android 2.2, you can modify {@link Context#getExternalCacheDir()} to other to
 * support Android 2.1 and below.
 * 
 * @author mariotaku
 * 
 */
public class ImageLoaderWrapper implements Constants {

	private final ImageLoader mImageLoader;
	private final DisplayImageOptions mProfileImageDisplayOptions, mImageDisplayOptions;

	public ImageLoaderWrapper(final ImageLoader loader) {
		mImageLoader = loader;
		final DisplayImageOptions.Builder profile_opts_builder = new DisplayImageOptions.Builder();
		profile_opts_builder.cacheInMemory();
		profile_opts_builder.cacheOnDisc();
		profile_opts_builder.showStubImage(R.drawable.ic_profile_image_default);
		profile_opts_builder.bitmapConfig(Bitmap.Config.ARGB_8888);
		profile_opts_builder.resetViewBeforeLoading();
		mProfileImageDisplayOptions = profile_opts_builder.build();
		final DisplayImageOptions.Builder image_opts_builder = new DisplayImageOptions.Builder();
		image_opts_builder.cacheInMemory();
		image_opts_builder.cacheOnDisc();
		image_opts_builder.bitmapConfig(Bitmap.Config.RGB_565);
		image_opts_builder.resetViewBeforeLoading();
		mImageDisplayOptions = image_opts_builder.build();
	}

	public void clearFileCache() {
		mImageLoader.clearDiscCache();
	}

	public void clearMemoryCache() {
		mImageLoader.clearMemoryCache();
	}

	public void displayPreviewImage(final ImageView view, final String url) {
		mImageLoader.displayImage(url, view, mImageDisplayOptions);
	}

	public void displayPreviewImage(final ImageView view, final String url, final ImageLoadingListener listener) {
		mImageLoader.displayImage(url, view, mImageDisplayOptions, listener);
	}

	public void displayProfileBanner(final ImageView view, final String base_url, final int width) {
		final String type = getBestBannerType(width);
		mImageLoader.displayImage(base_url + "/" + type, view, mImageDisplayOptions);
	}
	
	public void displayProfileImage(final ImageView view, final String url) {
		mImageLoader.displayImage(url, view, mProfileImageDisplayOptions);
	}

	public void reloadConnectivitySettings() {
	}

}
