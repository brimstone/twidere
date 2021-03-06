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

import org.mariotaku.twidere.R;
import org.mariotaku.twidere.model.Panes;
import org.mariotaku.twidere.view.SlidingPaneView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.BackStackEntryTrojan;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.BackStackEntry;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentManagerTrojan;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

@SuppressLint("Registered")
public class DualPaneActivity extends BaseDialogWhenLargeActivity implements OnBackStackChangedListener {

	private SharedPreferences mPreferences;

	private SlidingPaneView mSlidingPane;

	private Fragment mDetailsFragment;

	private boolean mDualPaneInPortrait, mDualPaneInLandscape;

	public Fragment getDetailsFragment() {
		return mDetailsFragment;
	}

	public final boolean isDualPaneMode() {
		return findViewById(PANE_LEFT) instanceof ViewGroup && findViewById(PANE_RIGHT) instanceof ViewGroup;
	}

	public final boolean isRightPaneUsed() {
		final FragmentManager fm = getSupportFragmentManager();
		final Fragment right_pane_fragment = fm.findFragmentById(PANE_RIGHT);
		return right_pane_fragment != null && right_pane_fragment.isAdded();
	}

	@Override
	public void onBackStackChanged() {
		if (isDualPaneMode()) {
			final FragmentManager fm = getSupportFragmentManager();
			final int count = fm.getBackStackEntryCount();
			final Fragment left_pane_fragment = fm.findFragmentById(PANE_LEFT);
			final Fragment right_pane_fragment = fm.findFragmentById(PANE_RIGHT);
			final View main_view = findViewById(R.id.main);
			final boolean left_pane_used = left_pane_fragment != null && left_pane_fragment.isAdded();
			final boolean right_pane_used = right_pane_fragment != null && right_pane_fragment.isAdded();
			if (count > 0) {
				final BackStackEntry entry = fm.getBackStackEntryAt(count - 1);
				if (entry == null) return;
				final Fragment fragment = BackStackEntryTrojan.getFragmentInBackStackRecord(entry);
				if (fragment instanceof Panes.Right) {
					showRightPane();
				} else if (fragment instanceof Panes.Left) {
					showLeftPane();
				}
			} else {
				if (fm.findFragmentById(R.id.main) != null || left_pane_used) {
					showLeftPane();
				} else if (right_pane_used) {
					showRightPane();
				}
			}
			if (main_view != null) {
				final int visibility = left_pane_used ? View.GONE : View.VISIBLE;
				// Visibility changed, so start animation.
				if (main_view.getVisibility() != visibility) {
					final Animation anim = AnimationUtils.loadAnimation(this, left_pane_used ? android.R.anim.fade_out : android.R.anim.fade_in);
					main_view.startAnimation(anim);
				}
				main_view.setVisibility(visibility);
			}
		}
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mSlidingPane = (SlidingPaneView) findViewById(R.id.sliding_pane);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		super.onCreate(savedInstanceState);
		final Resources res = getResources();
		final int orientation = res.getConfiguration().orientation;
		final int layout;
		final boolean default_dual_pane_mode = res.getBoolean(R.bool.default_dual_pane_mode);
		mDualPaneInPortrait = mPreferences.getBoolean(PREFERENCE_KEY_DUAL_PANE_IN_PORTRAIT, default_dual_pane_mode);
		mDualPaneInLandscape = mPreferences.getBoolean(PREFERENCE_KEY_DUAL_PANE_IN_LANDSCAPE, default_dual_pane_mode);
		switch (orientation) {
			case Configuration.ORIENTATION_LANDSCAPE:
				layout = mDualPaneInLandscape || shouldForceEnableDualPaneMode() ? getDualPaneLayoutRes() : getNormalLayoutRes();
				break;
			case Configuration.ORIENTATION_PORTRAIT:
				layout = mDualPaneInPortrait || shouldForceEnableDualPaneMode() ? getDualPaneLayoutRes() : getNormalLayoutRes();
				break;
			default:
				layout = getNormalLayoutRes();
				break;
		}
		setContentView(layout);
		if (mSlidingPane != null) {
			mSlidingPane.setRightPaneBackground(getPaneBackground());
		}
		getSupportFragmentManager().addOnBackStackChangedListener(this);
	}

	public final void showAtPane(final int pane, final Fragment fragment, final boolean addToBackStack) {
		if (isStateSaved()) return;
		final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out);
		switch (pane) {
			case PANE_LEFT: {
				showLeftPane();
				ft.replace(PANE_LEFT, fragment);
				break;
			}
			case PANE_RIGHT: {
				showRightPane();
				ft.replace(PANE_RIGHT, fragment);
				break;
			}
		}
		if (addToBackStack) {
			ft.addToBackStack(null);
		}
		ft.setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		ft.commit();
		mDetailsFragment = fragment;
	}

	public final void showFragment(final Fragment fragment, final boolean add_to_backstack) {
		if (fragment instanceof Panes.Right) {
			showAtPane(PANE_RIGHT, fragment, add_to_backstack);
		} else {
			showAtPane(PANE_LEFT, fragment, add_to_backstack);
		}
	}

	public final void showLeftPane() {
		if (mSlidingPane != null) {
			mSlidingPane.animateClose();
		}
	}

	public final void showRightPane() {
		if (mSlidingPane != null) {
			mSlidingPane.animateOpen();
		}
	}

	protected int getDualPaneLayoutRes() {
		return R.layout.base_dual_pane;
	}

	protected int getNormalLayoutRes() {
		return R.layout.base;
	}

	@Override
	protected void onStart() {
		final FragmentManager fm = getSupportFragmentManager();
		if (!isDualPaneMode() && !FragmentManagerTrojan.isStateSaved(fm)) {
			final int count = fm.getBackStackEntryCount();
			for (int i = 0; i < count; i++) {
				fm.popBackStackImmediate();
			}
		}
		super.onStart();
		final Resources res = getResources();
		final boolean def_dualpane = res.getBoolean(R.bool.default_dual_pane_mode);
		final boolean dual_pane_in_portrait = mPreferences.getBoolean(PREFERENCE_KEY_DUAL_PANE_IN_PORTRAIT, def_dualpane);
		final boolean dual_pane_in_landscape = mPreferences.getBoolean(PREFERENCE_KEY_DUAL_PANE_IN_LANDSCAPE, def_dualpane);
		final int orientation = res.getConfiguration().orientation;
		switch (orientation) {
			case Configuration.ORIENTATION_LANDSCAPE:
				if (mDualPaneInLandscape != dual_pane_in_landscape) {
					restart();
				}
				break;
			case Configuration.ORIENTATION_PORTRAIT:
				if (mDualPaneInPortrait != dual_pane_in_portrait) {
					restart();
				}
				break;
		}
	}

	@Override
	protected boolean shouldDisableDialogWhenLargeMode() {
		return true;
	}

	protected boolean shouldForceEnableDualPaneMode() {
		return false;
	}

	private int getPaneBackground() {
		final boolean dark = isDarkTheme(), solid = isSolidColorBackground();
		return dark ? solid ? android.R.color.black : R.drawable.background_holo_dark : solid ? android.R.color.white
				: R.drawable.background_holo_light;
	}
}
