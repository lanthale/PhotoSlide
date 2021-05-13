/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.bookmarksboard;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 *
 * @author selfemp
 */
public class BMBIcon extends StackPane {

    private final FontIcon icon;
    private final Label counter;

    public BMBIcon(FontIcon iconString) {
        this.icon = iconString;
        this.counter = new Label("0");        
        counter.setId("iconcounter");
        counter.setVisible(false);
        this.setAlignment(Pos.BOTTOM_RIGHT);
        this.getChildren().add(icon);
        this.getChildren().add(counter);
    }

    public void setCounter(int value) {
        if (value == 0) {
            counter.setVisible(false);
        } else {
            counter.setVisible(true);
            counter.setText("" + value);
        }
    }

}
