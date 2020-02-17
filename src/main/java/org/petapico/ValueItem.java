package org.petapico;

import org.apache.wicket.markup.html.panel.Panel;
import org.eclipse.rdf4j.model.IRI;

public class ValueItem extends Panel {
	
	private static final long serialVersionUID = 1L;

	public ValueItem(String id, IRI iri, boolean objectPosition, PublishForm form) {
		super(id);
		if (form.template.isUriPlaceholder(iri)) {
			add(new IriTextfieldItem("value", iri, form));
		} else if (form.template.isLiteralPlaceholder(iri)) {
				add(new LiteralTextfieldItem("value", iri, form));
		} else {
			add(new IriItem("value", iri, objectPosition, form));
		}
	}

}
