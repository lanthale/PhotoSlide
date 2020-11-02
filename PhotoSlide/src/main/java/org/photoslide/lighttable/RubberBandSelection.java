/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.lighttable;

import org.photoslide.datamodel.GridCellSelectionModel;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 *
 * @author selfemp
 */
public class RubberBandSelection {

    final DragContext dragContext = new DragContext();
    Rectangle rect;
    GridCellSelectionModel selectionModel;

    Pane group;

    public RubberBandSelection(Pane group, GridCellSelectionModel selectionModel) {

        this.group = group;
        this.selectionModel = selectionModel;

        rect = new Rectangle(0, 0, 0, 0);
        rect.setStroke(Color.BLUE);
        rect.setStrokeWidth(1);
        rect.setStrokeLineCap(StrokeLineCap.ROUND);
        rect.setFill(Color.LIGHTBLUE.deriveColor(0, 1.2, 1, 0.6));

        group.addEventHandler(MouseEvent.MOUSE_PRESSED, onMousePressedEventHandler);
        group.addEventHandler(MouseEvent.MOUSE_DRAGGED, onMouseDraggedEventHandler);
        group.addEventHandler(MouseEvent.MOUSE_RELEASED, onMouseReleasedEventHandler);

    }

    EventHandler<MouseEvent> onMousePressedEventHandler = new EventHandler<MouseEvent>() {

        @Override
        public void handle(MouseEvent event) {
            dragContext.mouseAnchorX = event.getSceneX();
            dragContext.mouseAnchorY = event.getSceneY();

            rect.setX(dragContext.mouseAnchorX);
            rect.setY(dragContext.mouseAnchorY);
            rect.setWidth(0);
            rect.setHeight(0);

            group.getChildren().add(rect);

            event.consume();

        }
    };

    EventHandler<MouseEvent> onMouseReleasedEventHandler = new EventHandler<MouseEvent>() {

        @Override
        public void handle(MouseEvent event) {

            if (!event.isShiftDown() && !event.isControlDown()) {
                selectionModel.clear();
            }

            for (Node node : group.getChildren()) {

                if (node instanceof ImageView) {
                    if (node.getBoundsInParent().intersects(rect.getBoundsInParent())) {

                        if (event.isShiftDown()) {

                            selectionModel.add(node);

                        } else if (event.isMetaDown()) {

                            if (selectionModel.contains(node)) {
                                selectionModel.remove(node);
                            } else {
                                selectionModel.add(node);
                            }
                        } else {
                            selectionModel.add(node);
                        }

                    }
                }

            }

            selectionModel.log();

            rect.setX(0);
            rect.setY(0);
            rect.setWidth(0);
            rect.setHeight(0);

            group.getChildren().remove(rect);

            event.consume();

        }
    };

    EventHandler<MouseEvent> onMouseDraggedEventHandler = new EventHandler<MouseEvent>() {

        @Override
        public void handle(MouseEvent event) {

            double offsetX = event.getSceneX() - dragContext.mouseAnchorX;
            double offsetY = event.getSceneY() - dragContext.mouseAnchorY;

            if (offsetX > 0) {
                rect.setWidth(offsetX);
            } else {
                rect.setX(event.getSceneX());
                rect.setWidth(dragContext.mouseAnchorX - rect.getX());
            }

            if (offsetY > 0) {
                rect.setHeight(offsetY);
            } else {
                rect.setY(event.getSceneY());
                rect.setHeight(dragContext.mouseAnchorY - rect.getY());
            }

            event.consume();

        }
    };

    private final class DragContext {

        public double mouseAnchorX;
        public double mouseAnchorY;

    }
}
