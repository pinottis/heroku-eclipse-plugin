package com.heroku.eclipse.ui.wizards;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.log.LogService;

import com.heroku.api.App;
import com.heroku.eclipse.core.services.HerokuServices;
import com.heroku.eclipse.core.services.exceptions.HerokuServiceException;
import com.heroku.eclipse.core.services.model.AppTemplate;
import com.heroku.eclipse.ui.Activator;
import com.heroku.eclipse.ui.Messages;
import com.heroku.eclipse.ui.utils.HerokuUtils;

/**
 * @author udo.rader@bestsolution.at
 * 
 */
public class HerokuAppCreate extends Wizard implements IImportWizard {

	private HerokuAppCreateNamePage namePage;
	private HerokuAppCreateTemplatePage templatePage;

	private HerokuServices service;

	/**
	 * 
	 */
	public HerokuAppCreate() {
		service = Activator.getDefault().getService();
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
	}

	@Override
	public void addPages() {
		setNeedsProgressMonitor(true);

		try {
			namePage = new HerokuAppCreateNamePage();
			addPage(namePage);
			templatePage = new HerokuAppCreateTemplatePage();
			addPage(templatePage);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean performFinish() {
		boolean rv = false;
		
		try {
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					// first clone
					App app = createHerokuApp(monitor);
					if (app != null) {
						// then materialize
						try {
							service.materializeGitApp(app, Messages.getFormattedString("HerokuAppCreate_CreatingApp", app.getName()), monitor); //$NON-NLS-1$
						}
						catch (HerokuServiceException e) {
							if ( e.getErrorCode() == HerokuServiceException.NOT_ACCEPTABLE ) {
								namePage.setErrorMessage(Messages.getString("HerokuAppCreateNamePage_Error_NameAlreadyExists")); //$NON-NLS-1$
								namePage.setVisible(true);
								return;
							}
							else {
								e.printStackTrace();
								Activator.getDefault().getLogger().log(LogService.LOG_ERROR, "internal error, aborting ...", e); //$NON-NLS-1$
								HerokuUtils.internalError(getShell(), e);
							}
						}
					}
				}
			});
		}
		catch (InvocationTargetException e1) {
			e1.printStackTrace();
			Activator.getDefault().getLogger().log(LogService.LOG_ERROR, "internal error, aborting ...", e1); //$NON-NLS-1$
			HerokuUtils.internalError(getShell(), e1);
		}
		catch (InterruptedException e1) {
			e1.printStackTrace();
			Activator.getDefault().getLogger().log(LogService.LOG_ERROR, "internal error, aborting ...", e1); //$NON-NLS-1$
			HerokuUtils.internalError(getShell(), e1);
		}

		return rv;
	}

	/**
	 * Creates the app on the Heroku side
	 * 
	 * @return the newly created App instance
	 */
	private App createHerokuApp( IProgressMonitor pm ) {
		App app = null;

		String appName = namePage.getAppName();

		if (appName != null) {
			AppTemplate template = templatePage.getAppTemplate();

			if (template != null) {
				try {
					app = service.createAppFromTemplate(appName, template.getTemplateName(), pm);
				}
				catch (HerokuServiceException e) {
					e.printStackTrace();
				}
			}
		}

		return app;
	}
}
