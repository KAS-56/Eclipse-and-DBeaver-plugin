package ru.tensor.explain.dbeaver.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import ru.tensor.explain.dbeaver.ExplainPostgreSQLPlugin;

public class FormatSQLhandler extends AbstractHandler {
	final private ILog log = ExplainPostgreSQLPlugin.getDefault().getLog();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		final ITextEditor editor = (ITextEditor) window.getActivePage().getActiveEditor();
		if (editor == null) {
			return null;
		}

		String workSql = null;
		final boolean isSelection;
		if (editor.getSelectionProvider().getSelection() instanceof TextSelection) {
			workSql = ((TextSelection) editor.getSelectionProvider().getSelection()).getText();
		}
		if (workSql == null || "".equals(workSql)) {
			workSql = editor.getDocumentProvider().getDocument(editor.getEditorInput()).get();
			isSelection = false;
		} else {
			isSelection = true;
		}

		final String sql = workSql;

		Job job = new Job(ExplainPostgreSQLPlugin.FORMATTER_TITLE) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				ExplainPostgreSQLPlugin.getExplainAPI().beautifier(sql, (String fmtText) -> {
					window.getWorkbench().getDisplay().asyncExec(new Runnable() {

						@Override
						public void run() {
							try {
								if (fmtText.startsWith("Error")) {
									throw new Exception(fmtText);
								}

								IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());

								if (isSelection) {
									TextSelection selection = (TextSelection) editor.getSelectionProvider().getSelection();
									int offset = selection.getOffset();
									int length = selection.getLength();
									doc.replace(offset, length, fmtText);
									editor.selectAndReveal(offset, fmtText.length());
								} else {
									doc.set(fmtText);
								}
							} catch (Exception ex) {
								String error = "Format SQL query failed: " + ex.getMessage();
								log.log(new Status(IStatus.ERROR, ExplainPostgreSQLPlugin.PLUGIN_ID, error, ex));
								MessageDialog.openError(window.getShell(), ExplainPostgreSQLPlugin.FORMATTER_TITLE, error);
							}
						}

					});

				});
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.SHORT);
		job.schedule();

		return null;
	}

}
