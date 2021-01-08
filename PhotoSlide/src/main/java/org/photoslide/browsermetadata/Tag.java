/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsermetadata;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 *
 * @author cleme
 */
public class Tag extends Label {
    
    public Tag() {
        super();
        initTag();
    }
    
    private Tag(String arg0, Node arg1) {
        super(arg0, arg1);
    }
    
    public Tag(String arg0) {
        super(arg0);
        initTag();
    }
    
    private final void initTag() {        
        setAlignment(Pos.CENTER);
        setPadding(new Insets(1, 1, 1, 8));
        FontIcon ics=new FontIcon("ti-close:8");
        Button bt = new Button();
        bt.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        bt.setGraphic(ics);
        bt.setId("toolbutton");
        bt.setOnAction((t) -> {
            Node n = Tag.this.getParent();
            if (n instanceof Pane) {//of course it is
                ((Pane) n).getChildren().remove(Tag.this);
            }
        });
        setGraphic(bt);
        setContentDisplay(ContentDisplay.RIGHT);
        setGraphicTextGap(0);        
        
        setStyle("-fx-background-color: rgb(50, 50, 50); "
                + "-fx-background-radius: 3;"
                + "-fx-border-radius: 3;"
                + "-fx-border-color: rgb(50, 50, 50);");
    }
    
}
