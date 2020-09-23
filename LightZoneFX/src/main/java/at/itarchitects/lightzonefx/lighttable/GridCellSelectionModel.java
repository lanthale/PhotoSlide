/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.itarchitects.lightzonefx.lighttable;

import at.itarchitects.lightzonefx.datamodel.MediaFile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;
import javafx.scene.Node;

/**
 *
 * @author selfemp
 */
public class GridCellSelectionModel {

    Set<Node> selection = new HashSet<>();    

    public void add(Node node) {
        selection.add(node);
        ((MediaFile) node).setSeleted(true);
    }

    public int selectionCount() {
        return selection.size();
    }

    public void remove(Node node) {
        selection.remove(node);
        ((MediaFile) node).setSeleted(false);
    }

    public void clear() {
        while (!selection.isEmpty()) {
            Iterator iter = selection.iterator();
            while (iter.hasNext()) {
                MediaFile selCell = (MediaFile) iter.next();
                selCell.setSeleted(false);                
            }
            selection.clear();
        }

    }

    public boolean contains(Node node) {
        return selection.contains(node);
    }

    public void log() {
        Logger.getLogger(GridCellSelectionModel.class.getName()).log(java.util.logging.Level.SEVERE, "Selected nodes", Arrays.asList(selection.toArray()));
    }

    public Set<Node> getSelection() {
        return selection;
    }

}
