package ru.tensor.explain.dbeaver.handlers;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import org.jkiss.dbeaver.ext.postgresql.model.plan.PostgreQueryPlaner;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlannerConfiguration;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithProgress;
import org.jkiss.dbeaver.model.sql.SQLQuery;
import org.jkiss.dbeaver.model.sql.SQLScriptElement;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditor;
import org.jkiss.dbeaver.ui.editors.sql.plan.ExplainPlanViewer;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;

import ru.tensor.explain.dbeaver.ExplainPostgreSQLPlugin;
import ru.tensor.explain.dbeaver.preferences.PreferenceConstants;

public class ExplainHandler extends AbstractHandler {
	final private ILog log = ExplainPostgreSQLPlugin.getDefault().getLog();
	final private IPreferenceStore store = ExplainPostgreSQLPlugin.getDefault().getPreferenceStore();
	private static Integer PLAN_TASK_TIMEOUT = 30000;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		final ITextEditor textEditor = (ITextEditor) window.getActivePage().getActiveEditor();
		if (textEditor instanceof SQLEditor == false) {
			return null;
		}

		SQLEditor editor = (SQLEditor) textEditor;
		SQLScriptElement scriptElement = editor.extractActiveQuery();
		if (scriptElement instanceof SQLQuery == false) {
			return null;
		}

		SQLQuery sqlQuery = (SQLQuery) scriptElement;
		String sqlQueryText = sqlQuery.getText();
		final String[] plan = new String[1];

		DBCQueryPlanner planner = GeneralUtils.adapt(editor.getDataSource(), DBCQueryPlanner.class);
		DBRRunnableWithProgress planObtainTask = monitor -> {
			DBCQueryPlannerConfiguration configuration = ExplainPlanViewer.makeExplainPlanConfiguration(monitor, planner);
			if (configuration == null) {
				return;
			}
			try (JDBCSession connection = (JDBCSession) editor.getExecutionContext().openSession(monitor, DBCExecutionPurpose.UTIL, "Prepare plan query")) {
				boolean oldAutoCommit = false;
				try {
					oldAutoCommit = connection.getAutoCommit();
					if (oldAutoCommit) {
						connection.setAutoCommit(false);
					}
					try (JDBCStatement dbStat = connection.createStatement()) {
						JDBCResultSet dbResult = dbStat.executeQuery(getPlanQueryString(configuration) + sqlQuery.toString());
						String planLines = "";
						while (dbResult.next()) {
							String planLine = dbResult.getString(1);
							if (!CommonUtils.isEmpty(planLine)) {
								planLines += planLine.toString() + "\n";
							}
						}
						plan[0] = planLines;
					}
				} catch (SQLException ex) {
					log.error("Execution plan build failed: " + ex.getMessage(), ex);
				} finally {
					// Rollback changes because EXPLAIN actually executes query and it could be INSERT/UPDATE
					try {
						connection.rollback();
						if (oldAutoCommit) {
							connection.setAutoCommit(true);
						}
					} catch (SQLException ex) {
						log.error("Error closing plan analyser: " + ex.getMessage(), ex);
					}
				}

			} catch (Exception ex) {
				log.error(ex.getMessage(), ex);
			}
		};

		if (RuntimeUtils.runTask(planObtainTask, "Explain '" + sqlQueryText + "'", PLAN_TASK_TIMEOUT)
				&& !CommonUtils.isEmpty(plan[0])) {
			boolean useExternalBrowser = store.getBoolean(PreferenceConstants.P_EXTERNAL);
			if (useExternalBrowser) {
				explainQueryPlanByExternalBrowser(plan[0], sqlQueryText);
			} else {
				ExplainPostgreSQLPlugin.getPlanView().explainQueryPlan(plan[0], sqlQueryText);
			}
		}

		return null;
	}

	private String getPlanQueryString(DBCQueryPlannerConfiguration configuration) {
		Map<String, Object> parameters = configuration.getParameters();
		StringBuilder explainStat = new StringBuilder(64);
		explainStat.append("EXPLAIN (FORMAT TEXT");
		for (Map.Entry<String, Object> entry : CommonUtils.safeCollection(parameters.entrySet())) {
			String key = entry.getKey();
			if (PostgreQueryPlaner.PARAM_TIMING.equals(key)
					&& !CommonUtils.toBoolean(parameters.get(PostgreQueryPlaner.PARAM_ANALYSE))) {
				continue;
			}
			if (PostgreQueryPlaner.PARAM_COSTS.equals(key) || PostgreQueryPlaner.PARAM_TIMING.equals(key)) {
				if (!CommonUtils.toBoolean(entry.getValue())) {
					explainStat.append(",").append(key).append(" FALSE");
				}
				continue;
			}
			if (CommonUtils.toBoolean(entry.getValue())) {
				explainStat.append(",").append(key);
			}
		}
		explainStat.append(") ");
		return explainStat.toString();
	}

	private void explainQueryPlanByExternalBrowser(String plan, String query) {

		Job job = new Job(ExplainPostgreSQLPlugin.EXPLAINER_TITLE) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {

				ExplainPostgreSQLPlugin.getExplainAPI().plan_archive(plan, query, (String url) -> {
					IWebBrowser externalBrowser = null;
					try {
						externalBrowser = PlatformUI
								.getWorkbench()
								.getBrowserSupport()
								.createBrowser(
									IWorkbenchBrowserSupport.AS_EXTERNAL,
									ExplainPostgreSQLPlugin.BROWSER_ID,
									ExplainPostgreSQLPlugin.PLUGIN_ID,
									ExplainPostgreSQLPlugin.PLUGIN_ID);
						externalBrowser.openURL(URI.create(url).toURL());
					} catch (Exception ex) {
						String error = "Show explain result failed: " + ex.getMessage();
						log.log(new Status(IStatus.ERROR, ExplainPostgreSQLPlugin.PLUGIN_ID, error, ex));
						MessageDialog.openError(null, ExplainPostgreSQLPlugin.EXPLAINER_TITLE, error);
					} finally {
						if (externalBrowser != null) {
							externalBrowser.close();
						}
					}
				});
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.SHORT);
		job.schedule();
	}
}
