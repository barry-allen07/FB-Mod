
package net.filebot.ui.rename;


import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoundedRangeModel;
import javax.swing.Timer;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


class ScrollPaneSynchronizer {

	private final RenameList[] components;


	public ScrollPaneSynchronizer(RenameList... components) {
		this.components = components;

		// share vertical and horizontal scrollbar model
		BoundedRangeModel horizontalScrollBarModel = components[0].getListScrollPane().getHorizontalScrollBar().getModel();
		BoundedRangeModel verticalScrollBarModel = components[0].getListScrollPane().getVerticalScrollBar().getModel();

		// recalculate common size on change
		ListDataListener resizeListener = new ListDataListener() {

			private final Timer timer = new Timer(50, new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					updatePreferredSize();

					// fire only once
					timer.stop();
				}
			});


			@Override
			public void intervalAdded(ListDataEvent e) {
				timer.restart();
			}


			@Override
			public void intervalRemoved(ListDataEvent e) {
				timer.restart();
			}


			@Override
			public void contentsChanged(ListDataEvent e) {
				timer.restart();
			}
		};

		// apply to all components
		for (RenameList<?> component : components) {
			component.getListScrollPane().getHorizontalScrollBar().setModel(horizontalScrollBarModel);
			component.getListScrollPane().getVerticalScrollBar().setModel(verticalScrollBarModel);

			component.getListComponent().getModel().addListDataListener(resizeListener);
		}

		// initial sync of component sizes
		updatePreferredSize();
	}


	public void updatePreferredSize() {
		Dimension max = new Dimension();

		for (RenameList component : components) {
			// reset preferred size
			component.getListComponent().setPreferredSize(null);

			// calculate preferred size based on data and renderer
			Dimension preferred = component.getListComponent().getPreferredSize();

			// update maximum size
			if (preferred.width > max.width)
				max.width = preferred.width;
			if (preferred.height > max.height)
				max.height = preferred.height;
		}

		for (RenameList component : components) {
			// set fixed preferred size
			component.getListComponent().setPreferredSize(max);

			// update scrollbars
			component.getListComponent().revalidate();
			component.getListScrollPane().revalidate();
		}
	}

}
