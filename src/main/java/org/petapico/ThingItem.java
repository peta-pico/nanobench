package org.petapico;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.eclipse.rdf4j.model.IRI;

public class ThingItem extends Panel {
	
	private static final long serialVersionUID = -5109507637942030910L;

	public ThingItem(String id, IRI iri) {
		super(id);
		String s = "null";
		if (iri != null) s = iri.stringValue();
		add(new Label("thing", s));
	}

}
