package app.tuxguitar.android.drawer;

import android.content.res.Configuration;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.android.R;
import app.tuxguitar.android.activity.TGActivity;
import app.tuxguitar.android.fragment.TGFragmentController;
import app.tuxguitar.util.TGContext;

import app.tuxguitar.android.view.popup.TGMorphDialog;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;

public class TGDrawerManager {

	private TGActivity activity;
	private TGDrawerViewBuilder drawerBuilder;
	private ViewGroup drawerView;
	private DrawerLayout drawerLayout;
	private ActionBarDrawerToggle drawerToggle;
	private boolean open;
	private TGMorphDialog menu;

	public TGDrawerManager(TGActivity activity) {
		this.activity = activity;
	}

	public void initialize() {
		this.drawerView = (ViewGroup) this.activity.findViewById(R.id.left_drawer);

		this.drawerLayout = (DrawerLayout) this.activity.findViewById(R.id.root_layout);
		this.drawerToggle = new ActionBarDrawerToggle(this.activity, this.drawerLayout, R.string.app_name, R.string.app_name) {
			public void onDrawerClosed(View view) {
				TGDrawerManager.this.onVisibilityChanged();
			}

			public void onDrawerOpened(View drawerView) {
				TGDrawerManager.this.onVisibilityChanged();
			}
		};
		this.drawerLayout.addDrawerListener(this.drawerToggle);

		this.appendListeners();
	}

	public void appendListeners() {
		TGDrawerEventListener drawerListener = new TGDrawerEventListener(this);
		TGDrawerActionInterceptor drawerInterceptor = new TGDrawerActionInterceptor(this);

		TGActionManager tgActionManager = TGActionManager.getInstance(this.findContext());
		tgActionManager.addPostExecutionListener(drawerListener);
		tgActionManager.addInterceptor(drawerInterceptor);

		this.activity.getNavigationManager().addNavigationListener(drawerListener);
	}

	public void syncState() {
		this.drawerToggle.syncState();
	}

	public void closeDrawer() {
		this.drawerLayout.closeDrawer(this.drawerView);
		if( this.menu != null ) {
			this.menu.dismissAnimated();
		}
	}

	/** Opens the main menu growing out of the button that was tapped. */
	public void openMenu(View anchor) {
		if( this.menu != null && this.menu.isOpen() ) {
			return;
		}
		this.menu = TGMorphDialog.show(this.activity, anchor, TGMorphDialog.KIND_MENU, new Runnable() {
			public void run() {
				TGDrawerManager.this.menu = null;
				TGDrawerManager.this.open = false;
			}
		});
		if( this.menu != null ) {
			this.open = true;
			this.activity.updateCache(true);
		}
	}

	public void onConfigurationChanged(Configuration configuration) {
		this.drawerToggle.onConfigurationChanged(configuration);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if( item.getItemId() == android.R.id.home ) {
			if (this.drawerToggle.isDrawerIndicatorEnabled()) {
				return this.drawerToggle.onOptionsItemSelected(item);
			}
			return this.activity.getNavigationManager().callOpenPreviousFragment();
		}
		return false;
	}

	public void onOpenFragment(TGFragmentController<?> controller) {
		if( this.drawerView.getChildCount() > 0 ) {
			this.drawerView.removeAllViews();
		}
		if( this.drawerBuilder != null ) {
			this.drawerBuilder.onOpenFragment(controller, this.drawerView);
		}

		boolean available = (this.drawerView.getChildCount() > 0);
		this.drawerToggle.setDrawerIndicatorEnabled(available);
		this.drawerLayout.setDrawerLockMode(available ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
	}

	public void onVisibilityChanged() {
		this.open = this.drawerLayout.isDrawerOpen(this.drawerView);

		if( this.open ) {
			this.activity.updateCache(true);
		}
	}

	public void setDrawerBuilder(TGDrawerViewBuilder drawerBuilder) {
		this.drawerBuilder = drawerBuilder;
	}

	public TGContext findContext() {
		return this.activity.findContext();
	}

	public boolean isOpen() {
		return open;
	}
}
