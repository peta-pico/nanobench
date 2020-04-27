package org.petapico.nanobench;

import java.util.List;

import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.eclipse.rdf4j.model.IRI;

public class OptionalStatementItem extends Panel {

	private static final long serialVersionUID = 1L;

	public OptionalStatementItem(String id, List<IRI> items, final PublishForm form) {
		super(id);

		add(new ListView<IRI>("items", items) {

			private static final long serialVersionUID = 1L;
			private int count = 0;

			protected void populateItem(ListItem<IRI> item) {
				item.add(new ValueItem("item", item.getModelObject(), count > 1, true, form));
				count++;
			}

		});
	}

}
