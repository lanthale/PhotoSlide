package org.photoslide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Generalised implementation of 'Draggability' of a {@link Node}. The Draggable class is used as a 'namespace' for the internal
 * class/interfaces/enum.
 * @author phill
 *
 */
public class Draggable {
public enum Event {
    None, DragStart, Drag, DragEnd
}

/**
 * Marker for an entity that has draggable nature.
 * @author phill
 */
public interface Interface {
    public abstract Draggable.Nature getDraggableNature();
}

public interface Listener {
    public void accept(Nature draggableNature, Event dragEvent);
}

/**
 * Class that encapsulates the draggable nature of a node.
 * <ul>
 * <li>EventNode: the event that receives the drag events</li>
 * <li>One or more DragNodes: that move in response to the drag events. The EventNode is usually (but not always) a
 * DragNode</li>
 * <li>Listeners: listen for the drag events</li>
 * </ul>
 * @author phill
 *
 */
public static final class Nature implements EventHandler<MouseEvent> {
    private double lastMouseX = 0, lastMouseY = 0; // scene coords

    private boolean dragging = false;

    private final boolean enabled = true;
    private final Node eventNode;
    private final List<Node> dragNodes = new ArrayList<>();
    private final List<Listener> dragListeners = new ArrayList<>();

    public Nature(final Node node) {
        this(node, node);
    }

    public Nature(final Node eventNode, final Node... dragNodes) {
        this.eventNode = eventNode;
        this.dragNodes.addAll(Arrays.asList(dragNodes));
        this.eventNode.addEventHandler(MouseEvent.ANY, this);
    }

    public final boolean addDraggedNode(final Node node) {
        if (!this.dragNodes.contains(node)) {
            return this.dragNodes.add(node);
        }
        return false;
    }

    public final boolean addListener(final Listener listener) {
        return this.dragListeners.add(listener);
    }

    public final void detatch() {
        this.eventNode.removeEventFilter(MouseEvent.ANY, this);
    }

    public final List<Node> getDragNodes() {
        return new ArrayList<>(this.dragNodes);
    }

    public final Node getEventNode() {
        return this.eventNode;
    }

    @Override
    public final void handle(final MouseEvent event) {
        if (MouseEvent.MOUSE_PRESSED == event.getEventType()) {
            if (this.enabled && this.eventNode.contains(event.getX(), event.getY())) {
                this.lastMouseX = event.getSceneX();
                this.lastMouseY = event.getSceneY();
                event.consume();
            }
        } else if (MouseEvent.MOUSE_DRAGGED == event.getEventType()) {
            if (!this.dragging) {
                this.dragging = true;
                for (final Listener listener : this.dragListeners) {
                    listener.accept(this, Draggable.Event.DragStart);
                }
            }
            if (this.dragging) {
                final double deltaX = event.getSceneX() - this.lastMouseX;
                final double deltaY = event.getSceneY() - this.lastMouseY;

                for (final Node dragNode : this.dragNodes) {
                    final double initialTranslateX = dragNode.getTranslateX();
                    final double initialTranslateY = dragNode.getTranslateY();
                    dragNode.setTranslateX(initialTranslateX + deltaX);
                    dragNode.setTranslateY(initialTranslateY + deltaY);
                }

                this.lastMouseX = event.getSceneX();
                this.lastMouseY = event.getSceneY();

                event.consume();
                for (final Listener listener : this.dragListeners) {
                    listener.accept(this, Draggable.Event.Drag);
                }
            }
        } else if (MouseEvent.MOUSE_RELEASED == event.getEventType()) {
            if (this.dragging) {
                event.consume();
                this.dragging = false;
                for (final Listener listener : this.dragListeners) {
                    listener.accept(this, Draggable.Event.DragEnd);
                }
            }
        }

    }

    public final boolean removeDraggedNode(final Node node) {
        return this.dragNodes.remove(node);
    }

    public final boolean removeListener(final Listener listener) {
        return this.dragListeners.remove(listener);
    }

    /**
     * When the initial mousePressed is missing we can supply the first coordinates programmatically.
     * @param lastMouseX
     * @param lastMouseY
     */
    public final void setLastMouse(final double lastMouseX, final double lastMouseY) {
        this.lastMouseX = lastMouseX;
        this.lastMouseY = lastMouseY;
    }
}
}
