/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.photoslide;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.util.Pair;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 *
 * @author selfemp
 */
public class MarkerLayer extends MapLayer {

    private final ObservableList<Pair<MapPoint, Node>> points = FXCollections.observableArrayList();    
    
    public MarkerLayer() {        
    }
    
    public void addFlag(MapPoint p) {          
        FontIcon flag=new FontIcon("ti-flag");
        flag.setIconSize(32);
        flag.setId("map-icon");
        points.add(new Pair<>(p, flag));                
        this.getChildren().add(flag);
        this.markDirty();
    }

    public void addPoint(MapPoint p, Node icon) {
        points.add(new Pair<>(p, icon));                
        this.getChildren().add(icon);        
        this.markDirty();
    }

    @Override
    protected void layoutLayer() {
        for (Pair<MapPoint, Node> candidate : points) {
            MapPoint point = candidate.getKey();
            Node icon = candidate.getValue();
            Point2D mapPoint = getMapPoint(point.getLatitude(), point.getLongitude());
            icon.setVisible(true);
            icon.setTranslateX(mapPoint.getX());
            icon.setTranslateY(mapPoint.getY());
        }
    }
    
    public void removeAllPoints(){
        points.clear();
        this.markDirty();
    }

    
    
}
