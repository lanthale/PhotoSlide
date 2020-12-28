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
        Path path = new Path();
        /**
         * you will need to increase the 5 if you want the close button to be
         * big
         */
        path.getElements().addAll(
                new MoveTo(0, 0), new LineTo(5, 5),
                new MoveTo(5, 0), new LineTo(0, 5));
        path.setStyle("-fx-stroke: rgb(120, 120, 120);");
        path.setOnMouseClicked(new EventHandler<MouseEvent>() {
            
            @Override
            public void handle(MouseEvent paramT) {
                Node n = Tag.this.getParent();
                if (n instanceof Pane) {//of course it is
                    ((Pane) n).getChildren().remove(Tag.this);
                }
            }
        });
        setPadding(new Insets(0, 3, 3, 5));
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
        setGraphicTextGap(8);
        graphicProperty().addListener(new ChangeListener<Node>() {
            @Override
            public void changed(ObservableValue<? extends Node> paramObservableValue,
                    Node paramT1, Node paramT2) {
                if (paramT2 != path) {
                    setGraphic(path);
                }
            }
        });
        
        setStyle("-fx-background-color: rgb(50, 50, 50); "
                + "-fx-background-radius: 3;"
                + "-fx-border-radius: 3;"
                + "-fx-border-color: rgb(50, 50, 50);");
    }
    
}
