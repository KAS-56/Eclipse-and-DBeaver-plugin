package ru.tensor.explain.dbeaver;

import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

import ru.tensor.explain.dbeaver.api.ExplainAPI;
import ru.tensor.explain.dbeaver.api.IExplainAPI;
import ru.tensor.explain.dbeaver.views.PostgresPlanView;

public class ExplainPostgreSQLPlugin extends AbstractUIPlugin {

	// The plug-in bundle Id
	final private static String BUNDLE_ID = "ru.tensor.explain.dbeaver";

	// The Execution Plan View Id
	final public static String PLAN_VIEW_ID = "ru.tensor.explain.dbeaver.planView";

	// The external browser Id
	final public static String BROWSER_ID = "ru.tensor.explain.dbeaver.browser";

	// The plug-in ID
	final public static String PLUGIN_ID = "Explain PostgreSQL";

	final public static String FORMATTER_TITLE = "Explain PostgreSQL formatter";
	final public static String EXPLAINER_TITLE = "Explain PostgreSQL explainer";

	// The shared instance
	private static ExplainPostgreSQLPlugin plugin;

	private PostgresPlanView _postgresPlanView;

	private IExplainAPI _explainAPI;

	private static final String platformName = Platform.getProduct().getName() + " " + Platform.getProduct().getDefiningBundle().getVersion().toString();
	private static final Version pluginVersion = Platform.getBundle(BUNDLE_ID).getVersion();
	public static final String versionString = platformName + " " + BUNDLE_ID + "/" + pluginVersion.getMajor() + "." + pluginVersion.getMinor() + "." + pluginVersion.getMicro();

	public ExplainPostgreSQLPlugin() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		ExplainPostgreSQLPlugin.getDefault().getLog().info("VERSION: " + versionString);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static ExplainPostgreSQLPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns the Execution Plan View
	 * 
	 * @return the Execution Plan View
	 */
	public static PostgresPlanView getPlanView() {
		synchronized (getDefault()) {
			createOrActivateView();
			return getDefault()._postgresPlanView;
		}
	}

	/**
	 * Set the Execution Plan View
	 */
	public static void setPlanView(PostgresPlanView view) {
		synchronized (getDefault()) {
			getDefault()._postgresPlanView = view;
		}
	}

	/**
	 * Clear the Execution Plan View
	 */
	public static void clearPlanView() {
		setPlanView(null);
	}

	/**
	 * Returns the API service
	 * 
	 * @return the API service
	 */
	public static IExplainAPI getExplainAPI() {
		synchronized (getDefault()) {
			if (getDefault()._explainAPI == null) {
				getDefault()._explainAPI = new ExplainAPI();
			}
			return getDefault()._explainAPI;
		}
	}

	private static void createOrActivateView() {

		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {

				IWorkbenchWindow activeWindow = getActiveWindow();
				if (activeWindow == null) {
					getDefault().getLog().warn("Active window not found");
					return;
				}
				
				IWorkbenchPage activePage = getActivePage(activeWindow);
				if (activePage == null) {
					getDefault().getLog().warn("Active page not found");
					return;
				}

				try {
					activePage.showView(PLAN_VIEW_ID, null, IWorkbenchPage.VIEW_ACTIVATE);
				} catch (PartInitException ex) {
					getDefault().getLog().error("ExplainPostgreSQLPlugin create view error: " + ex.getMessage(), ex);
				}

			}
		});
	}

	private static IWorkbenchWindow getActiveWindow() {

		// get the active window
		IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

		// if can not find the active window, select one from the workbench windows list
		if (activeWindow == null) {
			for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
				if (window != null) {
					return window;
				}
			}
		}

		return activeWindow;
	}

	private static IWorkbenchPage getActivePage(IWorkbenchWindow window) {

		// get the active page in this window
		IWorkbenchPage activePage = window.getActivePage();

		// if can not find the active page, select one from page list
		if (activePage == null) {
			for (IWorkbenchPage page : window.getPages()) {
				if (page != null) {
					return page;
				}
			}
		}

		return activePage;
	}
}
