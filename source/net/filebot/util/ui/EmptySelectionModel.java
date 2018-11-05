
package net.filebot.util.ui;


import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;


public class EmptySelectionModel implements ListSelectionModel {

	@Override
	public void addListSelectionListener(ListSelectionListener x) {
	}


	@Override
	public void addSelectionInterval(int from, int to) {
	}


	@Override
	public void clearSelection() {
	}


	@Override
	public int getAnchorSelectionIndex() {
		return -1;
	}


	@Override
	public int getLeadSelectionIndex() {
		return -1;
	}


	@Override
	public int getMaxSelectionIndex() {
		return -1;
	}


	@Override
	public int getMinSelectionIndex() {
		return -1;
	}


	@Override
	public int getSelectionMode() {
		return -1;
	}


	@Override
	public boolean getValueIsAdjusting() {
		return false;
	}


	@Override
	public void insertIndexInterval(int index, int length, boolean before) {
	}


	@Override
	public boolean isSelectedIndex(int index) {
		return false;
	}


	@Override
	public boolean isSelectionEmpty() {
		return true;
	}


	@Override
	public void removeIndexInterval(int from, int to) {
	}


	@Override
	public void removeListSelectionListener(ListSelectionListener listener) {
	}


	@Override
	public void removeSelectionInterval(int from, int to) {
	}


	@Override
	public void setAnchorSelectionIndex(int index) {
	}


	@Override
	public void setLeadSelectionIndex(int index) {
	}


	@Override
	public void setSelectionInterval(int from, int to) {
	}


	@Override
	public void setSelectionMode(int selectionMode) {
	}


	@Override
	public void setValueIsAdjusting(boolean valueIsAdjusting) {
	}

}
