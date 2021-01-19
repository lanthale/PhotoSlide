/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author selfemp
 */
public class GridCellSelectionModel {

    Set<MediaFile> selection = new HashSet<>();    

    public void add(MediaFile node) {
        selection.add(node);
        ((MediaFile) node).setSelected(true);
    }

    public int selectionCount() {
        return selection.size();
    }

    public void remove(MediaFile node) {
        selection.remove(node);
        ((MediaFile) node).setSelected(false);
    }

    public void clear() {
        while (!selection.isEmpty()) {
            Iterator iter = selection.iterator();
            while (iter.hasNext()) {
                MediaFile selCell = (MediaFile) iter.next();
                selCell.setSelected(false);                
            }
            selection.clear();
        }

    }

    public boolean contains(MediaFile node) {
        return selection.contains(node);
    }

    public void log() {
        Logger.getLogger(GridCellSelectionModel.class.getName()).log(java.util.logging.Level.SEVERE, "Selected nodes", Arrays.asList(selection.toArray()));
    }

    public Set<MediaFile> getSelection() {
        return selection;
    }

}
