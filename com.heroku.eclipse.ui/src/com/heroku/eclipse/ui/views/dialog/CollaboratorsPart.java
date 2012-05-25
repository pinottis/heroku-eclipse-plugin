package com.heroku.eclipse.ui.views.dialog;

import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.heroku.api.App;
import com.heroku.api.Collaborator;
import com.heroku.eclipse.core.services.exceptions.HerokuServiceException;
import com.heroku.eclipse.ui.Activator;
import com.heroku.eclipse.ui.utils.HerokuUtils;
import com.heroku.eclipse.ui.utils.LabelProviderFactory;
import com.heroku.eclipse.ui.utils.RunnableWithReturn;
import com.heroku.eclipse.ui.utils.ViewerOperations;

public class CollaboratorsPart {
	private TableViewer viewer;
	private App domainObject;
	private Button addButton;
	private Button removeButton;
	private Button makeOwner;
	// private Button saveButton;

	private List<Collaborator> collaboratorsList;
	private Collaborator currentOwner;

	public Composite createUI(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(2, false));

		{
			viewer = new TableViewer(container, SWT.MULTI | SWT.H_SCROLL
					| SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
			GridData gd = new GridData(GridData.FILL_BOTH);
			gd.heightHint = 300;
			viewer.getControl().setLayoutData(gd);
			viewer.getTable().setHeaderVisible(true);
			viewer.getTable().setLinesVisible(true);
			viewer.setContentProvider(new ArrayContentProvider());

			{
				TableViewerColumn column = new TableViewerColumn(viewer,
						SWT.NONE);
				column.getColumn().setText("Owner");
				column.getColumn().pack();
				column.setLabelProvider(LabelProviderFactory
						.createCollaborator_Owner(new RunnableWithReturn<Boolean, Collaborator>() {

							@Override
							public Boolean run(Collaborator argument) {
								return currentOwner == argument;
							}
						}));
			}

			{
				TableViewerColumn column = new TableViewerColumn(viewer,
						SWT.NONE);
				column.getColumn().setText("Collaborator E-Mail");
				column.getColumn().setWidth(200);
				column.setLabelProvider(LabelProviderFactory
						.createCollaborator_Email());
			}
		}

		{
			Composite controls = new Composite(container, SWT.NONE);
			controls.setLayout(new GridLayout(1, true));

			{
				addButton = new Button(controls, SWT.PUSH);
				addButton.setText("+");
				addButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				addButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						handleAdd(addButton.getShell());
					}
				});
			}

			{
				removeButton = new Button(controls, SWT.PUSH);
				removeButton.setText("-");
				removeButton.setLayoutData(new GridData(
						GridData.FILL_HORIZONTAL));
				removeButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						@SuppressWarnings("unchecked")
						List<Collaborator> collabs = ((IStructuredSelection) viewer
								.getSelection()).toList();
						if (collabs.contains(currentOwner)) {
							// TODO Show message that this is not possible
							System.err.println("Can't remove owner");
						} else {
							handleRemove(removeButton.getShell(), collabs);
						}
					}
				});
			}

			{
				makeOwner = new Button(controls, SWT.PUSH);
				makeOwner.setText("Make Owner");
				makeOwner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			}

			// {
			// saveButton = new Button(controls, SWT.PUSH);
			// saveButton.setText("Save");
			// saveButton
			// .setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			// }
		}

		return container;
	}

	void handleRemove(Shell shell, List<Collaborator> collaborators) {
		String message;

		if (collaborators.size() == 1) {
			message = "Do you really want to remove the collaborator '"
					+ collaborators.get(0).getEmail() + "'?";
		} else {
			message = "The following collaborators will be remove:\n";
			for (Collaborator c : collaborators) {
				message += "* " + c.getEmail() + "\n";
			}
			message += "Do you want to proceed?";
		}

		if (MessageDialog.openQuestion(shell, "Remove collaborator", message)) {
			try {
				String[] emails = new String[collaborators.size()];
				for (int i = 0; i < emails.length; i++) {
					emails[i] = collaborators.get(i).getEmail();
				}

				Activator.getDefault().getService()
						.removeCollaborators(domainObject, emails);
				refreshCollaboratorList();
			} catch (HerokuServiceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	void handleAdd(Shell shell) {
		TrayDialog d = new TrayDialog(shell) {

			private Text emailField;

			@Override
			protected Control createDialogArea(Composite parent) {
				Composite container = (Composite) super
						.createDialogArea(parent);
				getShell().setText("Add Collaborator");

				Composite area = new Composite(container, SWT.NONE);
				area.setLayout(new GridLayout(2, false));
				area.setLayoutData(new GridData(GridData.FILL_BOTH));

				{
					Label l = new Label(area, SWT.NONE);
					l.setText("E-Mail");

					emailField = new Text(area, SWT.BORDER);
					emailField.setLayoutData(new GridData(
							GridData.FILL_HORIZONTAL));
				}

				return container;
			}

			@Override
			protected void okPressed() {
				for (Collaborator u : collaboratorsList) {
					if (u.getEmail().equals(emailField.getText().trim())) {
						// TODO Show warning - already exists
						return;
					}
				}

				try {
					Activator
							.getDefault()
							.getService()
							.addCollaborator(domainObject,
									emailField.getText().trim());
					super.okPressed();
					refreshCollaboratorList();
				} catch (HerokuServiceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		d.open();
	}

	public void setDomainObject(App domainObject) {
		this.domainObject = domainObject;
		refreshCollaboratorList();
	}

	private void refreshCollaboratorList() {
		try {
			collaboratorsList = Activator.getDefault().getService()
					.getCollaborators(domainObject);

			if (domainObject.getOwnerEmail() != null) {
				for (Collaborator c : collaboratorsList) {
					if (domainObject.getOwnerEmail().equals(c.getEmail())) {
						currentOwner = c;
						break;
					}
				}
			} else {
				currentOwner = null;
			}

			HerokuUtils.runOnDisplay(true, viewer, collaboratorsList,
					ViewerOperations.input(viewer));
		} catch (HerokuServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void dispose() {

	}
}
