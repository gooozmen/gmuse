package app.tuxguitar.android.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.android.R;
import app.tuxguitar.android.action.TGActionAdapterManager;
import app.tuxguitar.android.action.impl.caret.TGGoDownAction;
import app.tuxguitar.android.action.impl.caret.TGGoLeftAction;
import app.tuxguitar.android.action.impl.caret.TGGoRightAction;
import app.tuxguitar.android.action.impl.caret.TGGoUpAction;
import app.tuxguitar.android.action.impl.gui.TGBackAction;
import app.tuxguitar.android.action.impl.gui.TGFinishAction;
import app.tuxguitar.android.action.impl.gui.TGOpenMenuAction;
import app.tuxguitar.android.action.impl.intent.TGProcessIntentAction;
import app.tuxguitar.android.action.impl.transport.TGTransportPlayAction;
import app.tuxguitar.android.action.impl.view.TGShowSmartMenuAction;
import app.tuxguitar.android.drawer.TGDrawerManager;
import app.tuxguitar.android.error.TGErrorHandlerImpl;
import app.tuxguitar.android.fragment.impl.TGMainFragmentController;
import app.tuxguitar.android.menu.controller.TGMenuContextualInflater;
import app.tuxguitar.android.menu.controller.impl.contextual.TGDurationMenu;
import app.tuxguitar.android.navigation.TGNavigationManager;
import app.tuxguitar.android.properties.TGPropertiesAdapter;
import app.tuxguitar.android.resource.TGResourceLoaderImpl;
import app.tuxguitar.android.synchronizer.TGSynchronizerControllerImpl;
import app.tuxguitar.android.transport.TGTransportAdapter;
import app.tuxguitar.android.variables.TGVarAdapter;
import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.editor.action.duration.TGChangeDottedDurationAction;
import app.tuxguitar.editor.action.duration.TGDecrementDurationAction;
import app.tuxguitar.editor.action.duration.TGIncrementDurationAction;
import app.tuxguitar.editor.action.effect.TGChangeLetRingAction;
import app.tuxguitar.editor.action.effect.TGChangePalmMuteAction;
import app.tuxguitar.editor.action.effect.TGChangeVibratoNoteAction;
import app.tuxguitar.editor.action.file.TGLoadTemplateAction;
import app.tuxguitar.editor.action.note.TGDecrementNoteSemitoneAction;
import app.tuxguitar.editor.action.note.TGDeleteNoteOrRestAction;
import app.tuxguitar.editor.action.note.TGIncrementNoteSemitoneAction;
import app.tuxguitar.editor.action.note.TGInsertRestBeatAction;
import app.tuxguitar.editor.action.note.TGSetNoteFretNumberAction;
import app.tuxguitar.editor.action.note.TGShiftNoteDownAction;
import app.tuxguitar.editor.action.note.TGShiftNoteUpAction;
import app.tuxguitar.resource.TGResourceBundle;
import app.tuxguitar.resource.TGResourceManager;
import app.tuxguitar.song.helpers.tuning.TuningManager;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGAbstractContext;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGLock;
import app.tuxguitar.util.TGMessagesManager;
import app.tuxguitar.util.TGSynchronizer;
import app.tuxguitar.util.error.TGErrorManager;
import app.tuxguitar.util.plugin.TGPluginManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Locale;

