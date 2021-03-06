package org.petapico.nanobench;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.flow.RedirectToUrlException;
import org.apache.wicket.request.mapper.parameter.PageParameters;


public class OrcidLinkingPage extends WebPage {

	private static final long serialVersionUID = 1L;

	public OrcidLinkingPage(final PageParameters parameters) {
		super();
		add(new TitleBar("titlebar"));
		if (!ProfilePage.isComplete()) {
			throw new RedirectToUrlException("./profile");
		}
		add(new Label("introuri", ProfilePage.getIntroNanopub().getNanopub().getUri().stringValue()));
	}

}
