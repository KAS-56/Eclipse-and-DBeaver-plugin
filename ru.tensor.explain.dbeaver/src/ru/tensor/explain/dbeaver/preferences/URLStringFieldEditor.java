package ru.tensor.explain.dbeaver.preferences;

import java.io.IOException;
import java.net.URI;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;

import ru.tensor.explain.dbeaver.ExplainPostgreSQLPlugin;

public class URLStringFieldEditor extends StringFieldEditor {

	private static final ILog log = ExplainPostgreSQLPlugin.getDefault().getLog();
	
	@Override
	protected void doStore() {
		super.doStore();
	}

	private int validateStrategy = VALIDATE_ON_FOCUS_LOST;

	public URLStringFieldEditor(String name, String labelText, Composite parent) {
		super(name, labelText, parent);
		setEmptyStringAllowed(false);
		setValidateStrategy(validateStrategy);
		setErrorMessage("Please input valid URL");
	}

	@Override
	protected boolean doCheckState() {
		String text = getTextControl().getText();
		if (text == null) {
			return false;
		}
		text = trimEndChar(text.trim(), '/');
		if (text.length() > 0) {
			try {
				URI.create(text).toURL().openStream().close();
				return true;
			} catch (Exception ex) {
        		log.error(ex.getMessage(), ex);
			}
		}		
		return false;
	}
	
	private static String trimEndChar(String str, char charToTrim) {
        if (str == null || "".equals(str)) {
            return str;
        }

        int i = str.length() - 1;
        while (i >= 0 && str.charAt(i) == charToTrim) {
            i--;
        }
        return str.substring(0, i + 1);
    }
}