public class TGActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

	private static String LANGUAGE_RESOURCE = "lang/messages";

	private boolean destroyed;
	private TGContext context;
	private TGNavigationManager navigationManager;
	private TGDrawerManager drawerManager;
	private TGActivityActionBarController actionBar;
	private TGActivityResultManager resultManager;
	private TGActivityPermissionResultManager permissionResultManager;
	private boolean maintainDisplayON;

	public TGActivity() {
		this.resultManager = new TGActivityResultManager();
		this.permissionResultManager = new TGActivityPermissionResultManager();
		this.navigationManager = new TGNavigationManager(this);
		this.drawerManager = new TGDrawerManager(this);
		this.actionBar = new TGActivityActionBarController(this);
		this.maintainDisplayON = false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		TGActionManager tgActionManager = TGActionManager.getInstance(context);
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:
				if (event.isShiftPressed()) {
					tgActionManager.execute(TGShiftNoteUpAction.NAME);
				} else {
					tgActionManager.execute(TGGoUpAction.NAME);
				}
				return true;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (event.isShiftPressed()) {
					tgActionManager.execute(TGShiftNoteDownAction.NAME);
				} else {
					tgActionManager.execute(TGGoDownAction.NAME);
				}
				return true;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				tgActionManager.execute(TGGoLeftAction.NAME);
				return true;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				tgActionManager.execute(TGGoRightAction.NAME);
				return true;
			case KeyEvent.KEYCODE_DEL:
				tgActionManager.execute(TGDeleteNoteOrRestAction.NAME);
				return true;
			case KeyEvent.KEYCODE_TAB:
				tgActionManager.execute(TGInsertRestBeatAction.NAME);
				return true;
			case KeyEvent.KEYCODE_MINUS:
				if (event.isShiftPressed()) {
					tgActionManager.execute(TGDecrementNoteSemitoneAction.NAME);
				} else {
					tgActionManager.execute(TGDecrementDurationAction.NAME);
				}
				return true;
			case KeyEvent.KEYCODE_EQUALS:
				if (event.isShiftPressed()) {
					tgActionManager.execute(TGIncrementNoteSemitoneAction.NAME);
				} else {
					tgActionManager.execute(TGIncrementDurationAction.NAME);
				}
				return true;
			case KeyEvent.KEYCODE_0:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(0));
				return true;
			case KeyEvent.KEYCODE_1:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(1));
				return true;
			case KeyEvent.KEYCODE_2:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(2));
				return true;
			case KeyEvent.KEYCODE_3:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(3));
				return true;
			case KeyEvent.KEYCODE_4:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(4));
				return true;
			case KeyEvent.KEYCODE_5:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(5));
				return true;
			case KeyEvent.KEYCODE_6:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(6));
				return true;
			case KeyEvent.KEYCODE_7:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(7));
				return true;
			case KeyEvent.KEYCODE_8:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(8));
				return true;
			case KeyEvent.KEYCODE_9:
				tgActionManager.execute(TGSetNoteFretNumberAction.getActionName(9));
				return true;
			case KeyEvent.KEYCODE_PERIOD:
				tgActionManager.execute(TGChangeDottedDurationAction.NAME);
				return true;
			case KeyEvent.KEYCODE_D:
				TGDurationMenu menu = new TGDurationMenu(this);
				TGActionContext ctx = tgActionManager.createActionContext();
				ctx.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_CONTROLLER, menu);
				ctx.setAttribute(TGOpenMenuAction.ATTRIBUTE_MENU_ACTIVITY, this);
				tgActionManager.execute(TGOpenMenuAction.NAME, ctx);
				return true;
			case KeyEvent.KEYCODE_G:
				tgActionManager.execute(TGTransportPlayAction.NAME);
				return true;
			case KeyEvent.KEYCODE_I:
				tgActionManager.execute(TGChangeLetRingAction.NAME);
				return true;
			case KeyEvent.KEYCODE_M:
				tgActionManager.execute(TGShowSmartMenuAction.NAME);
				return true;
			case KeyEvent.KEYCODE_P:
				tgActionManager.execute(TGChangePalmMuteAction.NAME);
				return true;
			case KeyEvent.KEYCODE_V:
				tgActionManager.execute(TGChangeVibratoNoteAction.NAME);
				return true;
			default:
				return super.onKeyDown(keyCode, event);
		}
	}

	/** Interface size setting: everything measured in dp/sp is scaled (the notes are sized separately). */
	@Override
	protected void attachBaseContext(android.content.Context base) {
		float scale = app.tuxguitar.android.view.tablature.TGViewSettings.getUiScale(base);
		if( Math.abs(scale - 1f) > 0.01f ) {
			android.content.res.Configuration configuration = new android.content.res.Configuration(base.getResources().getConfiguration());
			configuration.densityDpi = Math.round(base.getResources().getDisplayMetrics().densityDpi * scale);
			base = base.createConfigurationContext(configuration);
		}
		super.attachBaseContext(base);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.destroyed = false;
		this.clearContext();
		this.attachInstance();
		this.createModules();
		this.setContentView(R.layout.activity_tg);
		this.setSupportActionBar((Toolbar) findViewById(R.id.app_toolbar));
		this.setupInsets();

		this.registerForContextMenu(findViewById(R.id.root_layout));
		this.getActionBarController().setDisplayHomeAsUpEnabled(true);
		this.getActionBarController().setHomeButtonEnabled(true);

		TGMessagesManager.getInstance().setResources(TGResourceBundle.getBundle(context, LANGUAGE_RESOURCE, Locale.getDefault()));

		this.resultManager.initialize();
		this.permissionResultManager.initialize();
		this.navigationManager.initialize();
		this.drawerManager.initialize();
		this.loadDefaultFragment();
	}

	private Insets systemBarInsets = Insets.NONE;
	private boolean fullBleed;
	private Runnable insetsListener;

	/**
	 * Edge-to-edge: the app draws behind the system bars and places its own content.
	 * The toolbar absorbs the status bar; other screens are kept clear of the navigation bar.
	 */
	private void setupInsets() {
		WindowCompat.setDecorFitsSystemWindows(this.getWindow(), false);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), new androidx.core.view.OnApplyWindowInsetsListener() {
			public WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets) {
				TGActivity.this.systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
				TGActivity.this.applyInsets();
				return insets;
			}
		});
	}

	private void applyInsets() {
		Insets bars = this.systemBarInsets;
		View toolbar = findViewById(R.id.app_toolbar);
		toolbar.setPadding(bars.left, bars.top, bars.right, 0);
		View content = findViewById(R.id.content_frame);
		if( this.fullBleed ) {
			// the screen places its own controls around the bars (see TGMainFragment)
			content.setPadding(0, 0, 0, 0);
		} else {
			content.setPadding(bars.left, 0, bars.right, bars.bottom);
		}
		if( this.insetsListener != null ) {
			this.insetsListener.run();
		}
	}

	public Insets getSystemBarInsets() {
		return this.systemBarInsets;
	}

	/** A screen without the toolbar that draws under the system bars itself. */
	public void setFullBleed(boolean fullBleed, Runnable insetsListener) {
		this.fullBleed = fullBleed;
		this.insetsListener = (fullBleed ? insetsListener : null);
		this.applyInsets();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		this.detachInstance();
		this.destroyModules();
		this.clearContext();
		this.destroyed = true;
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		this.connectPlugins();
		this.loadDefaultSong();
		this.drawerManager.syncState();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.clear();
	}

	@Override
	public void onBackPressed() {
		this.callBackAction();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		this.setIntent(intent);
		this.callProcessIntent();
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		super.onConfigurationChanged(configuration);

		this.drawerManager.onConfigurationChanged(configuration);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if( this.drawerManager.onOptionsItemSelected(item) ) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		TGMenuContextualInflater.getInstance(this.findContext()).inflate(menu, getMenuInflater());
	}

	public void openContextMenu() {
		this.openContextMenu(this.findViewById(R.id.root_layout));
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		this.resultManager.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		this.permissionResultManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	public void attachInstance() {
		TGActivityController.getInstance(findContext()).setActivity(this);
	}

	public void detachInstance() {
		TGActivityController.getInstance(findContext()).setActivity(null);
	}

	public void createModules() {
		TGContext context = this.findContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		TGSynchronizer.getInstance(context).setController(new TGSynchronizerControllerImpl(context));
		TGErrorManager.getInstance(context).addErrorHandler(new TGErrorHandlerImpl(this));
		TGResourceManager.getInstance(context).setResourceLoader(new TGResourceLoaderImpl(this));
		TGActionAdapterManager.getInstance(context).initialize(this);
		TGEditorManager.getInstance(context).setLockControl(new TGLock(context));
		TGVarAdapter.initialize(context);
		TGPropertiesAdapter.initialize(context, this);
		TGTransportAdapter.getInstance(context).initialize();
	}

	public void destroyModules() {
		TGContext context = this.findContext();
		TGThreadManager.getInstance(context).dispose();
	}

	public void connectPlugins() {
		TGPluginManager.getInstance(this.context).connectEnabled();
	}

	public void disconnectPlugins() {
		TGPluginManager.getInstance(this.context).disconnectAll();
	}

	public void destroyEditor() {
		TGEditorManager.getInstance(this.context).destroy(null);
	}

	public void destroy() {
		this.disconnectPlugins();
		this.destroyEditor();
		this.callFinishAction();
	}

	public void updateCache(boolean updateItems){
		this.updateCache(updateItems, null);
	}

	public void updateCache(boolean updateItems, TGAbstractContext sourceContext){
		TGEditorManager editorManager = TGEditorManager.getInstance(this.context);
		if( updateItems ){
			editorManager.updateSelection(sourceContext);
		}
		editorManager.redraw(sourceContext);
	}

	public TGDrawerManager getDrawerManager() {
		return drawerManager;
	}

	public TGNavigationManager getNavigationManager() {
		return navigationManager;
	}

	public TGActivityResultManager getResultManager() {
		return resultManager;
	}

	public TuningManager getTuningManager(){
		return TuningManager.getInstance(this.findContext());
	}

	public TGActivityPermissionResultManager getPermissionResultManager() {
		return permissionResultManager;
	}

	public TGActivityActionBarController getActionBarController() {
		return this.actionBar;
	}

	public TGContext findContext() {
		if( this.context == null ) {
			this.context = new TGContext();
		}
		return context;
	}

	public void clearContext() {
		this.findContext().clear();
	}

	public void loadDefaultFragment() {
		this.getNavigationManager().callOpenFragment(TGMainFragmentController.getInstance(findContext()));
	}

	public void loadDefaultSong() {
		Intent intent = this.getIntent();
		if( intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
			this.callProcessIntent();
		} else {
			this.callLoadDefaultSong();
		}
	}

	public void callLoadDefaultSong() {
		TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), TGLoadTemplateAction.NAME);
		tgActionProcessor.process();
	}

	public void callProcessIntent() {
		TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), TGProcessIntentAction.NAME);
		tgActionProcessor.setAttribute(TGProcessIntentAction.ATTRIBUTE_ACTIVITY, this);
		tgActionProcessor.process();
	}

	public void callBackAction() {
		TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), TGBackAction.NAME);
		tgActionProcessor.setAttribute(TGBackAction.ATTRIBUTE_ACTIVITY, this);
		tgActionProcessor.process();
	}

	public void callFinishAction() {
		TGActionProcessor tgActionProcessor = new TGActionProcessor(findContext(), TGFinishAction.NAME);
		tgActionProcessor.setAttribute(TGFinishAction.ATTRIBUTE_ACTIVITY, this);
		tgActionProcessor.process();
	}

	public boolean isDestroyed() {
		return this.destroyed;
	}

	public void setDisplayOn(boolean displayOn) {
		if (this.maintainDisplayON != displayOn) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (displayOn) {
						getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					} else {
						getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					}
				}
			});
		}
		this.maintainDisplayON = displayOn;
	}
}
