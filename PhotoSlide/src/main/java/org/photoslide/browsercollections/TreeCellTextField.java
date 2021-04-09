/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import java.nio.file.Paths;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 *
 * @author selfemp
 */
public class TreeCellTextField extends TextFieldTreeCell<PathItem> {

    private TextField textField;

    public TreeCellTextField() {
        super(new PathItemConverter());
        createTextField();
    }
    
    

    /**
     * On editing, create new text field and set it using setGraphic method.
     */
    @Override
    public void startEdit() {
        super.startEdit();
        
        setText(null);
        setGraphic(textField);
        textField.selectAll();
    }   
    
    
    private void createTextField() {
        textField = new TextField(getString());
        textField.setOnKeyReleased((KeyEvent t) -> {
            if (t.getCode() == KeyCode.ENTER) {
                commitEdit(new PathItem(Paths.get(t.getText())));
            } else if (t.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });
    }

    private String getString() {
        return getItem() == null ? "" : getItem().toString();
    }

}
