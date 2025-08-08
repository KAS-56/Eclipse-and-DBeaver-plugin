package ru.tensor.explain.dbeaver.views;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import ru.tensor.explain.dbeaver.ExplainPostgreSQLPlugin;
import ru.tensor.explain.dbeaver.preferences.PreferenceConstants;

public class PostgresPlanView extends ViewPart {

	private Browser internalBrowser;
	final private IPreferenceStore store = ExplainPostgreSQLPlugin.getDefault().getPreferenceStore();
	final private ILog log = ExplainPostgreSQLPlugin.getDefault().getLog();

	public PostgresPlanView() {
		super();
	}

	@Override
	public void createPartControl(Composite parent) {
		try {
			internalBrowser = new Browser(parent, SWT.NONE);
		} catch (Throwable ex) {
			String error = "Create internal browser failed, external browser will be used. Error: " + ex.getMessage();
			log.log(new Status(IStatus.ERROR, ExplainPostgreSQLPlugin.PLUGIN_ID, error, ex));
			store.setValue(PreferenceConstants.P_EXTERNAL, true);
		}
		ExplainPostgreSQLPlugin.setPlanView(this);
	}

	@Override
	public void setFocus() {
	}

	public void explainQueryPlan(String plan, String query) {

		Job job = new Job(ExplainPostgreSQLPlugin.EXPLAINER_TITLE) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {

				ExplainPostgreSQLPlugin.getExplainAPI().plan_archive(plan, query, (String url) -> {
					PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

						@Override
						public void run() {
							try {
								internalBrowser.setUrl(url);
							} catch (Exception ex) {
								String error = "Show explain result failed: " + ex.getMessage();
								log.log(new Status(IStatus.ERROR, ExplainPostgreSQLPlugin.PLUGIN_ID, error, ex));
								MessageDialog.openError(null, ExplainPostgreSQLPlugin.EXPLAINER_TITLE, error);
							}
						}
					});
				});
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.SHORT);
		job.schedule();
	}

	@Override
	public void dispose() {
		ExplainPostgreSQLPlugin.clearPlanView();
		super.dispose();
	}
}
