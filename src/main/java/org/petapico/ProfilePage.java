package org.petapico;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;

import javax.xml.bind.DatatypeConverter;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.flow.RedirectToUrlException;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.validation.validator.PatternValidator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.nanopub.extra.security.MakeKeys;
import org.nanopub.extra.security.SignNanopub;
import org.nanopub.extra.security.SignatureAlgorithm;

public class ProfilePage extends WebPage {

	private static final long serialVersionUID = 1L;

	private static final String ORCID_PATTERN = "[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9X]";

	Model<String> messageModel = Model.of("");

	public ProfilePage(final PageParameters parameters) {
		super();
		add(new ProfileItem("profile"));

		add(new Label("message", messageModel));
		updateMessage();

		Model<String> model = Model.of("");
		if (getUserIri() != null) {
			model.setObject(getUserIri().stringValue().replaceFirst("^https://orcid.org/", ""));
		}
		final TextField<String> orcidField = new TextField<>("orcidfield", model);
		orcidField.add(new PatternValidator(ORCID_PATTERN));
		Form<Void> form = new Form<Void>("form") {

			private static final long serialVersionUID = 1L;

			protected void onSubmit() {
				setOrcid(orcidField.getModelObject());
				updateMessage();
			}

		};
		form.add(orcidField);
		add(form);
		add(new FeedbackPanel("feedback"));

		Label keymessage = new Label("keymessage", "No key file found.");
		Link<String> link = new Link<String>("createkey") {

			private static final long serialVersionUID = 1L;

			@Override
			public MarkupContainer setDefaultModel(IModel<?> arg0) {
				return null;
			}

			@Override
			public void onClick() {
				try {
					MakeKeys.make(keyFile.getAbsolutePath().replaceFirst("_rsa$", ""), SignatureAlgorithm.RSA);
					throw new RedirectToUrlException("./profile");
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

		};
		add(new Label("keyfile", keyFile.getPath()));
		if (keyFile.exists()) {
			if (getKeyPair() == null) {
				add(new Label("pubkey", "Error loading key file"));
			} else {
				String pubkeyString = DatatypeConverter.printBase64Binary(keyPair.getPublic().getEncoded()).replaceAll("\\s", "");
				add(new Label("pubkey", pubkeyString));
			}
			keymessage.setVisible(false);
			link.setVisible(false);
		} else {
			add(new Label("pubkey", ""));
		}
		add(keymessage);
		add(link);
	}

	private void updateMessage() {
		if (isComplete()) {
			messageModel.setObject("");
		} else {
			messageModel.setObject("You need to set an ORCID identifier and load the signature keys before you can publish nanopublications.");
		}
	}

	private static ValueFactory vf = SimpleValueFactory.getInstance();

	private static File orcidFile = new File(System.getProperty("user.home") + "/.nanopub/orcid");
	private static File keyFile = new File(System.getProperty("user.home") + "/.nanopub/id_rsa");

	private static KeyPair keyPair;
	private static IRI userIri;

	static boolean isComplete() {
		return getUserIri() != null && getKeyPair() != null;
	}

	static KeyPair getKeyPair() {
		if (keyPair == null) {
			try {
				keyPair = SignNanopub.loadKey(keyFile.getPath(), SignatureAlgorithm.RSA);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		return keyPair;
	}

	static IRI getUserIri() {
		if (userIri == null) {
			if (orcidFile.exists()) {
				try {
					String orcid = Files.readString(orcidFile.toPath(), StandardCharsets.UTF_8).trim();
					if (orcid.matches(ORCID_PATTERN)) {
						userIri = vf.createIRI("https://orcid.org/" + orcid);
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
		return userIri;
	}

	private static void setOrcid(String orcid) {
		if (orcid.matches(ORCID_PATTERN)) {
			try {
				Files.writeString(orcidFile.toPath(), orcid + "\n");
				userIri = vf.createIRI("https://orcid.org/" + orcid);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

}
